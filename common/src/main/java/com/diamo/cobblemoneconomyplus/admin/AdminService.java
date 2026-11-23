package com.diamo.cobblemoneconomyplus.admin;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.config.ConfigFiles;
import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermission;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermissions;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopJson;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.diamo.cobblemoneconomyplus.shop.ShopService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the admin screen. Every edit is checked here (permission, that the screen saw the current shops,
 * and the same validation as shops.json), written to the file with a backup, reloaded, and recorded in the
 * {@link AdminLog}. The files stay the source of truth: the screen only edits them.
 */
public final class AdminService {
    /** Highest base value an item can be given from the screen. */
    static final long MAX_ITEM_VALUE = 1_000_000_000_000L;

    private AdminService() {
    }

    /** Opens the admin screen for the player, optionally on one shop. */
    public static void open(ServerPlayer player, String focusShopId) {
        if (!allowed(player)) {
            return;
        }
        MinecraftServer server = player.server;
        String shops = ShopJson.shopsFile(ShopManager.all(server));
        String values = ShopJson.itemValuesFile(ShopManager.itemValues(server));
        if (shops.length() > EconomyPayloads.MAX_ADMIN_JSON || values.length() > EconomyPayloads.MAX_ADMIN_JSON) {
            player.sendSystemMessage(Component.translatable("message.cobblemon_economy_plus.admin.too_large"));
            return;
        }
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.AdminOpenPayload(
                ShopManager.revision(server), focusShopId == null ? "" : focusShopId, shops, values));
    }

    // ------------------------------------------------------------------ shops

    public static void saveShop(ServerPlayer player, EconomyPayloads.AdminSaveShopPayload request) {
        if (!allowed(player) || !current(player, request.revision())) {
            return;
        }
        MinecraftServer server = player.server;
        JsonObject submitted;
        try {
            JsonElement element = JsonParser.parseString(request.shopJson());
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("not an object");
            }
            submitted = element.getAsJsonObject();
        } catch (RuntimeException exception) {
            fail(player, "invalid", ShopManager.describe(exception).component());
            return;
        }

        List<ConfigMessage> problems = new ArrayList<>();
        ShopDefinition shop = ShopJson.parseShop(submitted, problems);
        if (shop == null || !problems.isEmpty()) {
            // The screen must not save anything the file loader would skip or change.
            fail(player, "invalid", problems.isEmpty() ? "?" : problems.get(0).component());
            return;
        }
        boolean creating = request.originalId().isEmpty();
        if (creating && ShopManager.get(server, shop.id()).isPresent()) {
            fail(player, "id_taken", shop.id());
            return;
        }
        if (!creating && !shop.id().equals(request.originalId())) {
            fail(player, "id_changed", request.originalId());
            return;
        }

        JsonObject root = shopsRoot(server);
        AdminEdits.putShop(root, shop.id(), ShopJson.toJson(shop));
        if (!writeShops(player, root)) {
            return;
        }
        AdminLog.record(player, creating ? "create_shop" : "save_shop",
                shop.id() + " entries=" + shop.entries().size() + " theme=" + shop.theme()
                        + " sell_multiplier=" + shop.sellMultiplier());
        succeed(player, creating ? "shop_created" : "shop_saved", shop.id());
    }

    public static void deleteShop(ServerPlayer player, EconomyPayloads.AdminDeleteShopPayload request) {
        if (!allowed(player) || !current(player, request.revision())) {
            return;
        }
        MinecraftServer server = player.server;
        String id = request.shopId();
        if (ShopManager.get(server, id).isEmpty()) {
            fail(player, "unknown_shop", id);
            return;
        }
        if (ShopManager.all(server).size() <= 1) {
            fail(player, "last_shop", id);
            return;
        }
        JsonObject root = shopsRoot(server);
        AdminEdits.removeShop(root, id);
        if (!writeShops(player, root)) {
            return;
        }
        AdminLog.record(player, "delete_shop", id);
        succeed(player, "shop_deleted", id);
    }

    // ------------------------------------------------------------------ item values

    public static void setItemValue(ServerPlayer player, EconomyPayloads.AdminItemValuePayload request) {
        if (!allowed(player) || !current(player, request.revision())) {
            return;
        }
        MinecraftServer server = player.server;
        ResourceLocation itemId = ResourceLocation.tryParse(request.itemId());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            fail(player, "unknown_item", request.itemId());
            return;
        }
        if (request.value() < 0L || request.value() > MAX_ITEM_VALUE) {
            fail(player, "value_range", Long.toString(MAX_ITEM_VALUE));
            return;
        }

        Path path = ShopManager.itemValuesPath(server);
        JsonObject root = null;
        try {
            if (Files.exists(path)) {
                root = AdminEdits.valuesRoot(Files.readString(path, StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not read {}; rebuilding it from the loaded values", path, exception);
        }
        if (root == null) {
            root = JsonParser.parseString(ShopJson.itemValuesFile(ShopManager.itemValues(server))).getAsJsonObject();
        }
        AdminEdits.setItemValue(root, itemId.toString(), request.value());
        if (!write(player, path, root)) {
            return;
        }
        AdminLog.record(player, request.value() > 0L ? "set_item_value" : "remove_item_value",
                itemId + (request.value() > 0L ? " = " + request.value() : ""));
        succeed(player, request.value() > 0L ? "value_set" : "value_removed", itemId.toString());
    }

    // ------------------------------------------------------------------ helpers

    private static boolean allowed(ServerPlayer player) {
        if (EconomyPermissions.has(player, EconomyPermission.ADMIN)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.admin.no_permission"), true);
        return false;
    }

    /** Refuses an edit made on shops that have changed since the screen was opened, and sends the current ones. */
    private static boolean current(ServerPlayer player, int revision) {
        if (revision == ShopManager.revision(player.server)) {
            return true;
        }
        fail(player, "conflict");
        open(player, "");
        return false;
    }

    /** shops.json as a tree; rebuilt from the loaded shops when the file is missing or broken. */
    private static JsonObject shopsRoot(MinecraftServer server) {
        Path path = ShopManager.configPath(server);
        try {
            if (Files.exists(path)) {
                JsonObject root = AdminEdits.shopsRoot(Files.readString(path, StandardCharsets.UTF_8));
                if (root != null) {
                    return root;
                }
            }
        } catch (IOException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not read {}; rebuilding it from the loaded shops", path, exception);
        }
        CobblemonEconomyPlus.LOGGER.warn("{} is missing or not valid JSON; the admin screen rebuilds it from the loaded "
                + "shops (the old file is kept as shops.json.bak)", path);
        return JsonParser.parseString(ShopJson.shopsFile(ShopManager.all(server))).getAsJsonObject();
    }

    private static boolean writeShops(ServerPlayer player, JsonObject root) {
        return write(player, ShopManager.configPath(player.server), root);
    }

    /** Writes the file (with a backup), reloads every config file it affects and refreshes open screens. */
    private static boolean write(ServerPlayer player, Path path, JsonObject root) {
        MinecraftServer server = player.server;
        try {
            ConfigFiles.writeWithBackup(path, ShopJson.PRETTY.toJson(root));
        } catch (IOException exception) {
            CobblemonEconomyPlus.LOGGER.error("Could not write {}", path, exception);
            fail(player, "write_failed", path.getFileName().toString());
            return false;
        }
        ShopManager.Report report = ShopManager.reload(server);
        if (!report.ok()) {
            // Should not happen (the edit was validated), but never hide it.
            fail(player, "reload_failed", report.errors().get(0).component());
        }
        ShopService.refreshOpenShops(server);
        return true;
    }

    private static void succeed(ServerPlayer player, String key, Object... args) {
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.AdminStatusPayload(true,
                Component.translatable("message.cobblemon_economy_plus.admin." + key, args)));
        open(player, "");
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.AdminStatusPayload(false,
                Component.translatable("message.cobblemon_economy_plus.admin." + key, args)));
    }
}
