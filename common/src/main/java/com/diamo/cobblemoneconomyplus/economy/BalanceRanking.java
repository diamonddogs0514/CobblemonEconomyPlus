package com.diamo.cobblemoneconomyplus.economy;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Balance ranking for {@code /cep top}. Players with equal balances share a rank (1, 2, 2, 4, ...); within a tie the
 * order is fixed by UUID so pages never shuffle between calls.
 */
public final class BalanceRanking {
    public static final int PAGE_SIZE = 10;

    private final List<Entry> entries;

    public record Entry(int rank, UUID playerId, long balance) {
    }

    private BalanceRanking(List<Entry> entries) {
        this.entries = entries;
    }

    /** How long {@link #cached} reuses a ranking: placeholders may be asked for it every tick. */
    private static final long CACHE_NANOS = 5_000_000_000L;
    private static BalanceRanking cached;
    private static MinecraftServer cachedServer;
    private static long cachedAt;

    /**
     * The server's ranking, recomputed at most every few seconds. For displays (Trainer Card, placeholders);
     * {@code /cep top} computes a fresh one.
     */
    public static synchronized BalanceRanking cached(MinecraftServer server) {
        long now = System.nanoTime();
        if (cached == null || cachedServer != server || now - cachedAt > CACHE_NANOS) {
            cached = of(EconomySavedData.get(server).balances());
            cachedServer = server;
            cachedAt = now;
        }
        return cached;
    }

    /** The entry at a 1-based position (not rank: tied players have their own positions). */
    public Optional<Entry> at(int position) {
        return position < 1 || position > entries.size() ? Optional.empty() : Optional.of(entries.get(position - 1));
    }

    public static BalanceRanking of(Map<UUID, Long> balances) {
        List<Map.Entry<UUID, Long>> sorted = new ArrayList<>();
        for (Map.Entry<UUID, Long> balance : balances.entrySet()) {
            if (balance.getValue() > 0L) {
                sorted.add(balance);
            }
        }
        sorted.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));

        List<Entry> ranked = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            long balance = sorted.get(index).getValue();
            int rank = index > 0 && ranked.get(index - 1).balance() == balance ? ranked.get(index - 1).rank() : index + 1;
            ranked.add(new Entry(rank, sorted.get(index).getKey(), balance));
        }
        return new BalanceRanking(List.copyOf(ranked));
    }

    /** Number of ranked players (those with more than 0 coins). */
    public int size() {
        return entries.size();
    }

    public int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The entries on a 1-based page; empty when the page does not exist. */
    public List<Entry> page(int page) {
        int from = (page - 1) * PAGE_SIZE;
        if (page < 1 || from >= entries.size()) {
            return List.of();
        }
        return entries.subList(from, Math.min(entries.size(), from + PAGE_SIZE));
    }

    public Optional<Entry> find(UUID playerId) {
        return entries.stream().filter(entry -> entry.playerId().equals(playerId)).findFirst();
    }
}
