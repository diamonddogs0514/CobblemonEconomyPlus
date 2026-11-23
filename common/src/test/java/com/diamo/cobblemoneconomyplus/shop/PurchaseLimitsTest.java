package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.diamo.cobblemoneconomyplus.TestBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseLimitsTest {
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);
    private static final ShopDefinition.Limit WEEKLY_TWO = new ShopDefinition.Limit(2, ShopDefinition.Period.WEEKLY);
    private static final ShopDefinition.Stock THREE_DAILY = new ShopDefinition.Stock(3, 1440);
    private static final long MINUTE = 60_000L;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    // ------------------------------------------------------------------ periods

    @Test
    void periodsStartAtMidnightAndOnMonday() {
        assertEquals(WEDNESDAY.toEpochDay(), ShopDefinition.Period.DAILY.periodStart(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(), ShopDefinition.Period.WEEKLY.periodStart(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(),
                ShopDefinition.Period.WEEKLY.periodStart(LocalDate.of(2026, 11, 16)));
        assertEquals(LocalDate.of(2026, 11, 16).toEpochDay(),
                ShopDefinition.Period.WEEKLY.periodStart(LocalDate.of(2026, 11, 22)));
        assertEquals(0L, ShopDefinition.Period.NEVER.periodStart(WEDNESDAY));
    }

    // ------------------------------------------------------------------ per-player limits

    @Test
    void aLimitCountsDownAndStartsOverNextWeek() {
        assertEquals(2, PurchaseRules.limitLeft(WEEKLY_TWO, null, WEDNESDAY));
        PurchaseRules.Counter counter = PurchaseRules.afterPurchase(WEEKLY_TWO, null, WEDNESDAY, 1);
        assertEquals(1, PurchaseRules.limitLeft(WEEKLY_TWO, counter, WEDNESDAY));
        counter = PurchaseRules.afterPurchase(WEEKLY_TWO, counter, WEDNESDAY.plusDays(2), 1);
        assertEquals(0, PurchaseRules.limitLeft(WEEKLY_TWO, counter, WEDNESDAY.plusDays(4)), "same week (Sunday)");
        assertEquals(2, PurchaseRules.limitLeft(WEEKLY_TWO, counter, WEDNESDAY.plusDays(5)), "next Monday");
        // Buying in the new week starts a new count instead of adding to the old one.
        counter = PurchaseRules.afterPurchase(WEEKLY_TWO, counter, WEDNESDAY.plusDays(5), 1);
        assertEquals(1, PurchaseRules.limitLeft(WEEKLY_TWO, counter, WEDNESDAY.plusDays(5)));
    }

    @Test
    void aNeverLimitNeverStartsOver() {
        ShopDefinition.Limit once = new ShopDefinition.Limit(1, ShopDefinition.Period.NEVER);
        PurchaseRules.Counter counter = PurchaseRules.afterPurchase(once, null, WEDNESDAY, 1);
        assertEquals(0, PurchaseRules.limitLeft(once, counter, WEDNESDAY.plusYears(3)));
    }

    // ------------------------------------------------------------------ stock

    @Test
    void stockIsSharedAndRefillsAfterTheRestockTime() {
        long start = 1_000_000_000L;
        assertEquals(3, PurchaseRules.stockLeft(THREE_DAILY, null, start));
        PurchaseRules.StockState state = PurchaseRules.afterSale(THREE_DAILY, null, start, 2);
        assertEquals(1, PurchaseRules.stockLeft(THREE_DAILY, state, start + 10 * MINUTE));
        // The restock time counts from the first sale, not the last one.
        state = PurchaseRules.afterSale(THREE_DAILY, state, start + 600 * MINUTE, 1);
        assertEquals(0, PurchaseRules.stockLeft(THREE_DAILY, state, start + 1439 * MINUTE));
        assertEquals(start + 1440 * MINUTE, PurchaseRules.restockAt(THREE_DAILY, state, start + 1439 * MINUTE));
        assertEquals(3, PurchaseRules.stockLeft(THREE_DAILY, state, start + 1440 * MINUTE));
        assertEquals(-1L, PurchaseRules.restockAt(THREE_DAILY, state, start + 1440 * MINUTE));
    }

    @Test
    void stockWithoutRestockStaysSoldOut() {
        ShopDefinition.Stock once = new ShopDefinition.Stock(1, 0);
        PurchaseRules.StockState state = PurchaseRules.afterSale(once, null, 0L, 1);
        assertEquals(0, PurchaseRules.stockLeft(once, state, Long.MAX_VALUE / 2));
        assertEquals(-1L, PurchaseRules.restockAt(once, state, 5L));
    }

    @Test
    void loweringTheMaximumNeverGoesNegative() {
        PurchaseRules.StockState state = PurchaseRules.afterSale(new ShopDefinition.Stock(10, 0), null, 0L, 5);
        assertEquals(0, PurchaseRules.stockLeft(new ShopDefinition.Stock(3, 0), state, 1L));
    }

    @Test
    void availabilityAllowsTheSmallerOfLimitAndStock() {
        assertEquals(Integer.MAX_VALUE, PurchaseLimits.Availability.NONE.allowed());
        assertEquals(1, new PurchaseLimits.Availability(5, 4, ShopDefinition.Period.DAILY, 1, -1L).allowed());
        assertEquals(0, new PurchaseLimits.Availability(5, 0, ShopDefinition.Period.DAILY, -1, -1L).allowed());
        assertEquals(2, new PurchaseLimits.Availability(-1, -1, ShopDefinition.Period.NEVER, 2, -1L).allowed());
    }

    // ------------------------------------------------------------------ saved data

    @Test
    void savedDataKeepsCountsAndStock() {
        UUID player = UUID.randomUUID();
        PurchaseSavedData data = new PurchaseSavedData();
        data.setCounter(player, "general/minecraft:apple*1", new PurchaseRules.Counter(20_000L, 2));
        data.setStock("general/minecraft:apple*1", new PurchaseRules.StockState(3, 123L));

        PurchaseSavedData loaded = PurchaseSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(new PurchaseRules.Counter(20_000L, 2), loaded.counter(player, "general/minecraft:apple*1"));
        assertEquals(new PurchaseRules.StockState(3, 123L), loaded.stock("general/minecraft:apple*1"));

        assertTrue(loaded.resetPlayer(player));
        assertFalse(loaded.resetPlayer(player));
        assertNull(loaded.counter(player, "general/minecraft:apple*1"));
        assertEquals(1, loaded.restockAll());
        assertNull(loaded.stock("general/minecraft:apple*1"));
    }

    // ------------------------------------------------------------------ shops.json

    @Test
    void limitsAndStockAreReadFromShopsJson() {
        ShopDefinition shop = ShopManager.parse("""
                { "shops": [ { "id": "rare", "entries": [
                  { "item": "minecraft:diamond", "amount": 1, "price": 500,
                    "limit": { "per_player": 1, "period": "weekly" },
                    "stock": { "max": 3, "restock_minutes": 1440 } },
                  { "item": "minecraft:apple", "amount": 1, "price": 5, "limit": { "per_player": 4 } },
                  { "item": "minecraft:bread", "amount": 1, "price": 5 }
                ] } ] }
                """).get("rare");
        ShopDefinition.Entry diamond = shop.entries().get(0);
        assertEquals(new ShopDefinition.Limit(1, ShopDefinition.Period.WEEKLY), diamond.limit());
        assertEquals(new ShopDefinition.Stock(3, 1440), diamond.stock());
        assertEquals(ShopDefinition.Period.NEVER, shop.entries().get(1).limit().period(), "period defaults to never");
        assertNull(shop.entries().get(1).stock());
        assertFalse(shop.entries().get(2).limited());
        assertEquals("rare/minecraft:diamond*1", diamond.counterKey("rare"));
    }

    @Test
    void anEntryWithABrokenLimitIsSkippedRatherThanSoldWithoutIt() {
        List<ConfigMessage> warnings = new ArrayList<>();
        ShopDefinition shop = ShopManager.parse("""
                { "shops": [ { "id": "rare", "entries": [
                  { "item": "minecraft:diamond", "amount": 1, "price": 500, "limit": { "per_player": 0 } },
                  { "item": "minecraft:emerald", "amount": 1, "price": 500, "limit": { "per_player": 1, "period": "monthly" } },
                  { "item": "minecraft:gold_ingot", "amount": 1, "price": 500, "stock": { "max": 2, "restock_minutes": -1 } },
                  { "item": "minecraft:iron_ingot", "amount": 1, "price": 500, "stock": 5 },
                  { "item": "minecraft:apple", "amount": 1, "price": 5 }
                ] } ] }
                """, warnings).get("rare");
        assertEquals(1, shop.entries().size());
        assertEquals(4, warnings.size(), warnings::toString);
    }
}
