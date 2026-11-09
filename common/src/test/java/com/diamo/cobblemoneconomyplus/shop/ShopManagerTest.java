package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopManagerTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    // ------------------------------------------------------------------ sell pricing

    @Test
    void scaledPriceMultipliesAndRoundsDown() {
        assertEquals(120L, ShopManager.scaledPrice(100L, 1.2D));
        assertEquals(1L, ShopManager.scaledPrice(3L, 0.5D)); // 1.5 -> 1
        assertEquals(100L, ShopManager.scaledPrice(100L, 1.0D));
    }

    @Test
    void scaledPriceIsZeroWhenSellingIsDisabledOrValueIsInvalid() {
        assertEquals(0L, ShopManager.scaledPrice(100L, 0.0D));
        assertEquals(0L, ShopManager.scaledPrice(100L, -1.0D));
        assertEquals(0L, ShopManager.scaledPrice(0L, 2.0D));
        // A multiplier that rounds the price below 1 is "not sellable" for the caller.
        assertEquals(0L, ShopManager.scaledPrice(1L, 0.5D));
    }

    @Test
    void scaledPriceIsCappedSoBulkSalesCannotOverflow() {
        long cap = Long.MAX_VALUE / 4096L;
        assertEquals(cap, ShopManager.scaledPrice(Long.MAX_VALUE, 100.0D));
        // The largest possible sale (a full inventory: 2304 items) must still fit in a long.
        assertEquals(cap * 2304L, Math.multiplyExact(cap, 2304L));
    }

    // ------------------------------------------------------------------ item values

    @Test
    void itemValuesSkipUnknownItemsAndInvalidValues() {
        Map<ResourceLocation, Long> values = ShopManager.parseItemValues("""
                { "values": {
                    "minecraft:diamond": 250,
                    "minecraft:apple": 0,
                    "minecraft:bread": -5,
                    "minecraft:stick": "lots",
                    "nosuchmod:thing": 10
                } }
                """);
        assertEquals(Map.of(id("minecraft:diamond"), 250L), values);
    }

    @Test
    void itemValuesRequireAValuesObject() {
        assertThrows(IllegalArgumentException.class, () -> ShopManager.parseItemValues("{}"));
        assertThrows(IllegalArgumentException.class, () -> ShopManager.parseItemValues("{ \"values\": [] }"));
    }

    // ------------------------------------------------------------------ shops

    @Test
    void parsesShopWithDefaultsAndLocalizedText() {
        Map<String, ShopDefinition> shops = ShopManager.parse("""
                { "shops": [ {
                    "id": "Mart",
                    "name": { "EN_US": "Mart", "ja_jp": "マート" },
                    "entries": [ { "item": "minecraft:apple", "amount": 4, "price": 12 } ]
                } ] }
                """);

        ShopDefinition shop = shops.get("mart"); // ids are lower-cased
        assertEquals("mart", shop.id());
        assertEquals("Mart", LocalizedText.resolve(shop.displayName(), "en_us"));
        assertEquals("マート", LocalizedText.resolve(shop.displayName(), "ja_jp"));
        assertEquals(ShopThemes.DEFAULT, shop.theme());
        assertEquals("", shop.subtitle());
        assertEquals(1.0D, shop.sellMultiplier());
        assertEquals(1, shop.entries().size());
        assertEquals(new ShopDefinition.Entry(id("minecraft:apple"), 4, 12L), shop.entries().get(0));
    }

    @Test
    void invalidThemeAndMultiplierFallBackToDefaults() {
        ShopDefinition shop = ShopManager.parse("""
                { "shops": [ {
                    "id": "a", "theme": "neon", "sell_multiplier": -3,
                    "entries": [ { "item": "minecraft:apple", "amount": 1, "price": 1 } ]
                } ] }
                """).get("a");
        assertEquals(ShopThemes.DEFAULT, shop.theme());
        assertEquals(1.0D, shop.sellMultiplier());
    }

    @Test
    void validThemeMultiplierAndZeroAreKept() {
        Map<String, ShopDefinition> shops = ShopManager.parse("""
                { "shops": [
                    { "id": "a", "theme": "iron", "sell_multiplier": 1.5,
                      "entries": [ { "item": "minecraft:apple", "amount": 1, "price": 1 } ] },
                    { "id": "b", "sell_multiplier": 0,
                      "entries": [ { "item": "minecraft:apple", "amount": 1, "price": 1 } ] }
                ] }
                """);
        assertEquals("iron", shops.get("a").theme());
        assertEquals(1.5D, shops.get("a").sellMultiplier());
        assertEquals(0.0D, shops.get("b").sellMultiplier());
    }

    @Test
    void invalidEntriesAreSkippedButTheShopSurvives() {
        ShopDefinition shop = ShopManager.parse("""
                { "shops": [ { "id": "a", "entries": [
                    { "item": "minecraft:apple", "amount": 1, "price": 5 },
                    { "item": "nosuchmod:thing", "amount": 1, "price": 5 },
                    { "item": "minecraft:bread", "amount": 0, "price": 5 },
                    { "item": "minecraft:bread", "amount": 65, "price": 5 },
                    { "item": "minecraft:bread", "amount": 1, "price": -1 },
                    { "item": "minecraft:bread", "amount": 1 }
                ] } ] }
                """).get("a");
        assertEquals(1, shop.entries().size());
        assertEquals(id("minecraft:apple"), shop.entries().get(0).itemId());
    }

    @Test
    void shopsWithInvalidIdsOrNoEntriesAreSkipped() {
        Map<String, ShopDefinition> shops = ShopManager.parse("""
                { "shops": [
                    { "id": "bad id!", "entries": [] },
                    { "id": "noentries" },
                    { "id": "good", "entries": [] }
                ] }
                """);
        assertEquals(java.util.Set.of("good"), shops.keySet());
    }

    @Test
    void rootMustBeAnObjectWithAShopsArray() {
        assertThrows(IllegalArgumentException.class, () -> ShopManager.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> ShopManager.parse("{}"));
    }

    @Test
    void anOversizedLocalizedNameFallsBackToASingleLanguage() {
        String longText = "x".repeat(95);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            names.append("\"l").append(i).append("_xx\": \"").append(longText).append("\",");
        }
        names.append("\"en_us\": \"English\"");
        ShopDefinition shop = ShopManager.parse("{ \"shops\": [ { \"id\": \"a\", \"name\": { " + names
                + " }, \"entries\": [] } ] }").get("a");
        // Too long to ship every language: one plain string is kept, not a broken JSON object.
        assertFalse(shop.displayName().startsWith("{"));
        assertTrue(shop.displayName().length() <= 96);
    }
}
