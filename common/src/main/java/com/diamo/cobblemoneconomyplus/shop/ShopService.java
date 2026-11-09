package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.content.ShopEditorItem;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Authoritative shop-opening, purchasing and selling logic. Every transaction is validated server-side. */
public final class ShopService {
    private static final int MAX_PURCHASE_QUANTITY = 64;
    /** A full main inventory: 36 slots of 64. */
    private static final int MAX_SELL_QUANTITY = 36 * 64;
    private static final double MAX_SHOP_DISTANCE_SQUARED = 36.0D;
    // Server-thread only. Entries are removed when invalid and when the player disconnects.
    private static final Map<UUID, ShopSession> sessions = new HashMap<>();

    private ShopService() {
    }

    /** Returns true when the interaction belongs to this mod and should consume the NPC interaction. */
    public static boolean tryOpen(ServerPlayer player, Entity target) {
        if (ShopEditorItem.isEditor(player.getMainHandItem())) {
            return false;
        }
        Optional<String> shopId = Shopkeeper.shopId(target);
        if (shopId.isEmpty() || !Shopkeeper.canBeShopkeeper(target)) {
            return false;
        }

        ShopDefinition shop = ShopManager.get(player.server, shopId.get()).orElse(null);
        if (shop == null) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_missing", shopId.get()), true);
            return true;
        }

