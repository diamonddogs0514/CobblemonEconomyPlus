package com.diamo.cobblemoneconomyplus.shop;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

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

    public record Entry(ResourceLocation itemId, int amount, long price) {
    }
}
