package com.diamo.cobblemoneconomyplus.shop;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A configured shop and its fixed-price item bundles.
 *
 * <p>{@code theme} is the shop GUI's default look (see {@link ShopThemes}); a shopkeeper can override it.
 * {@code subtitle} is the optional header tagline; empty means the theme's default text.
 * {@code sellMultiplier} scales the global item values (item_values.json) into this shop's buy-back
 * prices; 0 disables selling in this shop.</p>
 */
public record ShopDefinition(String id, String displayName, String theme, String subtitle, double sellMultiplier,
                             List<Entry> entries) {
    public ShopDefinition {
        entries = List.copyOf(entries);
    }

    /**
     * One bundle on sale. {@code limit} (per player) and {@code stock} (server-wide) are optional and count bundles,
     * i.e. the quantity the player picks in the shop.
     */
    public record Entry(ResourceLocation itemId, int amount, long price, @Nullable Limit limit, @Nullable Stock stock) {
        public Entry(ResourceLocation itemId, int amount, long price) {
            this(itemId, amount, price, null, null);
        }

        public boolean limited() {
            return limit != null || stock != null;
        }

        /**
         * Key under which purchases of this entry are counted. It ignores the price and the entry's position, so
         * reordering entries or changing a price keeps what was already bought.
         */
        public String counterKey(String shopId) {
            return shopId + "/" + itemId + "*" + amount;
        }
    }

    /** At most {@code perPlayer} bundles per player in each {@code period}. */
    public record Limit(int perPlayer, Period period) {
    }

    /**
     * {@code max} bundles shared by all players. With {@code restockMinutes > 0}, the stock fills up again that many
     * minutes after the first sale since the last restock; 0 never restocks.
     */
    public record Stock(int max, int restockMinutes) {
    }

    /** When per-player purchase counts start over (server time). */
    public enum Period {
        DAILY("daily"),
        /** Weeks start on Monday. */
        WEEKLY("weekly"),
        NEVER("never");

        private final String id;

        Period(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** An id for the period containing {@code day}: its first day as an epoch day (0 for {@link #NEVER}). */
        public long periodStart(LocalDate day) {
            return switch (this) {
                case DAILY -> day.toEpochDay();
                case WEEKLY -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
                case NEVER -> 0L;
            };
        }

        public static Optional<Period> byId(String id) {
            String wanted = id.toLowerCase(Locale.ROOT);
            for (Period period : values()) {
                if (period.id.equals(wanted)) {
                    return Optional.of(period);
                }
            }
            return Optional.empty();
        }
    }
}
