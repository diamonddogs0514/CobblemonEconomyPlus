package com.diamo.cobblemoneconomyplus.shop;

import java.util.List;

/**
 * Ids of the boutique GUI themes. The client maps each id to colours and motifs
 * (see {@code BoutiqueTheme}); unknown ids simply fall back to the default there.
 */
public final class ShopThemes {
    public static final String DEFAULT = "pokeball";
    public static final List<String> IDS = List.of(
            "pokeball", "greatball", "ultraball", "masterball", "tm", "berry",
            "stone", "wood", "dirt", "ore", "iron"
    );

    private ShopThemes() {
    }

    public static boolean isValid(String id) {
        return id != null && IDS.contains(id);
    }

    /** Next theme after {@code current} (or the default when unset/unknown), wrapping at both ends. */
    public static String cycle(String current, int direction) {
        int index = IDS.indexOf(current == null ? DEFAULT : current);
        if (index < 0) {
            index = IDS.indexOf(DEFAULT);
        }
        return IDS.get(Math.floorMod(index + direction, IDS.size()));
    }
}
