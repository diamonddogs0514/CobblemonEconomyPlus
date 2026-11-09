package com.diamo.cobblemoneconomyplus.content;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.resources.ResourceLocation;

/** Shared registry names. Actual registry registration remains loader-specific. */
public final class EconomyContent {
    public static final ResourceLocation SHOPKEEPER_SPAWN_EGG_ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonEconomyPlus.MOD_ID, "shopkeeper_spawn_egg"
    );
    public static final ResourceLocation SHOP_EDITOR_ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonEconomyPlus.MOD_ID, "shop_editor"
    );
    public static final ResourceLocation TRAINER_CARD_ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonEconomyPlus.MOD_ID, "trainer_card"
    );
    public static final ResourceLocation CREATIVE_TAB_ID = ResourceLocation.fromNamespaceAndPath(
            CobblemonEconomyPlus.MOD_ID, "main"
    );

    private EconomyContent() {
    }
}
