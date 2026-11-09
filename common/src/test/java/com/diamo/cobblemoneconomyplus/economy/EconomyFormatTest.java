package com.diamo.cobblemoneconomyplus.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyFormatTest {
    @Test
    void groupsThousandsWithCommas() {
        assertEquals("0", EconomyFormat.money(0L));
        assertEquals("999", EconomyFormat.money(999L));
        assertEquals("1,000", EconomyFormat.money(1_000L));
        assertEquals("12,500,000", EconomyFormat.money(12_500_000L));
    }

    @Test
    void negativeAmountsAreShownAsZero() {
        assertEquals("0", EconomyFormat.money(-5L));
    }

    @Test
    void handlesTheLargestBalance() {
        assertEquals("9,223,372,036,854,775,807", EconomyFormat.money(Long.MAX_VALUE));
    }
}
