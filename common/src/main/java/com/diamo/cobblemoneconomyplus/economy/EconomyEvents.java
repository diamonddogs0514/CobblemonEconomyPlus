package com.diamo.cobblemoneconomyplus.economy;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.api.BalanceChange;
import com.diamo.cobblemoneconomyplus.api.RewardEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Listener lists behind {@code CobblemonEconomyApi.onBalanceChanged} and {@code onReward}. A listener that throws is
 * logged and skipped, so another mod's bug cannot break payments or the other listeners.
 */
public final class EconomyEvents {
    private static final List<Consumer<BalanceChange>> BALANCE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<RewardEvent>> REWARD_LISTENERS = new CopyOnWriteArrayList<>();

    private EconomyEvents() {
    }

    public static void addBalanceListener(Consumer<BalanceChange> listener) {
        BALANCE_LISTENERS.add(listener);
    }

    public static void addRewardListener(Consumer<RewardEvent> listener) {
        REWARD_LISTENERS.add(listener);
    }

    public static void removeBalanceListener(Consumer<BalanceChange> listener) {
        BALANCE_LISTENERS.remove(listener);
    }

    public static void removeRewardListener(Consumer<RewardEvent> listener) {
        REWARD_LISTENERS.remove(listener);
    }

    /** Notifies listeners of a change; does nothing when the balance did not actually change. */
    static void fireBalanceChanged(BalanceChange change) {
        if (change.delta() == 0L) {
            return;
        }
        for (Consumer<BalanceChange> listener : BALANCE_LISTENERS) {
            try {
                listener.accept(change);
            } catch (RuntimeException exception) {
                CobblemonEconomyPlus.LOGGER.error("A balance listener failed", exception);
            }
        }
    }

    /** Lets every listener see (and possibly cancel or change) the reward; returns the same event. */
    public static RewardEvent fireReward(RewardEvent event) {
        for (Consumer<RewardEvent> listener : REWARD_LISTENERS) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                CobblemonEconomyPlus.LOGGER.error("A reward listener failed", exception);
            }
        }
        return event;
    }
}
