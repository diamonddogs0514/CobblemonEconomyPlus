package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.TestBootstrap;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The inventory-space check that decides whether a purchase may proceed. */
class ShopServiceTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private static NonNullList<ItemStack> mainInventory() {
        return NonNullList.withSize(36, ItemStack.EMPTY);
    }

    private static void fillWith(NonNullList<ItemStack> slots, ItemStack prototype) {
        for (int i = 0; i < slots.size(); i++) {
            slots.set(i, prototype.copy());
        }
    }

    @Test
    void anEmptyInventoryHoldsAFullStack() {
        assertTrue(ShopService.canFit(mainInventory(), new ItemStack(Items.APPLE, 64)));
    }

    @Test
    void aFullInventoryOfOtherItemsHoldsNothing() {
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        assertFalse(ShopService.canFit(slots, new ItemStack(Items.APPLE, 1)));
    }

    @Test
    void partialStacksOfTheSameItemAreFilledFirst() {
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        slots.set(7, new ItemStack(Items.APPLE, 60));
        assertTrue(ShopService.canFit(slots, new ItemStack(Items.APPLE, 4)));
        assertFalse(ShopService.canFit(slots, new ItemStack(Items.APPLE, 5)));
    }

    @Test
    void stacksAreSplitAcrossSeveralEmptySlots() {
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        slots.set(0, ItemStack.EMPTY);
        // One empty slot holds at most 64 apples, so 65 do not fit; with a second empty slot they do.
        assertTrue(ShopService.canFit(slots, new ItemStack(Items.APPLE, 64)));
        assertFalse(ShopService.canFit(slots, bigStack(Items.APPLE, 65)));
        slots.set(1, ItemStack.EMPTY);
        assertTrue(ShopService.canFit(slots, bigStack(Items.APPLE, 65)));
    }

    /** A stack above the normal maximum, like the one a bulk purchase builds. */
    private static ItemStack bigStack(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(count);
        return stack;
    }

    @Test
    void unstackableItemsNeedOneSlotEach() {
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        slots.set(0, ItemStack.EMPTY);
        slots.set(1, ItemStack.EMPTY);
        assertTrue(ShopService.canFit(slots, bigStack(Items.DIAMOND_SWORD, 2)));
        assertFalse(ShopService.canFit(slots, bigStack(Items.DIAMOND_SWORD, 3)));
    }

    @Test
    void onlyTheMainSlotsCountSoArmorAndOffhandSlotsDoNotHideAFullInventory() {
        // Regression test (issue A1): a full main inventory must be reported as full even though the player's
        // armor and offhand slots are empty. The check is given just the 36 main slots, as the real one is.
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        assertFalse(ShopService.canFit(slots, new ItemStack(Items.IRON_HELMET, 1)));
    }

    @Test
    void sameItemWithDifferentComponentsDoesNotMerge() {
        NonNullList<ItemStack> slots = mainInventory();
        fillWith(slots, new ItemStack(Items.STONE, 64));
        ItemStack renamed = new ItemStack(Items.APPLE, 10);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("x"));
        slots.set(0, renamed);
        assertFalse(ShopService.canFit(slots, new ItemStack(Items.APPLE, 1)));
    }
}
