package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Converts shops and item values to the JSON layout of shops.json / item_values.json, and back. Used by the admin
 * screen on both sides: the server sends the shops in this form and the client sends edited shops back in it, so the
 * server validates them with the same parser as the files.
 */
public final class ShopJson {
    /** Pretty printing for the files. Non-ASCII text (Japanese names) is written as is, not as \\u escapes. */
    public static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ShopJson() {
    }

    public static JsonObject toJson(ShopDefinition shop) {
        JsonObject json = new JsonObject();
        json.addProperty("id", shop.id());
        json.add("name", localized(shop.displayName()));
        if (!shop.subtitle().isEmpty()) {
            json.add("subtitle", localized(shop.subtitle()));
        }
        json.addProperty("theme", shop.theme());
        json.addProperty("sell_multiplier", shop.sellMultiplier());
        JsonArray entries = new JsonArray();
        for (ShopDefinition.Entry entry : shop.entries()) {
            entries.add(toJson(entry));
        }
        json.add("entries", entries);
        return json;
    }

    public static JsonObject toJson(ShopDefinition.Entry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("item", entry.itemId().toString());
        json.addProperty("amount", entry.amount());
        json.addProperty("price", entry.price());
        if (entry.limit() != null) {
            JsonObject limit = new JsonObject();
            limit.addProperty("per_player", entry.limit().perPlayer());
            limit.addProperty("period", entry.limit().period().id());
            json.add("limit", limit);
        }
        if (entry.stock() != null) {
            JsonObject stock = new JsonObject();
            stock.addProperty("max", entry.stock().max());
            stock.addProperty("restock_minutes", entry.stock().restockMinutes());
            json.add("stock", stock);
        }
        return json;
    }

    /** {@code {"shops": [...]}} for all shops, in order. */
    public static String shopsFile(Collection<ShopDefinition> shops) {
        JsonArray array = new JsonArray();
        shops.forEach(shop -> array.add(toJson(shop)));
        JsonObject root = new JsonObject();
        root.add("shops", array);
        return PRETTY.toJson(root);
    }

    /** {@code {"values": {...}}}. */
    public static String itemValuesFile(Map<ResourceLocation, Long> values) {
        JsonObject object = new JsonObject();
        values.forEach((id, value) -> object.addProperty(id.toString(), value));
        JsonObject root = new JsonObject();
        root.add("values", object);
        return PRETTY.toJson(root);
    }

    /** Reads shops sent by {@link #shopsFile}. Never throws: unreadable text gives an empty list. */
    public static List<ShopDefinition> readShops(String json) {
        try {
            return new ArrayList<>(ShopManager.parse(json, new ArrayList<>()).values());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** Reads item values sent by {@link #itemValuesFile}. Never throws. */
    public static Map<ResourceLocation, Long> readItemValues(String json) {
        try {
            return ShopManager.parseItemValues(json, new ArrayList<>());
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    /**
     * Parses one shop object exactly like shops.json does, collecting every problem in {@code problems}. Returns null
     * when the shop could not be read at all.
     */
    public static ShopDefinition parseShop(JsonObject json, List<ConfigMessage> problems) {
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            array.add(json);
            root.add("shops", array);
            Map<String, ShopDefinition> parsed = ShopManager.parse(root.toString(), problems);
            return parsed.isEmpty() ? null : parsed.values().iterator().next();
        } catch (RuntimeException exception) {
            problems.add(ShopManager.describe(exception));
            return null;
        }
    }

    /** A stored name or subtitle (plain text or compact per-language JSON) as it should appear in the file. */
    static JsonElement localized(String raw) {
        if (raw.startsWith("{")) {
            try {
                JsonElement parsed = JsonParser.parseString(raw);
                if (parsed.isJsonObject()) {
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // Not JSON after all: keep it as text.
            }
        }
        return new com.google.gson.JsonPrimitive(raw);
    }
}
