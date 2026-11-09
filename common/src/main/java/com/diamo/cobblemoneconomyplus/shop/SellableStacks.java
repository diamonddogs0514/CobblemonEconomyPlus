package com.diamo.cobblemoneconomyplus.shop;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which stacks count as "sellable". Only pristine stacks qualify (no custom name, enchantment, damage, stored
 * contents or other data), so a shulker box full of items or an enchanted tool can never be sold at the base
 * price of the plain item. Shared by the client (what it lists) and the server (what it actually takes).
 */
public final class SellableStacks {
    private SellableStacks() {
    }

    public static boolean isPristine(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.is(item) && stack.getComponentsPatch().isEmpty();
    }
}
