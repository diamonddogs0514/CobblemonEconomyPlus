package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.content.EconomyContent;
import com.diamo.cobblemoneconomyplus.content.ShopEditorItem;
import com.diamo.cobblemoneconomyplus.content.ShopkeeperSpawnEggItem;
import com.diamo.cobblemoneconomyplus.content.TrainerCardItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Fabric-side item and creative-tab registration. */
public final class EconomyContentFabric {
    private static final Item SHOPKEEPER_SPAWN_EGG = new ShopkeeperSpawnEggItem(new Item.Properties());
    private static final Item SHOP_EDITOR = new ShopEditorItem(new Item.Properties());
    private static final Item TRAINER_CARD = new TrainerCardItem(new Item.Properties());
    private static boolean registered;

    private EconomyContentFabric() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        Registry.register(BuiltInRegistries.ITEM, EconomyContent.SHOPKEEPER_SPAWN_EGG_ID, SHOPKEEPER_SPAWN_EGG);
        Registry.register(BuiltInRegistries.ITEM, EconomyContent.SHOP_EDITOR_ID, SHOP_EDITOR);
        Registry.register(BuiltInRegistries.ITEM, EconomyContent.TRAINER_CARD_ID, TRAINER_CARD);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EconomyContent.CREATIVE_TAB_ID,
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.cobblemon_economy_plus"))
                        .icon(() -> new ItemStack(SHOPKEEPER_SPAWN_EGG))
                        .displayItems((parameters, output) -> {
                            output.accept(SHOPKEEPER_SPAWN_EGG);
                            output.accept(SHOP_EDITOR);
                            output.accept(TRAINER_CARD);
                        })
                        .build()
        );
    }
}
