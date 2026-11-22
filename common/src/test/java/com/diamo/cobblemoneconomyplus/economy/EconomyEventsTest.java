package com.diamo.cobblemoneconomyplus.economy;

import com.diamo.cobblemoneconomyplus.api.BalanceChange;
import com.diamo.cobblemoneconomyplus.api.RewardEvent;
import com.diamo.cobblemoneconomyplus.api.RewardType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyEventsTest {
    private static RewardEvent reward(long amount) {
        // The player is only passed through, so the listener tests do not need a server.
        return new RewardEvent(RewardType.WILD_BATTLE, null, amount, false, 10, 0, null, null);
    }

    @Test
    void balanceListenersSeeChangesButNotNoOps() {
        List<BalanceChange> seen = new ArrayList<>();
        Consumer<BalanceChange> listener = seen::add;
        EconomyEvents.addBalanceListener(listener);
        try {
            UUID player = UUID.randomUUID();
            EconomyEvents.fireBalanceChanged(new BalanceChange(player, 10, 25, "test"));
            EconomyEvents.fireBalanceChanged(new BalanceChange(player, 25, 25, "test"));
            assertEquals(1, seen.size());
            assertEquals(15, seen.get(0).delta());
        } finally {
            EconomyEvents.removeBalanceListener(listener);
        }
    }

    @Test
    void aThrowingListenerDoesNotStopTheOthers() {
        List<String> calls = new ArrayList<>();
        Consumer<RewardEvent> broken = event -> {
            throw new IllegalStateException("boom");
        };
        Consumer<RewardEvent> working = event -> calls.add("ok");
        EconomyEvents.addRewardListener(broken);
        EconomyEvents.addRewardListener(working);
        try {
            EconomyEvents.fireReward(reward(10));
            assertEquals(List.of("ok"), calls);
        } finally {
            EconomyEvents.removeRewardListener(broken);
            EconomyEvents.removeRewardListener(working);
        }
    }

    @Test
    void rewardListenersCanCancelOrChangeTheAmount() {
        Consumer<RewardEvent> doubler = event -> event.setAmount(event.amount() * 2);
        EconomyEvents.addRewardListener(doubler);
        try {
            RewardEvent event = reward(10);
            assertSame(event, EconomyEvents.fireReward(event));
            assertEquals(20, event.amount());
            assertFalse(event.isCancelled());
        } finally {
            EconomyEvents.removeRewardListener(doubler);
        }

        Consumer<RewardEvent> canceller = RewardEvent::cancel;
        EconomyEvents.addRewardListener(canceller);
        try {
            assertTrue(EconomyEvents.fireReward(reward(10)).isCancelled());
        } finally {
            EconomyEvents.removeRewardListener(canceller);
        }
    }

    @Test
    void rewardAmountsNeverGoNegative() {
        RewardEvent event = reward(-5);
        assertEquals(0, event.amount());
        event.setAmount(-1);
        assertEquals(0, event.amount());
    }
}
