package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Loads per-server shop configuration from config/cobblemon_economy_plus/shops.json. */
public final class ShopManager {
    public static final String DEFAULT_SHOP_ID = "general";
    private static final int MAX_ENTRIES_PER_SHOP = 120;
    private static final int MAX_DISPLAY_NAME_LENGTH = 96;
    /** Must not exceed the payload's name limit (EconomyPayloads.MAX_NAME_LENGTH). */
    private static final int MAX_LOCALIZED_JSON_LENGTH = 480;

    private static final String DEFAULT_CONFIG = """
            {
              "shops": [
                {
                  "id": "general",
                  "name": { "en_us": "Poké Mart", "ja_jp": "ポケマート" },
                  "entries": [
                    { "item": "minecraft:apple", "amount": 1, "price": 8 },
                    { "item": "minecraft:bread", "amount": 1, "price": 12 },
                    { "item": "minecraft:torch", "amount": 16, "price": 16 },
                    { "item": "minecraft:iron_ingot", "amount": 1, "price": 50 },
                    { "item": "cobblemon:poke_ball", "amount": 1, "price": 200 },
                    { "item": "cobblemon:great_ball", "amount": 1, "price": 600 },
                    { "item": "cobblemon:ultra_ball", "amount": 1, "price": 1200 },
                    { "item": "cobblemon:potion", "amount": 1, "price": 300 }
                  ]
                },
                {
                  "id": "trainer_boutique",
                  "name": { "en_us": "Trainer Boutique", "ja_jp": "トレーナーブティック" },
                  "theme": "pokeball",
                  "sell_multiplier": 1.2,
                  "entries": [
                    { "item": "cobblemon:poke_ball", "amount": 1, "price": 200 },
                    { "item": "cobblemon:great_ball", "amount": 1, "price": 600 },
                    { "item": "cobblemon:ultra_ball", "amount": 1, "price": 1200 },
                    { "item": "cobblemon:premier_ball", "amount": 1, "price": 250 },
                    { "item": "cobblemon:quick_ball", "amount": 1, "price": 1000 },
                    { "item": "cobblemon:timer_ball", "amount": 1, "price": 1000 },
                    { "item": "cobblemon:luxury_ball", "amount": 1, "price": 1000 },
                    { "item": "cobblemon:potion", "amount": 1, "price": 300 },
                    { "item": "cobblemon:super_potion", "amount": 1, "price": 700 },
                    { "item": "cobblemon:hyper_potion", "amount": 1, "price": 1200 },
                    { "item": "cobblemon:revive", "amount": 1, "price": 1500 },
                    { "item": "cobblemon:full_heal", "amount": 1, "price": 600 },
                    { "item": "cobblemon:antidote", "amount": 1, "price": 100 },
                    { "item": "cobblemon:fire_stone", "amount": 1, "price": 2100 },
                    { "item": "cobblemon:water_stone", "amount": 1, "price": 2100 },
                    { "item": "cobblemon:thunder_stone", "amount": 1, "price": 2100 },
                    { "item": "cobblemon:leaf_stone", "amount": 1, "price": 2100 },
                    { "item": "cobblemon:moon_stone", "amount": 1, "price": 2100 },
                    { "item": "cobblemon:rare_candy", "amount": 1, "price": 4800 },
                    { "item": "cobblemon:oran_berry", "amount": 4, "price": 80 },
                    { "item": "cobblemon:sitrus_berry", "amount": 4, "price": 160 }
                  ]
                }
              ]
            }
            """;

    /**
     * Base sell values. A shop's selling price is the base value times that shop's "sell_multiplier"
     * (default 1.0; 0 turns selling off for the shop). Items not listed here cannot be sold.
     */
    private static final String DEFAULT_ITEM_VALUES = """
            {
              "values": {
                "minecraft:apple": 4,
                "minecraft:bread": 6,
                "minecraft:carrot": 2,
                "minecraft:torch": 1,
                "minecraft:iron_ingot": 25,
                "minecraft:gold_ingot": 40,
                "minecraft:diamond": 250,
                "minecraft:emerald": 175,
                "minecraft:redstone": 5,
                "minecraft:oak_log": 3,
                "cobblemon:poke_ball": 100,
                "cobblemon:great_ball": 300,
                "cobblemon:ultra_ball": 600,
                "cobblemon:premier_ball": 125,
                "cobblemon:quick_ball": 500,
                "cobblemon:timer_ball": 500,
                "cobblemon:luxury_ball": 500,
                "cobblemon:potion": 150,
                "cobblemon:super_potion": 350,
                "cobblemon:hyper_potion": 600,
                "cobblemon:revive": 750,
                "cobblemon:full_heal": 300,
                "cobblemon:antidote": 50,
                "cobblemon:fire_stone": 1000,
                "cobblemon:water_stone": 1000,
                "cobblemon:thunder_stone": 1000,
                "cobblemon:leaf_stone": 1000,
                "cobblemon:moon_stone": 1000,
                "cobblemon:rare_candy": 2400,
                "cobblemon:oran_berry": 10,
                "cobblemon:sitrus_berry": 20
              }
            }
            """;

