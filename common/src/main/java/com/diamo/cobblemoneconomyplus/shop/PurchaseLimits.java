package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.config.ModConfig;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Server-side purchase limits ({@code limit}, per player) and stock ({@code stock}, shared) for shop entries.
 * Everything is checked here at purchase time; what the shop screen shows is only informational.
 */
public final class PurchaseLimits {
    /** Value used for "no limit" / "no stock" (also on the wire). */
    public static final int UNLIMITED = -1;

    private PurchaseLimits() {
    }

    /**
     * What a player can still buy of one entry. {@code limitLeft} / {@code stockLeft} are {@link #UNLIMITED} when the
     * entry has no such restriction (or limits are switched off in config.json).
     */
    public record Availability(int limitMax, int limitLeft, ShopDefinition.Period period, int stockLeft,
                               long restockAt) {
        public static final Availability NONE =
                new Availability(UNLIMITED, UNLIMITED, ShopDefinition.Period.NEVER, UNLIMITED, -1L);

        /** The most bundles that can be bought now (Integer.MAX_VALUE when unrestricted). */
        public int allowed() {
            int allowed = Integer.MAX_VALUE;
            if (limitLeft != UNLIMITED) {
                allowed = Math.min(allowed, limitLeft);
            }
            if (stockLeft != UNLIMITED) {
                allowed = Math.min(allowed, stockLeft);
            }
            return allowed;
        }
    }

    public static boolean enabled() {
        return ModConfig.get().purchaseLimitsEnabled();
    }

    public static Availability check(MinecraftServer server, UUID playerId, String shopId, ShopDefinition.Entry entry) {
        if (!enabled() || !entry.limited()) {
            return Availability.NONE;
        }
        PurchaseSavedData data = PurchaseSavedData.get(server);
        String key = entry.counterKey(shopId);
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();

        int limitMax = UNLIMITED;
        int limitLeft = UNLIMITED;
        ShopDefinition.Period period = ShopDefinition.Period.NEVER;
        if (entry.limit() != null) {
            limitMax = entry.limit().perPlayer();
            limitLeft = PurchaseRules.limitLeft(entry.limit(), data.counter(playerId, key), today);
            period = entry.limit().period();
        }
        int stockLeft = UNLIMITED;
        long restockAt = -1L;
        if (entry.stock() != null) {
            stockLeft = PurchaseRules.stockLeft(entry.stock(), data.stock(key), now);
            restockAt = PurchaseRules.restockAt(entry.stock(), data.stock(key), now);
        }
        return new Availability(limitMax, limitLeft, period, stockLeft, restockAt);
    }

    /** Counts a completed purchase of {@code quantity} bundles. */
    public static void record(MinecraftServer server, UUID playerId, String shopId, ShopDefinition.Entry entry,
                              int quantity) {
        if (!enabled() || !entry.limited()) {
            return;
        }
        PurchaseSavedData data = PurchaseSavedData.get(server);
        String key = entry.counterKey(shopId);
        if (entry.limit() != null) {
            data.setCounter(playerId, key,
                    PurchaseRules.afterPurchase(entry.limit(), data.counter(playerId, key), LocalDate.now(), quantity));
        }
        if (entry.stock() != null) {
            data.setStock(key, PurchaseRules.afterSale(entry.stock(), data.stock(key), System.currentTimeMillis(), quantity));
        }
    }
}
