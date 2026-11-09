package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellableStacksTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void plainStacksOfTheRightItemAreSellable() {
        assertTrue(SellableStacks.isPristine(new ItemStack(Items.APPLE, 12), Items.APPLE));
    }

    @Test
    void emptyStacksAndOtherItemsAreNot() {
        assertFalse(SellableStacks.isPristine(ItemStack.EMPTY, Items.APPLE));
        assertFalse(SellableStacks.isPristine(new ItemStack(Items.BREAD), Items.APPLE));
    }

    @Test
    void renamedStacksAreNotSellable() {
        ItemStack stack = new ItemStack(Items.APPLE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Golden Delicious"));
        assertFalse(SellableStacks.isPristine(stack, Items.APPLE));
    }

    @Test
    void damagedToolsAreNotSellable() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        assertTrue(SellableStacks.isPristine(sword, Items.IRON_SWORD));
        sword.setDamageValue(5);
        assertFalse(SellableStacks.isPristine(sword, Items.IRON_SWORD));
    }
}
