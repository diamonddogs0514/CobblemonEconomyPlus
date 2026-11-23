package com.diamo.cobblemoneconomyplus.reward;

import com.diamo.cobblemoneconomyplus.config.ConfigException;
import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Settings from {@code config/cobblemon_economy_plus/rewards.json}. Anything missing from the file falls back to
 * the default shown in {@link #DEFAULT_JSON}, so admins only need to write what they change.
 */
public record RewardConfig(
        boolean enabled,
        long dailyCap,
        Formula wildBattle,
        Formula wildBattleAlpha,
        Formula trainerBattle,
        Formula capture,
        Formula captureAlpha,
        boolean pokedexEnabled,
        NavigableMap<Integer, Long> milestones
) {
    /** Upper bound for any single number in the file, so a typo cannot pay absurd amounts. */
    static final long MAX_AMOUNT = 1_000_000_000L;

    static final String DEFAULT_JSON = """
            {
              "enabled": true,
              "daily_cap": 0,
              "wild_battle":       { "enabled": true, "base": 10,  "per_level": 1 },
              "wild_battle_alpha": { "enabled": true, "base": 100, "per_level": 5 },
              "trainer_battle":    { "enabled": true, "base": 100, "per_level": 5 },
              "capture":           { "enabled": true, "base": 5,   "per_level": 1 },
              "capture_alpha":     { "enabled": true, "base": 50,  "per_level": 3 },
              "pokedex_milestones": {
                "enabled": true,
                "milestones": {
                  "10": 500, "25": 1000, "50": 2500, "100": 5000, "150": 7500, "200": 10000,
                  "300": 15000, "400": 20000, "500": 25000, "600": 30000, "700": 35000,
                  "800": 40000, "900": 45000, "1000": 50000
                }
              }
            }
            """;

    /** {@code base + level × perLevel} coins, or nothing when disabled. */
    public record Formula(boolean enabled, long base, long perLevel) {
        public long amount(int level) {
            if (!enabled) {
                return 0L;
            }
            return Math.max(0L, base + Math.max(0, level) * perLevel);
        }
    }

    public RewardConfig {
        milestones = Collections.unmodifiableNavigableMap(new TreeMap<>(milestones));
    }

    public static RewardConfig defaults() {
        return parse(DEFAULT_JSON, new java.util.ArrayList<>());
    }

    /** The formula for a defeated wild Pokémon. A disabled alpha formula falls back to the normal one. */
    public Formula wildFormula(boolean alpha) {
        return alpha && wildBattleAlpha.enabled() ? wildBattleAlpha : wildBattle;
    }

    /** The formula for a captured Pokémon. A disabled alpha formula falls back to the normal one. */
    public Formula captureFormula(boolean alpha) {
        return alpha && captureAlpha.enabled() ? captureAlpha : capture;
    }

    /**
     * Parses rewards.json text. Throws on invalid JSON (the caller keeps the previous settings); values that are
     * wrong in themselves (negative, not a number) are replaced by their default and reported in {@code warnings}.
     */
    static RewardConfig parse(String source, List<ConfigMessage> warnings) {
        JsonElement root = JsonParser.parseString(source);
        if (!root.isJsonObject()) {
            throw new ConfigException("root_not_object", "the root JSON value must be an object");
        }
        JsonObject json = root.getAsJsonObject();
        JsonObject defaults = DEFAULT_JSON.equals(source) ? json : JsonParser.parseString(DEFAULT_JSON).getAsJsonObject();

        JsonObject dex = object(json, "pokedex_milestones", warnings);
        NavigableMap<Integer, Long> milestones = dex != null && dex.has("milestones")
                ? milestones(dex.get("milestones"), warnings)
                : milestones(defaults.getAsJsonObject("pokedex_milestones").get("milestones"), warnings);

        return new RewardConfig(
                bool(json, "enabled", true, "", warnings),
                amount(json, "daily_cap", 0L, "", warnings),
                formula(json, defaults, "wild_battle", warnings),
                formula(json, defaults, "wild_battle_alpha", warnings),
                formula(json, defaults, "trainer_battle", warnings),
                formula(json, defaults, "capture", warnings),
                formula(json, defaults, "capture_alpha", warnings),
                dex == null || bool(dex, "enabled", true, "pokedex_milestones.", warnings),
                milestones
        );
    }

    private static Formula formula(JsonObject json, JsonObject defaults, String key, List<ConfigMessage> warnings) {
        JsonObject fallback = defaults.getAsJsonObject(key);
        long defaultBase = fallback.get("base").getAsLong();
        long defaultPerLevel = fallback.get("per_level").getAsLong();
        JsonObject section = object(json, key, warnings);
        if (section == null) {
            return new Formula(true, defaultBase, defaultPerLevel);
        }
        String prefix = key + ".";
        return new Formula(
                bool(section, "enabled", true, prefix, warnings),
                amount(section, "base", defaultBase, prefix, warnings),
                amount(section, "per_level", defaultPerLevel, prefix, warnings));
    }

    private static NavigableMap<Integer, Long> milestones(JsonElement element, List<ConfigMessage> warnings) {
        TreeMap<Integer, Long> result = new TreeMap<>();
        if (!element.isJsonObject()) {
            warnings.add(ConfigMessage.of("milestones_not_object",
                    "pokedex_milestones.milestones must be an object like { \"10\": 500 }; no milestones are paid"));
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            int count;
            try {
                count = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException exception) {
                warnings.add(ConfigMessage.of("milestone_not_number",
                        "pokedex_milestones: \"%s\" is not a number of species; skipped", entry.getKey()));
                continue;
            }
            Long coins = number(entry.getValue());
            if (count <= 0 || coins == null || coins < 0 || coins > MAX_AMOUNT) {
                warnings.add(ConfigMessage.of("milestone_invalid",
                        "pokedex_milestones: \"%s\" needs a positive species count and 0-%s coins; skipped",
                        entry.getKey(), MAX_AMOUNT));
                continue;
            }
            result.put(count, coins);
        }
        return result;
    }

    private static JsonObject object(JsonObject json, String key, List<ConfigMessage> warnings) {
        if (!json.has(key)) {
            return null;
        }
        if (!json.get(key).isJsonObject()) {
            warnings.add(ConfigMessage.of("section_not_object", "%s must be an object; using the defaults", key));
            return null;
        }
        return json.getAsJsonObject(key);
    }

    private static boolean bool(JsonObject json, String key, boolean fallback, String prefix, List<ConfigMessage> warnings) {
        if (!json.has(key)) {
            return fallback;
        }
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        warnings.add(ConfigMessage.of("not_boolean", "%s must be true or false; using %s", prefix + key, fallback));
        return fallback;
    }

    private static long amount(JsonObject json, String key, long fallback, String prefix, List<ConfigMessage> warnings) {
        if (!json.has(key)) {
            return fallback;
        }
        Long value = number(json.get(key));
        if (value == null || value < 0 || value > MAX_AMOUNT) {
            warnings.add(ConfigMessage.of("bad_reward_amount", "%s must be a whole number from 0 to %s; using %s",
                    prefix + key, MAX_AMOUNT, fallback));
            return fallback;
        }
        return value;
    }

    private static Long number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double raw = value.getAsDouble();
        if (raw != Math.rint(raw) || Double.isInfinite(raw)) {
            return null;
        }
        return value.getAsLong();
    }
}
