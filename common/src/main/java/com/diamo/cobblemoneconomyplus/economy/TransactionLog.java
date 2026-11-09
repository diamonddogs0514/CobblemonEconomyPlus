package com.diamo.cobblemoneconomyplus.economy;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only audit trail of every balance change, so operators can answer "who gained or lost how much,
 * and why". Written to {@code logs/cobblemon_economy_plus/transactions.log} in the server directory, one line
 * per change; the file is rotated when it grows past {@link #MAX_BYTES}.
 *
 * <p>Line format (tab separated): {@code timestamp, type, player uuid, player name, delta, balance after,
 * source, detail}.</p>
 */
public final class TransactionLog {
    /** What kind of change a log line records. */
    public enum Type {
        SET, DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
    }

    private static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final int KEPT_ROTATED_FILES = 5;
    private static boolean failureReported;

    private TransactionLog() {
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory().resolve("logs").resolve(CobblemonEconomyPlus.MOD_ID).resolve("transactions.log");
    }

    /**
     * @param delta        signed change in coins (after any saturation or clamping)
     * @param balanceAfter the player's balance after the change
     * @param source       who caused it, e.g. {@code command:Steve}, {@code shop}, or an API caller's reason
     * @param detail       free-form extra information (may be empty)
     */
    public static synchronized void record(MinecraftServer server, Type type, UUID playerId, long delta,
                                           long balanceAfter, String source, String detail) {
        try {
            Path file = path(server);
            Files.createDirectories(file.getParent());
            rotateIfNeeded(file);

            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            String line = formatLine(Instant.now(), type, playerId,
                    online != null ? online.getGameProfile().getName() : "-",
                    delta, balanceAfter, source, detail) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException exception) {
            // Never let logging break a transaction; report the problem once instead of on every line.
            if (!failureReported) {
                failureReported = true;
                CobblemonEconomyPlus.LOGGER.error("Could not write the transaction log (further errors are not repeated)",
                        exception);
            }
        }
    }

    /** One log line (without a trailing newline). Tabs and line breaks in free text are replaced by spaces. */
    static String formatLine(Instant time, Type type, UUID playerId, String playerName, long delta, long balanceAfter,
                             String source, String detail) {
        return String.join("\t",
                time.toString(),
                type.name(),
                playerId.toString(),
                clean(playerName),
                (delta >= 0 ? "+" : "") + delta,
                Long.toString(balanceAfter),
                clean(source),
                clean(detail)
        );
    }

    /** One parsed log line. */
    public record Entry(Instant time, Type type, UUID playerId, String playerName, long delta, long balanceAfter,
                        String source, String detail) {
    }

    /** Parses a line written by {@link #formatLine}; returns null for anything malformed. */
    static Entry parseLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 8) {
            return null;
        }
        try {
            return new Entry(
                    Instant.parse(fields[0]),
                    Type.valueOf(fields[1]),
                    UUID.fromString(fields[2]),
                    fields[3],
                    Long.parseLong(fields[4].startsWith("+") ? fields[4].substring(1) : fields[4]),
                    Long.parseLong(fields[5]),
                    fields[6],
                    fields[7]
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * The player's most recent entries (oldest first, at most {@code limit}) from lines that are in
     * chronological order. Malformed lines and other players' lines are skipped.
     */
    static List<Entry> selectRecent(List<String> lines, UUID playerId, int limit) {
        List<Entry> matches = new ArrayList<>();
        String idText = playerId.toString();
        for (String line : lines) {
            // Cheap pre-check before the full parse: the UUID is the third field.
            if (!line.contains(idText)) {
                continue;
            }
            Entry entry = parseLine(line);
            if (entry != null && entry.playerId().equals(playerId)) {
                matches.add(entry);
            }
        }
        int from = Math.max(0, matches.size() - Math.max(0, limit));
        return new ArrayList<>(matches.subList(from, matches.size()));
    }

    /**
     * The player's most recent transactions, oldest first (at most {@code limit}). Reads the current log and,
     * when it does not hold enough, the rotated files, newest to oldest.
     */
    public static synchronized List<Entry> recent(MinecraftServer server, UUID playerId, int limit) {
        Path current = path(server);
        List<Entry> result = new ArrayList<>();
        for (int index = 0; index <= KEPT_ROTATED_FILES && result.size() < limit; index++) {
            Path file = index == 0 ? current : current.resolveSibling("transactions.log." + index);
            if (!Files.exists(file)) {
                continue;
            }
            try {
                List<String> lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).lines().toList();
                // Older files are read later, so their entries go in front of what we already have.
                List<Entry> fromFile = selectRecent(lines, playerId, limit - result.size());
                result.addAll(0, fromFile);
            } catch (IOException exception) {
                CobblemonEconomyPlus.LOGGER.warn("Could not read {}", file, exception);
            }
        }
        return result;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void rotateIfNeeded(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) < MAX_BYTES) {
            return;
        }
        // transactions.log -> transactions.log.1 -> ... -> transactions.log.N (oldest dropped)
        Files.deleteIfExists(file.resolveSibling("transactions.log." + KEPT_ROTATED_FILES));
        for (int index = KEPT_ROTATED_FILES - 1; index >= 1; index--) {
            Path from = file.resolveSibling("transactions.log." + index);
            if (Files.exists(from)) {
                Files.move(from, file.resolveSibling("transactions.log." + (index + 1)));
            }
        }
        Files.move(file, file.resolveSibling("transactions.log.1"));
    }
}
