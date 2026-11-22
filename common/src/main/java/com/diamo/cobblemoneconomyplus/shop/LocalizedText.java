package com.diamo.cobblemoneconomyplus.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Map;

/**
 * Shop names and subtitles in shops.json may be a plain string or a per-language object such as
 * {@code {"en_us": "Poke Mart", "ja_jp": "ポケマート"}}. The raw value travels as a single string
 * (plain text, or the compact JSON object) and is resolved against the viewer's language on use.
 */
public final class LocalizedText {
    private LocalizedText() {
    }

    /**
     * Picks the best text for {@code languageCode} (for example {@code ja_jp}): exact match, then the same
     * language family, then {@code en_us}, then the first listed value. Plain strings are returned as is.
     */
    public static String resolve(String raw, String languageCode) {
        if (raw == null) {
            return "";
        }
        if (!raw.startsWith("{")) {
            return raw;
        }
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            String lang = languageCode == null ? "" : languageCode.toLowerCase(Locale.ROOT);

            String exact = find(object, lang);
            if (exact != null) {
                return exact;
            }
            int underscore = lang.indexOf('_');
            if (underscore > 0) {
                String family = lang.substring(0, underscore + 1);
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getKey().toLowerCase(Locale.ROOT).startsWith(family) && isText(entry.getValue())) {
                        return entry.getValue().getAsString();
                    }
                }
            }
            String english = find(object, "en_us");
            if (english != null) {
                return english;
            }
            for (JsonElement value : object.asMap().values()) {
                if (isText(value)) {
                    return value.getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through: show the raw text rather than nothing.
        }
        return raw;
    }

    /**
     * Like {@link #resolve}, but a per-language object without the viewer's language (or its family) gives an empty
     * string instead of another language, so the caller can use its own default. Used for subtitles, whose default
     * is the theme's tagline in the viewer's language. Plain strings are still shown to everyone.
     */
    public static String resolveOwnLanguage(String raw, String languageCode) {
        if (raw == null || !raw.startsWith("{")) {
            return raw == null ? "" : raw;
        }
        try {
            JsonObject object = JsonParser.parseString(raw).getAsJsonObject();
            String lang = languageCode == null ? "" : languageCode.toLowerCase(Locale.ROOT);
            String exact = find(object, lang);
            if (exact != null) {
                return exact;
            }
            int underscore = lang.indexOf('_');
            if (underscore > 0) {
                String family = lang.substring(0, underscore + 1);
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    if (entry.getKey().toLowerCase(Locale.ROOT).startsWith(family) && isText(entry.getValue())) {
                        return entry.getValue().getAsString();
                    }
                }
            }
            return "";
        } catch (RuntimeException ignored) {
            return raw;
        }
    }

    private static String find(JsonObject object, String key) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && isText(entry.getValue())) {
                return entry.getValue().getAsString();
            }
        }
        return null;
    }

    private static boolean isText(JsonElement element) {
        return element.isJsonPrimitive();
    }
}
