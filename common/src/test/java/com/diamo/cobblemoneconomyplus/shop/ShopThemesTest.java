package com.diamo.cobblemoneconomyplus.shop;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopThemesTest {
    @Test
    void idsAreUniqueAndIncludeTheDefault() {
        assertEquals(ShopThemes.IDS.size(), new HashSet<>(ShopThemes.IDS).size());
        assertTrue(ShopThemes.isValid(ShopThemes.DEFAULT));
    }

    @Test
    void validityChecksExactIds() {
        assertTrue(ShopThemes.isValid("iron"));
        assertFalse(ShopThemes.isValid("neon"));
        assertFalse(ShopThemes.isValid("IRON"));
        assertFalse(ShopThemes.isValid(null));
    }

    @Test
    void cycleWrapsInBothDirections() {
        String first = ShopThemes.IDS.get(0);
        String last = ShopThemes.IDS.get(ShopThemes.IDS.size() - 1);
        assertEquals(ShopThemes.IDS.get(1), ShopThemes.cycle(first, 1));
        assertEquals(first, ShopThemes.cycle(last, 1));
        assertEquals(last, ShopThemes.cycle(first, -1));
    }

    @Test
    void cycleFromUnsetOrUnknownStartsAtTheDefault() {
        int defaultIndex = ShopThemes.IDS.indexOf(ShopThemes.DEFAULT);
        String expected = ShopThemes.IDS.get(Math.floorMod(defaultIndex + 1, ShopThemes.IDS.size()));
        assertEquals(expected, ShopThemes.cycle(null, 1));
        assertEquals(expected, ShopThemes.cycle("nonsense", 1));
    }
}
