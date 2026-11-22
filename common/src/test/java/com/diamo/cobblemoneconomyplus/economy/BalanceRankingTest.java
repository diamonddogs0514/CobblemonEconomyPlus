package com.diamo.cobblemoneconomyplus.economy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceRankingTest {
    private static UUID id(int n) {
        return new UUID(0L, n);
    }

    @Test
    void richestFirstAndTiesShareARank() {
        BalanceRanking ranking = BalanceRanking.of(Map.of(id(1), 50L, id(2), 300L, id(3), 50L, id(4), 10L));
        List<BalanceRanking.Entry> page = ranking.page(1);
        assertEquals(List.of(id(2), id(1), id(3), id(4)), page.stream().map(BalanceRanking.Entry::playerId).toList());
        assertEquals(List.of(1, 2, 2, 4), page.stream().map(BalanceRanking.Entry::rank).toList());
    }

    @Test
    void pagesHoldTenPlayers() {
        Map<UUID, Long> balances = new HashMap<>();
        for (int n = 1; n <= 23; n++) {
            balances.put(id(n), (long) n * 100);
        }
        BalanceRanking ranking = BalanceRanking.of(balances);
        assertEquals(23, ranking.size());
        assertEquals(3, ranking.pageCount());
        assertEquals(10, ranking.page(1).size());
        assertEquals(3, ranking.page(3).size());
        assertEquals(21, ranking.page(3).get(0).rank());
        assertTrue(ranking.page(4).isEmpty());
        assertTrue(ranking.page(0).isEmpty());
    }

    @Test
    void findsAPlayersOwnRank() {
        BalanceRanking ranking = BalanceRanking.of(Map.of(id(1), 5L, id(2), 9L));
        assertEquals(2, ranking.find(id(1)).orElseThrow().rank());
        assertTrue(ranking.find(id(3)).isEmpty());
    }

    @Test
    void emptyAndZeroBalancesAreNotRanked() {
        BalanceRanking ranking = BalanceRanking.of(Map.of(id(1), 0L));
        assertEquals(0, ranking.size());
        assertEquals(1, ranking.pageCount());
        assertTrue(ranking.page(1).isEmpty());
    }
}
