package com.diamo.cobblemoneconomyplus.admin;

import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopThemes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A shop being edited in the admin screen. Numbers are kept as the text the admin typed, so a half-typed value never
 * gets lost; {@link #toJson()} checks them and builds the shops.json object the server validates again.
 */
public final class ShopDraft {
    public static final String EN = "en_us";
    public static final String JA = "ja_jp";

    public String id;
    /** True for a shop that is not in shops.json yet (its id can still be edited). */
    public final boolean isNew;
    /** Per-language names; other languages from the file are kept. */
    public final Map<String, String> name = new LinkedHashMap<>();
    public final Map<String, String> subtitle = new LinkedHashMap<>();
    public String theme;
    public String sellMultiplier;
    public final List<EntryDraft> entries = new ArrayList<>();

    /** One entry; blank limit / stock fields mean "none". */
    public static final class EntryDraft {
        public String itemId;
        public String amount;
        public String price;
        public String limitCount = "";
        public ShopDefinition.Period period = ShopDefinition.Period.NEVER;
        public String stockMax = "";
        public String restockMinutes = "";

        public EntryDraft(String itemId, String amount, String price) {
            this.itemId = itemId;
            this.amount = amount;
            this.price = price;
        }

        public EntryDraft copy() {
            EntryDraft copy = new EntryDraft(itemId, amount, price);
            copy.limitCount = limitCount;
            copy.period = period;
            copy.stockMax = stockMax;
            copy.restockMinutes = restockMinutes;
            return copy;
        }
    }

    /** Why a draft cannot be saved: a translation key and the field / entry it is about. */
    public record Problem(String key, String field, int entry) {
    }

    /** The JSON to send, or the first problem found. */
    public record Result(JsonObject json, Problem problem) {
        public boolean ok() {
            return problem == null;
        }
    }

    private ShopDraft(String id, boolean isNew) {
        this.id = id;
        this.isNew = isNew;
    }

    public static ShopDraft blank(String id) {
        ShopDraft draft = new ShopDraft(id, true);
        draft.theme = ShopThemes.DEFAULT;
        draft.sellMultiplier = "1.0";
        return draft;
    }

    public static ShopDraft of(ShopDefinition shop) {
        ShopDraft draft = new ShopDraft(shop.id(), false);
        readLocalized(shop.displayName(), draft.name);
        readLocalized(shop.subtitle(), draft.subtitle);
        draft.theme = shop.theme();
        draft.sellMultiplier = number(shop.sellMultiplier());
        for (ShopDefinition.Entry entry : shop.entries()) {
            EntryDraft entryDraft = new EntryDraft(entry.itemId().toString(), Integer.toString(entry.amount()),
                    Long.toString(entry.price()));
            if (entry.limit() != null) {
                entryDraft.limitCount = Integer.toString(entry.limit().perPlayer());
                entryDraft.period = entry.limit().period();
            }
            if (entry.stock() != null) {
                entryDraft.stockMax = Integer.toString(entry.stock().max());
                entryDraft.restockMinutes = Integer.toString(entry.stock().restockMinutes());
            }
            draft.entries.add(entryDraft);
        }
        return draft;
    }

    /** The text shown for a localized field in one language (empty when not set). */
    public String text(Map<String, String> field, String language) {
        return field.getOrDefault(language, "");
    }

    public static void setText(Map<String, String> field, String language, String value) {
        if (value == null || value.isBlank()) {
            field.remove(language);
        } else {
            field.put(language, value);
        }
    }

    public Result toJson() {
        String shopId = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!shopId.matches("[a-z0-9_-]{1,32}")) {
            return problem("bad_id", "id", -1);
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", shopId);
        JsonElement nameJson = writeLocalized(name);
        json.add("name", nameJson == null ? new JsonPrimitive(shopId) : nameJson);
        JsonElement subtitleJson = writeLocalized(subtitle);
        if (subtitleJson != null) {
            json.add("subtitle", subtitleJson);
        }
        json.addProperty("theme", ShopThemes.isValid(theme) ? theme : ShopThemes.DEFAULT);

        Double multiplier = parseDouble(sellMultiplier);
        if (multiplier == null || multiplier < 0.0D || multiplier > 100.0D) {
            return problem("bad_multiplier", "sell_multiplier", -1);
        }
        json.addProperty("sell_multiplier", multiplier);

        JsonArray entryArray = new JsonArray();
        for (int index = 0; index < entries.size(); index++) {
            EntryDraft entry = entries.get(index);
            JsonObject entryJson = new JsonObject();
            entryJson.addProperty("item", entry.itemId);
            Long amount = parseLong(entry.amount);
            if (amount == null || amount < 1 || amount > 64) {
                return problem("bad_amount", "amount", index);
            }
            entryJson.addProperty("amount", amount);
            Long price = parseLong(entry.price);
            if (price == null || price < 0) {
                return problem("bad_price", "price", index);
            }
            entryJson.addProperty("price", price);
            if (!entry.limitCount.isBlank()) {
                Long count = parseLong(entry.limitCount);
                if (count == null || count < 1 || count > 1_000_000) {
                    return problem("bad_limit", "limit", index);
                }
                JsonObject limit = new JsonObject();
                limit.addProperty("per_player", count);
                limit.addProperty("period", entry.period.id());
                entryJson.add("limit", limit);
            }
            if (!entry.stockMax.isBlank()) {
                Long max = parseLong(entry.stockMax);
                if (max == null || max < 1 || max > 1_000_000) {
                    return problem("bad_stock", "stock", index);
                }
                Long restock = entry.restockMinutes.isBlank() ? Long.valueOf(0L) : parseLong(entry.restockMinutes);
                if (restock == null || restock < 0 || restock > 525_600) {
                    return problem("bad_restock", "restock_minutes", index);
                }
                JsonObject stock = new JsonObject();
                stock.addProperty("max", max);
                stock.addProperty("restock_minutes", restock);
                entryJson.add("stock", stock);
            }
            entryArray.add(entryJson);
        }
        json.add("entries", entryArray);
        return new Result(json, null);
    }

    private static Result problem(String key, String field, int entry) {
        return new Result(null, new Problem("message.cobblemon_economy_plus.admin." + key, field, entry));
    }

    private static void readLocalized(String raw, Map<String, String> into) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        if (raw.startsWith("{")) {
            try {
                JsonElement parsed = JsonParser.parseString(raw);
                if (parsed.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            into.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                    return;
                }
            } catch (RuntimeException ignored) {
                // Plain text that starts with a brace.
            }
        }
        // Plain text is shown to everyone; put it in the column of the language it is written in.
        into.put(looksJapanese(raw) ? JA : EN, raw);
    }

    static boolean looksJapanese(String text) {
        return text.codePoints().anyMatch(codePoint -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN;
        });
    }

    /** A single language is written as plain text (shown to everyone), several as a per-language object. */
    private static JsonElement writeLocalized(Map<String, String> field) {
        Map<String, String> filled = new LinkedHashMap<>();
        field.forEach((language, text) -> {
            if (text != null && !text.isBlank()) {
                filled.put(language, text.trim());
            }
        });
        if (filled.isEmpty()) {
            return null;
        }
        if (filled.size() == 1) {
            return new JsonPrimitive(filled.values().iterator().next());
        }
        JsonObject object = new JsonObject();
        filled.forEach(object::addProperty);
        return object;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.format(Locale.ROOT, "%.1f", value) : Double.toString(value);
    }

    private static Long parseLong(String text) {
        try {
            return Long.parseLong(text.trim().replace(",", ""));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Double parseDouble(String text) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