    private static final double MAX_SELL_MULTIPLIER = 100.0D;
    /** Upper bound on distinct sellable items sent to a client (see EconomyPayloads.MAX_ENTRIES). */
    private static final int MAX_SELL_OFFERS = 512;

    private static MinecraftServer loadedFor;
    private static Map<String, ShopDefinition> shops = Map.of();
    private static Map<ResourceLocation, Long> itemValues = Map.of();

    /** One item a shop will buy from players, at {@code unitPrice} per item. */
    public record SellOffer(ResourceLocation itemId, long unitPrice) {
    }

    private ShopManager() {
    }

    public static Optional<ShopDefinition> get(MinecraftServer server, String id) {
        ensureLoaded(server);
        return Optional.ofNullable(shops.get(id.toLowerCase(Locale.ROOT)));
    }

    public static Collection<ShopDefinition> all(MinecraftServer server) {
        ensureLoaded(server);
        return shops.values();
    }

    /** Returns the next configured shop in file order, wrapping at either end. */
    public static Optional<ShopDefinition> cycle(MinecraftServer server, String currentShopId, int direction) {
        ensureLoaded(server);
        List<ShopDefinition> availableShops = List.copyOf(shops.values());
        if (availableShops.isEmpty()) {
            return Optional.empty();
        }

        int currentIndex = -1;
        if (currentShopId != null) {
            for (int index = 0; index < availableShops.size(); index++) {
                if (availableShops.get(index).id().equalsIgnoreCase(currentShopId)) {
                    currentIndex = index;
                    break;
                }
            }
        }

        if (currentIndex < 0) {
            return Optional.of(availableShops.get(direction < 0 ? availableShops.size() - 1 : 0));
        }
        return Optional.of(availableShops.get(Math.floorMod(currentIndex + direction, availableShops.size())));
    }

    public static synchronized int reload(MinecraftServer server) {
        Path configPath = configPath(server);
        try {
            writeDefaultConfigIfMissing(configPath);
            Map<String, ShopDefinition> parsed = parse(Files.readString(configPath, StandardCharsets.UTF_8));
            if (parsed.isEmpty()) {
                throw new IllegalArgumentException("No valid shops were found");
            }

            shops = Collections.unmodifiableMap(parsed);
            loadedFor = server;
            CobblemonEconomyPlus.LOGGER.info("Loaded {} Cobblemon Economy Plus shop(s) from {}", shops.size(), configPath);
        } catch (IOException | IllegalArgumentException exception) {
            shops = Collections.unmodifiableMap(defaultShops());
            loadedFor = server;
            CobblemonEconomyPlus.LOGGER.error(
                    "Could not load shop configuration at {}. Using the built-in fallback shop instead.",
                    configPath,
                    exception
            );
        }
        loadItemValues(server);
        warnAboutArbitrage();
        return shops.size();
    }

    // ------------------------------------------------------------------ selling

    /** Price a shop pays per item, or 0 when the shop does not buy it (no value, or selling disabled). */
    public static long sellUnitPrice(ShopDefinition shop, ResourceLocation itemId) {
        Long base = itemValues.get(itemId);
        if (base == null || shop.sellMultiplier() <= 0.0D) {
            return 0L;
        }
        return scaledPrice(base, shop.sellMultiplier());
    }

    /** {@code base × multiplier}, rounded down, capped so that {@code × 4096 items} still fits a long. */
    static long scaledPrice(long base, double multiplier) {
        if (multiplier <= 0.0D || base <= 0L) {
            return 0L;
        }
        double scaled = Math.floor(base * multiplier);
        return scaled >= (double) Long.MAX_VALUE / 4096.0D ? Long.MAX_VALUE / 4096L : (long) scaled;
    }

