package com.diamo.cobblemoneconomyplus.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;

/** Pure reward arithmetic, kept apart from the Cobblemon event code so it can be tested without a game. */
final class RewardRules {
    private RewardRules() {
    }

    /**
     * How much of {@code amount} may still be paid today under {@code dailyCap} (0 or less = no cap), given what
     * the player already received today.
     */
    static long capped(long amount, long dailyCap, long alreadyToday) {
        if (amount <= 0L) {
            return 0L;
        }
        if (dailyCap <= 0L) {
            return amount;
        }
        return Math.max(0L, Math.min(amount, dailyCap - alreadyToday));
    }

    /** Milestones reached with {@code caughtSpecies} that have not been claimed yet, lowest first. */
    static List<Integer> dueMilestones(NavigableMap<Integer, Long> milestones, Set<Integer> claimed, int caughtSpecies) {
        List<Integer> due = new ArrayList<>();
        for (int milestone : milestones.headMap(caughtSpecies, true).keySet()) {
            if (!claimed.contains(milestone)) {
                due.add(milestone);
            }
        }
        return due;
    }
}