        sessions.put(player.getUUID(), new ShopSession(shop.id(), target.getUUID()));
        sendShop(player, shop, target);
        return true;
    }

    /** Forgets a player's open shop (call on disconnect). */
    public static void onDisconnect(UUID playerId) {
        sessions.remove(playerId);
    }

    private static void sendShop(ServerPlayer player, ShopDefinition shop, Entity shopkeeper) {
        List<EconomyPayloads.ShopEntryPayload> entries = shop.entries().stream()
                .map(entry -> new EconomyPayloads.ShopEntryPayload(
                        entry.itemId().toString(),
                        entry.amount(),
                        entry.price()
                ))
                .toList();
        List<EconomyPayloads.ShopEntryPayload> sellEntries = ShopManager.sellOffers(shop).stream()
                .map(offer -> new EconomyPayloads.ShopEntryPayload(offer.itemId().toString(), 1, offer.unitPrice()))
                .toList();
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.OpenShopPayload(
                shop.id(),
                shop.displayName(),
                Shopkeeper.themeId(shopkeeper).orElse(shop.theme()),
                shop.subtitle(),
                EconomyService.getBalance(player),
                entries,
                sellEntries
        ));
    }

    /**
     * Checks that the player has this shop open and is still near its shopkeeper. On failure the player is told
     * why and {@code null} is returned.
     */
    private static Entity validateSession(ServerPlayer player, String shopId) {
        ShopSession session = sessions.get(player.getUUID());
        if (session == null || !session.shopId.equals(shopId)) {
            // The player never opened this shop (or it was closed by disconnect / removal).
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_closed"), true);
            return null;
        }

        Entity shopkeeper = player.serverLevel().getEntity(session.shopkeeperId);
        if (shopkeeper == null || !Shopkeeper.shopId(shopkeeper).filter(session.shopId::equals).isPresent()) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_closed"), true);
            return null;
        }
        if (player.distanceToSqr(shopkeeper) > MAX_SHOP_DISTANCE_SQUARED) {
            // Keep the session: walking back is enough, no need to re-open the shop.
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_too_far"), true);
            return null;
        }
        return shopkeeper;
    }

    // ------------------------------------------------------------------ buying

    public static void handlePurchase(ServerPlayer player, EconomyPayloads.ShopActionPayload action) {
        Entity shopkeeper = validateSession(player, action.shopId());
        if (shopkeeper == null) {
            return;
        }

        if (action.entryIndex() < 0 || action.quantity() < 1 || action.quantity() > MAX_PURCHASE_QUANTITY) {
            return;
        }

        ShopDefinition shop = ShopManager.get(player.server, action.shopId()).orElse(null);
        if (shop == null) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_closed"), true);
            return;
        }

        // The client sends what it displayed. If the shop was reloaded or edited in the meantime the
        // index may now point at something else, so compare before charging anything.
        if (action.entryIndex() >= shop.entries().size() || !matchesDisplayed(shop.entries().get(action.entryIndex()), action)) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_changed"), true);
            sendShop(player, shop, shopkeeper);
            return;
        }

        ShopDefinition.Entry entry = shop.entries().get(action.entryIndex());
        final int totalItems;
        final long totalPrice;
        try {
            totalItems = Math.multiplyExact(entry.amount(), action.quantity());
            totalPrice = Math.multiplyExact(entry.price(), action.quantity());
        } catch (ArithmeticException exception) {
            return;
        }

        if (totalItems < 1 || totalItems > 64) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.purchase_limit"), true);
            return;
        }

        ItemStack purchased = new ItemStack(BuiltInRegistries.ITEM.get(entry.itemId()), totalItems);
        if (purchased.isEmpty() || !canFit(player.getInventory(), purchased)) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.inventory_full"), true);
            return;
        }

        if (!EconomyService.withdraw(player.server, player.getUUID(), totalPrice, "shop:" + shop.id(),
                "buy " + entry.itemId() + " x" + totalItems)) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.not_enough_funds"), true);
            EconomyService.sync(player);
            return;
        }

        Component purchasedName = purchased.getHoverName();
        // Never lose paid-for items: whatever the inventory cannot take is dropped at the player's feet.
        player.getInventory().add(purchased);
        if (!purchased.isEmpty()) {
            player.drop(purchased, false);
        }
        EconomyService.sync(player);
        player.displayClientMessage(Component.translatable(
                "message.cobblemon_economy_plus.purchase_success",
                purchasedName,
                EconomyFormat.money(totalPrice)
        ), true);
    }

    private static boolean matchesDisplayed(ShopDefinition.Entry entry, EconomyPayloads.ShopActionPayload action) {
        return entry.itemId().toString().equals(action.itemId())
                && entry.amount() == action.amount()
                && entry.price() == action.price();
    }

    /**
     * Whether the main inventory (the 36 slots {@link Inventory#add} can fill) has room. Armor and offhand
     * slots are deliberately not counted: items are never inserted there.
     */
    private static boolean canFit(Inventory inventory, ItemStack stack) {
        return canFit(inventory.items, stack);
    }

    /** The slot-level check behind {@link #canFit(Inventory, ItemStack)}, usable without an inventory. */
    static boolean canFit(Iterable<ItemStack> slots, ItemStack stack) {
        int remaining = stack.getCount();
        for (ItemStack existing : slots) {
            if (remaining <= 0) {
                break;
            }
            if (existing.isEmpty()) {
                remaining -= Math.min(remaining, stack.getMaxStackSize());
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                remaining -= Math.min(remaining, Math.max(0, existing.getMaxStackSize() - existing.getCount()));
            }
        }
        return remaining <= 0;
    }

    // ------------------------------------------------------------------ selling

    public static void handleSale(ServerPlayer player, EconomyPayloads.SellActionPayload action) {
        Entity shopkeeper = validateSession(player, action.shopId());
        if (shopkeeper == null) {
            return;
        }
        if (action.quantity() < 1 || action.quantity() > MAX_SELL_QUANTITY) {
            return;
        }

        ShopDefinition shop = ShopManager.get(player.server, action.shopId()).orElse(null);
        if (shop == null) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_closed"), true);
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(action.itemId());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return;
        }
        long unitPrice = ShopManager.sellUnitPrice(shop, itemId);
        if (unitPrice < 1L) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.not_sellable"), true);
            sendShop(player, shop, shopkeeper);
            return;
        }
        // Same idea as purchases: the price the player saw must still be the price in force.
        if (unitPrice != action.unitPrice()) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.shop_changed"), true);
            sendShop(player, shop, shopkeeper);
            return;
        }

        final long total;
        try {
            total = Math.multiplyExact(unitPrice, (long) action.quantity());
        } catch (ArithmeticException exception) {
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        Inventory inventory = player.getInventory();
        if (countSellable(inventory, item) < action.quantity()) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.not_enough_items"), true);
            return;
        }

        Component itemName = new ItemStack(item).getHoverName();
        removeSellable(inventory, item, action.quantity());
        inventory.setChanged();
        EconomyService.deposit(player.server, player.getUUID(), total, "shop:" + shop.id(),
                "sell " + itemId + " x" + action.quantity());
        EconomyService.sync(player);
        player.displayClientMessage(Component.translatable(
                "message.cobblemon_economy_plus.sell_success",
                action.quantity(),
                itemName,
                EconomyFormat.money(total)
        ), true);
    }

    private static int countSellable(Inventory inventory, Item item) {
        int count = 0;
        for (ItemStack stack : inventory.items) {
            if (SellableStacks.isPristine(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeSellable(Inventory inventory, Item item, int quantity) {
        int remaining = quantity;
        for (ItemStack stack : inventory.items) {
            if (remaining <= 0) {
                break;
            }
            if (SellableStacks.isPristine(stack, item)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    private record ShopSession(String shopId, UUID shopkeeperId) {
    }
}
