package com.diamo.cobblemoneconomyplus.shop;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

/** Pure purchase-limit arithmetic, separate from the server so it can be tested on its own. */
final class PurchaseRules {
    private PurchaseRules() {
    }

    /** Bundles a player bought in the period starting at {@code periodStart}. */
    record Counter(long periodStart, int count) {
    }

    /** Bundles sold from a stock since {@code windowStart} (epoch millis of the first sale since the last restock). */
    record StockState(int sold, long windowStart) {
        static final StockState FULL = new StockState(0, 0L);
    }

    /** How many more bundles the player may buy in the current period. */
    static int limitLeft(ShopDefinition.Limit limit, @Nullable Counter counter, LocalDate today) {
        long periodStart = limit.period().periodStart(today);
        int bought = counter != null && counter.periodStart() == periodStart ? counter.count() : 0;
        return Math.max(0, limit.perPlayer() - bought);
    }

    /** The counter after buying {@code quantity} more bundles today. */
    static Counter afterPurchase(ShopDefinition.Limit limit, @Nullable Counter counter, LocalDate today, int quantity) {
        long periodStart = limit.period().periodStart(today);
        int bought = counter != null && counter.periodStart() == periodStart ? counter.count() : 0;
        return new Counter(periodStart, saturatedAdd(bought, quantity));
    }

    /** The stock state at {@code now}: back to full once the restock time since the first sale has passed. */
    static StockState current(ShopDefinition.Stock stock, @Nullable StockState state, long now) {
        if (state == null || state.sold() <= 0) {
            return StockState.FULL;
        }
        long interval = stock.restockMinutes() * 60_000L;
        if (interval > 0L && now - state.windowStart() >= interval) {
            return StockState.FULL;
        }
        return state;
    }

    static int stockLeft(ShopDefinition.Stock stock, @Nullable StockState state, long now) {
        return Math.max(0, stock.max() - current(stock, state, now).sold());
    }

    /** The stock state after selling {@code quantity} more bundles at {@code now}. */
    static StockState afterSale(ShopDefinition.Stock stock, @Nullable StockState state, long now, int quantity) {
        StockState current = current(stock, state, now);
        long windowStart = current.sold() == 0 ? now : current.windowStart();
        return new StockState(saturatedAdd(current.sold(), quantity), windowStart);
    }

    /** Epoch millis when a sold-out or partly sold stock fills up again, or -1 when it never restocks. */
    static long restockAt(ShopDefinition.Stock stock, @Nullable StockState state, long now) {
        StockState current = current(stock, state, now);
        if (stock.restockMinutes() <= 0 || current.sold() == 0) {
            return -1L;
        }
        return current.windowStart() + stock.restockMinutes() * 60_000L;
    }

    private static int saturatedAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