    /** Everything the shop buys (price at least 1), in item_values.json order. */
    public static List<SellOffer> sellOffers(ShopDefinition shop) {
        List<SellOffer> offers = new ArrayList<>();
        for (ResourceLocation itemId : itemValues.keySet()) {
            long price = sellUnitPrice(shop, itemId);
            if (price >= 1L) {
                offers.add(new SellOffer(itemId, price));
                if (offers.size() >= MAX_SELL_OFFERS) {
                    break;
                }
            }
        }
        return offers;
    }

    public static Path itemValuesPath(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve(CobblemonEconomyPlus.MOD_ID)
                .resolve("item_values.json");
    }

    private static void loadItemValues(MinecraftServer server) {
        Path path = itemValuesPath(server);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_ITEM_VALUES, StandardCharsets.UTF_8);
                CobblemonEconomyPlus.LOGGER.info("Created default item values at {}", path);
            }
            itemValues = Collections.unmodifiableMap(parseItemValues(Files.readString(path, StandardCharsets.UTF_8)));
            CobblemonEconomyPlus.LOGGER.info("Loaded {} sellable item value(s) from {}", itemValues.size(), path);
        } catch (IOException | RuntimeException exception) {
            itemValues = Collections.unmodifiableMap(parseItemValues(DEFAULT_ITEM_VALUES));
            CobblemonEconomyPlus.LOGGER.error("Could not load item values at {}. Using the built-in defaults instead.",
                    path, exception);
        }
    }

    static Map<ResourceLocation, Long> parseItemValues(String source) {
        JsonElement root = JsonParser.parseString(source);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("values")
                || !root.getAsJsonObject().get("values").isJsonObject()) {
            throw new IllegalArgumentException("item_values.json needs a \"values\" object");
        }

        Map<ResourceLocation, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().getAsJsonObject("values").entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                CobblemonEconomyPlus.LOGGER.warn("Skipping item value for unknown item '{}'", entry.getKey());
                continue;
            }
            try {
                long value = entry.getValue().getAsLong();
                if (value < 1L) {
                    throw new NumberFormatException("must be at least 1");
                }
                result.put(itemId, value);
            } catch (RuntimeException exception) {
                CobblemonEconomyPlus.LOGGER.warn("Skipping invalid item value for '{}': {}", entry.getKey(),
                        exception.getMessage());
            }
        }
        return result;
    }

    /** Warns about shops where selling an item pays more than buying it costs (a buy/sell money loop). */
    private static void warnAboutArbitrage() {
        for (ShopDefinition shop : shops.values()) {
            for (ShopDefinition.Entry entry : shop.entries()) {
                long sellPerItem = sellUnitPrice(shop, entry.itemId());
                double buyPerItem = entry.price() / (double) entry.amount();
                if (sellPerItem > 0L && sellPerItem > buyPerItem) {
                    CobblemonEconomyPlus.LOGGER.warn(
                            "Shop '{}': {} sells for {} each but costs only {} each to buy - players can profit by buying and selling back",
                            shop.id(), entry.itemId(), sellPerItem, buyPerItem);
                }
            }
        }
    }

    public static Path configPath(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve(CobblemonEconomyPlus.MOD_ID)
                .resolve("shops.json");
    }

    private static void ensureLoaded(MinecraftServer server) {
        if (loadedFor != server) {
            reload(server);
        }
    }

    private static void writeDefaultConfigIfMissing(Path configPath) throws IOException {
        if (Files.exists(configPath)) {
            return;
        }

        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, DEFAULT_CONFIG, StandardCharsets.UTF_8);
        CobblemonEconomyPlus.LOGGER.info("Created default shop configuration at {}", configPath);
    }

    static Map<String, ShopDefinition> parse(String source) {
        JsonElement rootElement = JsonParser.parseString(source);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("Root JSON value must be an object");
        }

        JsonArray shopArray = rootElement.getAsJsonObject().getAsJsonArray("shops");
        if (shopArray == null) {
            throw new IllegalArgumentException("Missing shops array");
        }

        Map<String, ShopDefinition> result = new LinkedHashMap<>();
        for (JsonElement shopElement : shopArray) {
            if (!shopElement.isJsonObject()) {
                CobblemonEconomyPlus.LOGGER.warn("Skipping a shop entry that is not an object");
                continue;
            }

            JsonObject object = shopElement.getAsJsonObject();
            String id = string(object, "id").toLowerCase(Locale.ROOT);
            if (!Shopkeeper.isValidShopId(id)) {
                CobblemonEconomyPlus.LOGGER.warn("Skipping shop with invalid id '{}'", id);
                continue;
            }

            String name = localizedText(id, object, "name", id);
            JsonArray entryArray = object.getAsJsonArray("entries");
            if (entryArray == null) {
                CobblemonEconomyPlus.LOGGER.warn("Skipping shop '{}' because it has no entries array", id);
                continue;
            }

            List<ShopDefinition.Entry> entries = new ArrayList<>();
            for (JsonElement entryElement : entryArray) {
                if (entries.size() >= MAX_ENTRIES_PER_SHOP) {
                    CobblemonEconomyPlus.LOGGER.warn("Shop '{}' has more than {} entries; extra entries were ignored", id,
                            MAX_ENTRIES_PER_SHOP);
                    break;
                }
                if (!entryElement.isJsonObject()) {
                    continue;
                }

                parseEntry(id, entryElement.getAsJsonObject()).ifPresent(entries::add);
            }

            String theme = object.has("theme") && object.get("theme").isJsonPrimitive()
                    ? object.get("theme").getAsString().toLowerCase(Locale.ROOT)
                    : ShopThemes.DEFAULT;
            if (!ShopThemes.isValid(theme)) {
                CobblemonEconomyPlus.LOGGER.warn("Shop '{}' has unknown theme '{}'; using {}", id, theme, ShopThemes.DEFAULT);
                theme = ShopThemes.DEFAULT;
            }

            String subtitle = localizedText(id, object, "subtitle", "");

            double sellMultiplier = 1.0D;
            if (object.has("sell_multiplier") && object.get("sell_multiplier").isJsonPrimitive()) {
                try {
                    sellMultiplier = object.get("sell_multiplier").getAsDouble();
                } catch (NumberFormatException exception) {
                    sellMultiplier = Double.NaN;
                }
                if (Double.isNaN(sellMultiplier) || sellMultiplier < 0.0D || sellMultiplier > MAX_SELL_MULTIPLIER) {
                    CobblemonEconomyPlus.LOGGER.warn("Shop '{}' has an invalid sell_multiplier; it must be 0 to {}. Using 1.0",
                            id, MAX_SELL_MULTIPLIER);
                    sellMultiplier = 1.0D;
                }
            }

            result.put(id, new ShopDefinition(id, name, theme, subtitle, sellMultiplier, entries));
        }
        return result;
    }

    /**
     * Reads a text field that is either a plain string or a per-language object
     * ({@code {"en_us": "...", "ja_jp": "..."}}). Objects are kept as compact JSON and resolved
     * per viewer by {@link LocalizedText}.
     */
    private static String localizedText(String shopId, JsonObject object, String key, String fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) {
            return truncate(shopId, key, element.getAsString());
        }
        if (element.isJsonObject()) {
            JsonObject compact = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    compact.addProperty(entry.getKey().toLowerCase(Locale.ROOT),
                            truncate(shopId, key, entry.getValue().getAsString()));
                }
            }
            String json = compact.toString();
            if (json.length() <= MAX_LOCALIZED_JSON_LENGTH && !compact.isEmpty()) {
                return json;
            }
            CobblemonEconomyPlus.LOGGER.warn("Shop '{}' field '{}' is empty or too long; using one language only", shopId, key);
            String single = LocalizedText.resolve(json, "en_us");
            return truncate(shopId, key, single.startsWith("{") ? fallback : single);
        }
        return fallback;
    }

    private static String truncate(String shopId, String key, String value) {
        if (value.length() <= MAX_DISPLAY_NAME_LENGTH) {
            return value;
        }
        CobblemonEconomyPlus.LOGGER.warn("Truncating '{}' for shop '{}' to {} characters", key, shopId, MAX_DISPLAY_NAME_LENGTH);
        return value.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }

    private static Optional<ShopDefinition.Entry> parseEntry(String shopId, JsonObject object) {
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(string(object, "item"));
            int amount = integer(object, "amount");
            long price = longValue(object, "price");
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                throw new IllegalArgumentException("unknown item '" + string(object, "item") + "'");
            }
            if (amount < 1 || amount > 64) {
                throw new IllegalArgumentException("amount must be between 1 and 64");
            }
            if (price < 0) {
                throw new IllegalArgumentException("price must be zero or positive");
            }
            return Optional.of(new ShopDefinition.Entry(itemId, amount, price));
        } catch (IllegalArgumentException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Skipping invalid entry in shop '{}': {}", shopId, exception.getMessage());
            return Optional.empty();
        }
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        return object.get(key).getAsInt();
    }

    private static long longValue(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        return object.get(key).getAsLong();
    }

    private static Map<String, ShopDefinition> defaultShops() {
        return parse(DEFAULT_CONFIG);
    }
}
