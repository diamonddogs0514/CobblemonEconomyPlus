package com.diamo.cobblemoneconomyplus.economy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Reading the log back (the data behind {@code /cep history}). */
class TransactionHistoryTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static String line(UUID id, long delta, long balance, String source) {
        return TransactionLog.formatLine(Instant.parse("2026-01-02T03:04:05Z").plusSeconds(Math.abs(balance)),
                delta >= 0 ? TransactionLog.Type.DEPOSIT : TransactionLog.Type.WITHDRAW,
                id, "-", delta, balance, source, "");
    }

    @Test
    void aWrittenLineParsesBackToTheSameValues() {
        Instant time = Instant.parse("2026-05-06T07:08:09Z");
        String text = TransactionLog.formatLine(time, TransactionLog.Type.TRANSFER_OUT, ALICE, "Alice", -250L, 750L,
                "pay:Alice", "to=" + BOB);

        TransactionLog.Entry entry = TransactionLog.parseLine(text);
        assertNotNull(entry);
        assertEquals(time, entry.time());
        assertEquals(TransactionLog.Type.TRANSFER_OUT, entry.type());
        assertEquals(ALICE, entry.playerId());
        assertEquals("Alice", entry.playerName());
        assertEquals(-250L, entry.delta());
        assertEquals(750L, entry.balanceAfter());
        assertEquals("pay:Alice", entry.source());
        assertEquals("to=" + BOB, entry.detail());
    }

    @Test
    void positiveDeltasWithAPlusSignParse() {
        TransactionLog.Entry entry = TransactionLog.parseLine(line(ALICE, 40L, 40L, "shop:general"));
        assertNotNull(entry);
        assertEquals(40L, entry.delta());
    }

    @Test
    void malformedLinesAreRejectedNotThrown() {
        assertNull(TransactionLog.parseLine(""));
        assertNull(TransactionLog.parseLine("just some text"));
        assertNull(TransactionLog.parseLine("a\tb\tc\td\te\tf\tg\th"));
        assertNull(TransactionLog.parseLine("2026-01-02T03:04:05Z\tNOPE\t" + ALICE + "\t-\t+1\t1\ts\td"));
    }

    @Test
    void recentReturnsOnlyThePlayersEntriesOldestFirstAndLimited() {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            lines.add(line(ALICE, 10L, 10L * i, "a" + i));
            lines.add(line(BOB, 1L, i, "b" + i));
        }
        lines.add("garbage line");

        List<TransactionLog.Entry> recent = TransactionLog.selectRecent(lines, ALICE, 3);
        assertEquals(3, recent.size());
        assertEquals("a3", recent.get(0).source());
        assertEquals("a4", recent.get(1).source());
        assertEquals("a5", recent.get(2).source());
    }

    @Test
    void asksForMoreThanExistsAndGetsEverything() {
        List<String> lines = List.of(line(ALICE, 5L, 5L, "x"), line(ALICE, 5L, 10L, "y"));
        assertEquals(2, TransactionLog.selectRecent(lines, ALICE, 50).size());
    }

    @Test
    void aPlayerWithNoEntriesGetsAnEmptyList() {
        assertEquals(0, TransactionLog.selectRecent(List.of(line(BOB, 1L, 1L, "x")), ALICE, 10).size());
    }

    @Test
    void aZeroLimitReturnsNothing() {
        assertEquals(0, TransactionLog.selectRecent(List.of(line(ALICE, 1L, 1L, "x")), ALICE, 0).size());
    }
}
