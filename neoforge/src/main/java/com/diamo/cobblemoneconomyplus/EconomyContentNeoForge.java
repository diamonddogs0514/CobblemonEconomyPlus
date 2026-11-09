package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.content.ShopEditorItem;
import com.diamo.cobblemoneconomyplus.content.ShopkeeperSpawnEggItem;
import com.diamo.cobblemoneconomyplus.content.TrainerCardItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge-side item and creative-tab registration. */
public final class EconomyContentNeoForge {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CobblemonEconomyPlus.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB,
            CobblemonEconomyPlus.MOD_ID
    );

    public static final DeferredItem<ShopkeeperSpawnEggItem> SHOPKEEPER_SPAWN_EGG = ITEMS.register(
            "shopkeeper_spawn_egg",
            () -> new ShopkeeperSpawnEggItem(new Item.Properties())
    );
    public static final DeferredItem<ShopEditorItem> SHOP_EDITOR = ITEMS.register(
            "shop_editor",
            () -> new ShopEditorItem(new Item.Properties())
    );
    public static final DeferredItem<TrainerCardItem> TRAINER_CARD = ITEMS.register(
            "trainer_card",
            () -> new TrainerCardItem(new Item.Properties())
    );
    private static final net.neoforged.neoforge.registries.DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cobblemon_economy_plus"))
                    .icon(() -> new ItemStack(SHOPKEEPER_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(SHOPKEEPER_SPAWN_EGG.get());
                        output.accept(SHOP_EDITOR.get());
                        output.accept(TRAINER_CARD.get());
                    })
                    .build());

    private EconomyContentNeoForge() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
