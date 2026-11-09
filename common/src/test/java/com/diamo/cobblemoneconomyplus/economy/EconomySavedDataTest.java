package com.diamo.cobblemoneconomyplus.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomySavedDataTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static CompoundTag entry(UUID id, long balance) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("player", id);
        entry.putLong("balance", balance);
        return entry;
    }

    @Test
    void unknownPlayersHaveNothing() {
        assertEquals(0L, new EconomySavedData().balance(ALICE));
    }

    @Test
    void depositAddsAndIgnoresNonPositiveAmounts() {
        EconomySavedData data = new EconomySavedData();
        assertEquals(100L, data.deposit(ALICE, 100L));
        assertEquals(100L, data.deposit(ALICE, 0L));
        assertEquals(100L, data.deposit(ALICE, -50L));
        assertEquals(100L, data.balance(ALICE));
    }

    @Test
    void depositSaturatesInsteadOfOverflowing() {
        EconomySavedData data = new EconomySavedData();
        data.setBalance(ALICE, Long.MAX_VALUE - 10L);
        assertEquals(Long.MAX_VALUE, data.deposit(ALICE, 1_000L));
        assertEquals(Long.MAX_VALUE, data.balance(ALICE));
    }

    @Test
    void withdrawNeedsEnoughFundsAndChangesNothingOtherwise() {
        EconomySavedData data = new EconomySavedData();
        data.setBalance(ALICE, 50L);
        assertFalse(data.withdraw(ALICE, 51L));
        assertEquals(50L, data.balance(ALICE));
        assertFalse(data.withdraw(ALICE, -1L));
        assertTrue(data.withdraw(ALICE, 50L));
        assertEquals(0L, data.balance(ALICE));
    }

    @Test
    void negativeBalancesAreClampedToZero() {
        EconomySavedData data = new EconomySavedData();
        data.setBalance(ALICE, -5L);
        assertEquals(0L, data.balance(ALICE));
    }

    @Test
    void saveThenLoadKeepsBalancesAndWritesTheVersion() {
        EconomySavedData data = new EconomySavedData();
        data.setBalance(ALICE, 1_234L);
        data.setBalance(BOB, 99L);

        CompoundTag saved = data.save(new CompoundTag(), null);
        assertEquals(1, saved.getInt("data_version"));

        EconomySavedData loaded = EconomySavedData.load(saved, null);
        assertEquals(1_234L, loaded.balance(ALICE));
        assertEquals(99L, loaded.balance(BOB));
    }

    @Test
    void zeroBalancesAreNotWritten() {
        EconomySavedData data = new EconomySavedData();
        data.setBalance(ALICE, 10L);
        data.withdraw(ALICE, 10L);
        assertEquals(0, data.save(new CompoundTag(), null).getList("balances", 10).size());
    }

    @Test
    void dataWrittenBeforeVersioningStillLoads() {
        CompoundTag legacy = new CompoundTag(); // no data_version key
        ListTag list = new ListTag();
        list.add(entry(ALICE, 700L));
        legacy.put("balances", list);

        assertEquals(700L, EconomySavedData.load(legacy, null).balance(ALICE));
    }

    @Test
    void dataFromANewerVersionStillLoadsWhatItUnderstands() {
        CompoundTag future = new CompoundTag();
        future.putInt("data_version", 999);
        ListTag list = new ListTag();
        list.add(entry(ALICE, 5L));
        future.put("balances", list);

        assertEquals(5L, EconomySavedData.load(future, null).balance(ALICE));
    }

    @Test
    void brokenEntriesAreSkippedOnLoad() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        list.add(entry(ALICE, -10L)); // not positive
        list.add(entry(BOB, 0L));     // zero
        list.add(new CompoundTag());  // no player id
        list.add(entry(CAROL, 8L));
        tag.put("balances", list);

        EconomySavedData loaded = EconomySavedData.load(tag, null);
        assertEquals(0L, loaded.balance(ALICE));
        assertEquals(0L, loaded.balance(BOB));
        assertEquals(8L, loaded.balance(CAROL));
    }
}
