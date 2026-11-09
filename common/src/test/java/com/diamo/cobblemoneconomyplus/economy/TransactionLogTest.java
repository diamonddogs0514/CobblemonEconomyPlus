package com.diamo.cobblemoneconomyplus.economy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionLogTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void aLineHasEightTabSeparatedFields() {
        String line = TransactionLog.formatLine(TIME, TransactionLog.Type.DEPOSIT, ID, "Steve", 250L, 1_000L,
                "command:Server", "reward");
        String[] fields = line.split("\t", -1);
        assertEquals(8, fields.length);
        assertEquals("2026-01-02T03:04:05Z", fields[0]);
        assertEquals("DEPOSIT", fields[1]);
        assertEquals(ID.toString(), fields[2]);
        assertEquals("Steve", fields[3]);
        assertEquals("+250", fields[4]);
        assertEquals("1000", fields[5]);
        assertEquals("command:Server", fields[6]);
        assertEquals("reward", fields[7]);
    }

    @Test
    void negativeChangesKeepTheirSignAndPositiveOnesGainAPlus() {
        assertEquals("-30", TransactionLog.formatLine(TIME, TransactionLog.Type.WITHDRAW, ID, "-", -30L, 70L, "s", "")
                .split("\t", -1)[4]);
        assertEquals("+0", TransactionLog.formatLine(TIME, TransactionLog.Type.SET, ID, "-", 0L, 70L, "s", "")
                .split("\t", -1)[4]);
    }

    @Test
    void tabsAndLineBreaksInFreeTextCannotBreakTheFormat() {
        String line = TransactionLog.formatLine(TIME, TransactionLog.Type.DEPOSIT, ID, "Ev\til", 1L, 1L,
                "mod\nwith\r\nbreaks", "a\tb");
        assertEquals(8, line.split("\t", -1).length);
        assertEquals(1, line.lines().count());
    }

    @Test
    void nullTextBecomesEmpty() {
        String line = TransactionLog.formatLine(TIME, TransactionLog.Type.DEPOSIT, ID, "-", 1L, 1L, null, null);
        String[] fields = line.split("\t", -1);
        assertEquals("", fields[6]);
        assertEquals("", fields[7]);
    }
}
