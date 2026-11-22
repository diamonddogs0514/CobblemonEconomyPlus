package com.diamo.cobblemoneconomyplus.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * Edits to the JSON trees of shops.json and item_values.json. Only the shop or value being edited changes; every
 * other shop (including ones the server skipped as invalid) is left exactly as it was in the file.
 */
final class AdminEdits {
    private AdminEdits() {
    }

    /** Reads a file's text as a shops.json root, or null when it is not one (broken JSON, no "shops" array). */
    static JsonObject shopsRoot(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonObject() && root.getAsJsonObject().has("shops")
                    && root.getAsJsonObject().get("shops").isJsonArray()) {
                return root.getAsJsonObject();
            }
        } catch (RuntimeException ignored) {
            // Handled by the caller (rebuilt from the loaded shops).
        }
        return null;
    }

    /** Reads a file's text as an item_values.json root, or null when it is not one. */
    static JsonObject valuesRoot(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (root.isJsonObject() && root.getAsJsonObject().has("values")
                    && root.getAsJsonObject().get("values").isJsonObject()) {
                return root.getAsJsonObject();
            }
        } catch (RuntimeException ignored) {
            // Handled by the caller.
        }
        return null;
    }

    /**
     * Replaces the shop whose id is {@code id} in place, or appends {@code shop} when there is none. Returns whether an
     * existing shop was replaced.
     */
    static boolean putShop(JsonObject root, String id, JsonObject shop) {
        JsonArray shops = root.getAsJsonArray("shops");
        for (int index = 0; index < shops.size(); index++) {
            if (id.equals(idOf(shops.get(index)))) {
                shops.set(index, shop);
                return true;
            }
        }
        shops.add(shop);
        return false;
    }

    /** Removes every shop whose id is {@code id}. Returns whether one was removed. */
    static boolean removeShop(JsonObject root, String id) {
        JsonArray shops = root.getAsJsonArray("shops");
        boolean removed = false;
        for (int index = shops.size() - 1; index >= 0; index--) {
            if (id.equals(idOf(shops.get(index)))) {
                shops.remove(index);
                removed = true;
            }
        }
        return removed;
    }

    /** Sets an item's value, or removes it when {@code value} is 0. */
    static void setItemValue(JsonObject root, String itemId, long value) {
        JsonObject values = root.getAsJsonObject("values");
        if (value <= 0L) {
            values.remove(itemId);
        } else {
            values.addProperty(itemId, value);
        }
    }

    private static String idOf(JsonElement shop) {
        if (!shop.isJsonObject() || !shop.getAsJsonObject().has("id") || !shop.getAsJsonObject().get("id").isJsonPrimitive()) {
            return null;
        }
        return shop.getAsJsonObject().get("id").getAsString().toLowerCase(Locale.ROOT);
    }
}
