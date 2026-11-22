package com.diamo.cobblemoneconomyplus.admin;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Who changed what in the admin screen: {@code logs/cobblemon_economy_plus/admin.log}, one tab-separated line per edit
 * ({@code time (UTC) / player name / UUID / action / detail}). Rotated to {@code admin.log.1} past 2 MB.
 */
public final class AdminLog {
    private static final long MAX_BYTES = 2L * 1024L * 1024L;

    private AdminLog() {
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory().resolve("logs").resolve(CobblemonEconomyPlus.MOD_ID).resolve("admin.log");
    }

    public static void record(ServerPlayer admin, String action, String detail) {
        String line = line(Instant.now(), admin.getGameProfile().getName(), admin.getUUID().toString(), action, detail);
        CobblemonEconomyPlus.LOGGER.info("Admin edit by {}: {} {}", admin.getGameProfile().getName(), action, detail);
        Path file = path(admin.server);
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file) && Files.size(file) > MAX_BYTES) {
                Files.move(file, file.resolveSibling("admin.log.1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not write the admin log {}", file, exception);
        }
    }

    static String line(Instant time, String name, String uuid, String action, String detail) {
        return String.join("\t", DateTimeFormatter.ISO_INSTANT.format(time), clean(name), clean(uuid), clean(action),
                clean(detail));
    }

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
