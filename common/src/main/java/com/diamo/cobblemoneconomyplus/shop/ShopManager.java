package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.config.ConfigException;
import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads per-server shop configuration from config/cobblemon_economy_plus/shops.json. */
public final class ShopManager {
    public static final String DEFAULT_SHOP_ID = "general";
    private static final int MAX_ENTRIES_PER_SHOP = 120;
    private static final int MAX_DISPLAY_NAME_LENGTH = 96;
    /** Must not exceed the payload's name limit (EconomyPayloads.MAX_NAME_LENGTH). */
    private static final int MAX_LOCALIZED_JSON_LENGTH = 480;

    static final String DEFAULT_CONFIG = """
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
    /** Goes up on every reload, so the admin screen can tell that the shops changed since it was opened. */
    private static int revision;

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

    /**
     * Outcome of a reload or validation. {@code errors} are files that could not be used at all (bad JSON, no
     * valid shops); {@code warnings} are entries that were skipped or adjusted. The counts describe the
     * shops and item values that are (reload) or would be (validate) in effect.
     */
    public record Report(int shopCount, int itemValueCount, List<ConfigMessage> errors, List<ConfigMessage> warnings) {
        public Report {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** A parsed config file, or why it could not be used ({@code error} is null on success). Never thrown. */
    record Parsed<T>(T value, ConfigMessage error, List<ConfigMessage> warnings) {
        boolean ok() {
            return error == null;
        }
    }

    /**
     * Reloads shops.json and item_values.json. A file that cannot be used (for example a missing comma) leaves
     * the previously loaded values in place, so a typo never wipes out the live shops. Only when nothing was
     * loaded yet (server start) do the built-in defaults stand in.
     */
    public static synchronized Report reload(MinecraftServer server) {
        boolean firstLoad = loadedFor != server;
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();

        Parsed<Map<String, ShopDefinition>> parsedShops = readShops(configPath(server), true);
        warnings.addAll(prefixed("shops.json", parsedShops.warnings()));
        if (!parsedShops.ok()) {
            errors.add(parsedShops.error().inFile("shops.json"));
        }
        shops = Collections.unmodifiableMap(choose(parsedShops, shops, firstLoad, ShopManager::defaultShops));

        Parsed<Map<ResourceLocation, Long>> parsedValues = readItemValues(itemValuesPath(server), true);
        warnings.addAll(prefixed("item_values.json", parsedValues.warnings()));
        if (!parsedValues.ok()) {
            errors.add(parsedValues.error().inFile("item_values.json"));
        }
        itemValues = Collections.unmodifiableMap(choose(parsedValues, itemValues, firstLoad,
                () -> parseItemValues(DEFAULT_ITEM_VALUES)));
        revision++;
        loadedFor = server;
        warnings.addAll(arbitrageWarnings(shops, itemValues));

        Report report = new Report(shops.size(), itemValues.size(), errors, warnings);
        log(report, firstLoad);
        return report;
    }

    /** Checks shops.json and item_values.json without applying them (a dry run of {@link #reload}). */
    public static synchronized Report validate(MinecraftServer server) {
        ensureLoaded(server);
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();

        Parsed<Map<String, ShopDefinition>> parsedShops = readShops(configPath(server), false);
        warnings.addAll(prefixed("shops.json", parsedShops.warnings()));
        if (!parsedShops.ok()) {
            errors.add(parsedShops.error().inFile("shops.json"));
        }
        Parsed<Map<ResourceLocation, Long>> parsedValues = readItemValues(itemValuesPath(server), false);
        warnings.addAll(prefixed("item_values.json", parsedValues.warnings()));
        if (!parsedValues.ok()) {
            errors.add(parsedValues.error().inFile("item_values.json"));
        }

        Map<String, ShopDefinition> checkedShops = parsedShops.ok() ? parsedShops.value() : shops;
        Map<ResourceLocation, Long> checkedValues = parsedValues.ok() ? parsedValues.value() : itemValues;
        warnings.addAll(arbitrageWarnings(checkedShops, checkedValues));
        return new Report(checkedShops.size(), checkedValues.size(), errors, warnings);
    }

    /**
     * Which values to use after reading a file: the new ones when they parsed, otherwise the previous ones,
     * or the defaults when there is nothing previous (first load).
     */
    static <T> T choose(Parsed<T> parsed, T previous, boolean firstLoad, Supplier<T> defaults) {
        if (parsed.ok()) {
            return parsed.value();
        }
        return firstLoad ? defaults.get() : previous;
    }

    private static void log(Report report, boolean firstLoad) {
        for (ConfigMessage warning : report.warnings()) {
            CobblemonEconomyPlus.LOGGER.warn(warning.text());
        }
        for (ConfigMessage error : report.errors()) {
            CobblemonEconomyPlus.LOGGER.error("Could not load {}. {}", error.text(), firstLoad
                    ? "Using the built-in defaults for this file until it is fixed and reloaded."
                    : "Keeping the previously loaded values for this file.");
        }
        CobblemonEconomyPlus.LOGGER.info("Cobblemon Economy Plus: {} shop(s), {} sellable item value(s) in effect",
                report.shopCount(), report.itemValueCount());
    }

    private static List<ConfigMessage> prefixed(String file, List<ConfigMessage> messages) {
        return messages.stream().map(message -> message.inFile(file)).toList();
    }

    /** "file not found (path); it is created with defaults on the next reload". */
    public static ConfigMessage fileNotFound(Path path) {
        return ConfigMessage.of("file_not_found", "file not found (%s); it is created with defaults on the next reload", path);
    }

    /** "could not read path: reason". */
    public static ConfigMessage cannotRead(Path path, Exception exception) {
        return ConfigMessage.of("cannot_read", "could not read %s: %s", path, String.valueOf(exception.getMessage()));
    }

    private static Parsed<Map<String, ShopDefinition>> readShops(Path path, boolean createIfMissing) {
        try {
            if (createIfMissing) {
                writeDefaultConfigIfMissing(path);
            } else if (!Files.exists(path)) {
                return new Parsed<>(null, fileNotFound(path), List.of());
            }
            return parseShops(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new Parsed<>(null, cannotRead(path, exception), List.of());
        }
    }

    private static Parsed<Map<ResourceLocation, Long>> readItemValues(Path path, boolean createIfMissing) {
        try {
            if (!Files.exists(path)) {
                if (!createIfMissing) {
                    return new Parsed<>(null, fileNotFound(path), List.of());
                }
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_ITEM_VALUES, StandardCharsets.UTF_8);
                CobblemonEconomyPlus.LOGGER.info("Created default item values at {}", path);
            }
            return parseItemValuesSafely(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new Parsed<>(null, cannotRead(path, exception), List.of());
        }
    }

    /** Parses shops.json text. Never throws: syntax errors and "no valid shops" come back as the error. */
    static Parsed<Map<String, ShopDefinition>> parseShops(String source) {
        List<ConfigMessage> warnings = new ArrayList<>();
        try {
            Map<String, ShopDefinition> parsed = parse(source, warnings);
            if (parsed.isEmpty()) {
                return new Parsed<>(null, ConfigMessage.of("no_shops", "no valid shops were found"), warnings);
            }
            return new Parsed<>(parsed, null, warnings);
        } catch (RuntimeException exception) {
            return new Parsed<>(null, describe(exception), warnings);
        }
    }

    /** Parses item_values.json text. Never throws. */
    static Parsed<Map<ResourceLocation, Long>> parseItemValuesSafely(String source) {
        List<ConfigMessage> warnings = new ArrayList<>();
        try {
            return new Parsed<>(parseItemValues(source, warnings), null, warnings);
        } catch (RuntimeException exception) {
            return new Parsed<>(null, describe(exception), warnings);
        }
    }

    private static final Pattern JSON_LOCATION = Pattern.compile("line (\\d+) column (\\d+)");

    /** A readable reason, keeping Gson's "at line X column Y" location for syntax errors. */
    public static ConfigMessage describe(RuntimeException exception) {
        if (exception instanceof ConfigException config) {
            return config.problem();
        }
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        if (exception instanceof JsonParseException || cause instanceof IOException) {
            Matcher location = JSON_LOCATION.matcher(message);
            if (location.find()) {
                return ConfigMessage.of("invalid_json_at", "invalid JSON at line %s, column %s (%s)",
                        location.group(1), location.group(2), message);
            }
            return ConfigMessage.of("invalid_json", "invalid JSON (%s)", message);
        }
        return ConfigMessage.raw(message);
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

    static Map<ResourceLocation, Long> parseItemValues(String source) {
        return parseItemValues(source, new ArrayList<>());
    }

    static Map<ResourceLocation, Long> parseItemValues(String source, List<ConfigMessage> warnings) {
        JsonElement root = JsonParser.parseString(source);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("values")
                || !root.getAsJsonObject().get("values").isJsonObject()) {
            throw new ConfigException("values_missing", "item_values.json needs a \"values\" object");
        }

        Map<ResourceLocation, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().getAsJsonObject("values").entrySet()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.getKey());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                warnings.add(ConfigMessage.of("value_unknown_item", "skipped the value for unknown item '%s'", entry.getKey()));
                continue;
            }
            long value;
            try {
                value = entry.getValue().getAsLong();
            } catch (RuntimeException exception) {
                value = 0L;
            }
            if (value < 1L) {
                warnings.add(ConfigMessage.of("value_invalid",
                        "skipped the value for '%s': it must be a whole number of at least 1", entry.getKey()));
                continue;
            }
            result.put(itemId, value);
        }
        return result;
    }

    /** Shops where selling an item pays more than buying it costs (a buy/sell money loop). */
    static List<ConfigMessage> arbitrageWarnings(Map<String, ShopDefinition> shops, Map<ResourceLocation, Long> values) {
        List<ConfigMessage> warnings = new ArrayList<>();
        for (ShopDefinition shop : shops.values()) {
            for (ShopDefinition.Entry entry : shop.entries()) {
                Long base = values.get(entry.itemId());
                long sellPerItem = base == null ? 0L : scaledPrice(base, shop.sellMultiplier());
                double buyPerItem = entry.price() / (double) entry.amount();
                if (sellPerItem > 0L && sellPerItem > buyPerItem) {
                    warnings.add(ConfigMessage.of("arbitrage",
                            "shop '%s': %s sells for %s each but costs only %s each to buy - players can profit by buying and selling back",
                            shop.id(), entry.itemId(), sellPerItem, buyPerItem));
                }
            }
        }
        return warnings;
    }

    /** Changes whenever the shops or item values are reloaded (see {@link #reload}). */
    public static synchronized int revision(MinecraftServer server) {
        ensureLoaded(server);
        return revision;
    }

    /** The base values of sellable items currently in effect. */
    public static Map<ResourceLocation, Long> itemValues(MinecraftServer server) {
        ensureLoaded(server);
        return itemValues;
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
        return parse(source, new ArrayList<>());
    }

    /**
     * Parses shops.json text. Throws only when the file as a whole is unusable (bad JSON, no {@code shops}
     * array); a broken shop or entry is skipped and reported in {@code warnings}.
     */
    static Map<String, ShopDefinition> parse(String source, List<ConfigMessage> warnings) {
        JsonElement rootElement = JsonParser.parseString(source);
        if (!rootElement.isJsonObject()) {
            throw new ConfigException("root_not_object", "the root JSON value must be an object");
        }

        JsonElement shopsElement = rootElement.getAsJsonObject().get("shops");
        if (shopsElement == null || !shopsElement.isJsonArray()) {
            throw new ConfigException("shops_missing", "missing the \"shops\" array");
        }

        Map<String, ShopDefinition> result = new LinkedHashMap<>();
        int index = 0;
        for (JsonElement shopElement : shopsElement.getAsJsonArray()) {
            index++;
            if (!shopElement.isJsonObject()) {
                warnings.add(ConfigMessage.of("shop_skipped", "skipped shop #%s: %s", index,
                        ConfigMessage.of("not_object", "it is not an object")));
                continue;
            }
            try {
                ShopDefinition shop = parseShop(shopElement.getAsJsonObject(), warnings);
                if (result.containsKey(shop.id())) {
                    warnings.add(ConfigMessage.of("shop_skipped", "skipped shop #%s: %s", index,
                            ConfigMessage.of("id_taken", "the id '%s' is already used", shop.id())));
                    continue;
                }
                result.put(shop.id(), shop);
            } catch (RuntimeException exception) {
                warnings.add(ConfigMessage.of("shop_skipped", "skipped shop #%s: %s", index, describe(exception)));
            }
        }
        return result;
    }

    private static ShopDefinition parseShop(JsonObject object, List<ConfigMessage> warnings) {
        String id = string(object, "id").toLowerCase(Locale.ROOT);
        if (!Shopkeeper.isValidShopId(id)) {
            throw new ConfigException("bad_id", "invalid id '%s' (use a-z, 0-9, _ and -, up to 32 characters)", id);
        }

        String name = localizedText(id, object, "name", id, warnings);
        JsonElement entriesElement = object.get("entries");
        if (entriesElement == null || !entriesElement.isJsonArray()) {
            throw new ConfigException("entries_missing", "shop '%s' has no \"entries\" array", id);
        }

        List<ShopDefinition.Entry> entries = new ArrayList<>();
        int entryIndex = 0;
        for (JsonElement entryElement : entriesElement.getAsJsonArray()) {
            entryIndex++;
            if (entries.size() >= MAX_ENTRIES_PER_SHOP) {
                warnings.add(ConfigMessage.of("too_many_entries", "shop '%s' has more than %s entries; the rest were ignored",
                        id, MAX_ENTRIES_PER_SHOP));
                break;
            }
            if (!entryElement.isJsonObject()) {
                warnings.add(ConfigMessage.of("entry_skipped", "shop '%s': skipped entry #%s: %s", id, entryIndex,
                        ConfigMessage.of("not_object", "it is not an object")));
                continue;
            }
            parseEntry(id, entryIndex, entryElement.getAsJsonObject(), warnings).ifPresent(entries::add);
        }

        String theme = object.has("theme") && object.get("theme").isJsonPrimitive()
                ? object.get("theme").getAsString().toLowerCase(Locale.ROOT)
                : ShopThemes.DEFAULT;
        if (!ShopThemes.isValid(theme)) {
            warnings.add(ConfigMessage.of("unknown_theme", "shop '%s' has unknown theme '%s'; using %s",
                    id, theme, ShopThemes.DEFAULT));
            theme = ShopThemes.DEFAULT;
        }

        String subtitle = localizedText(id, object, "subtitle", "", warnings);

        double sellMultiplier = 1.0D;
        if (object.has("sell_multiplier")) {
            try {
                sellMultiplier = object.get("sell_multiplier").getAsDouble();
            } catch (RuntimeException exception) {
                sellMultiplier = Double.NaN;
            }
            if (Double.isNaN(sellMultiplier) || sellMultiplier < 0.0D || sellMultiplier > MAX_SELL_MULTIPLIER) {
                warnings.add(ConfigMessage.of("bad_multiplier",
                        "shop '%s' has an invalid sell_multiplier; it must be 0 to %s. Using 1.0", id, MAX_SELL_MULTIPLIER));
                sellMultiplier = 1.0D;
            }
        }

        return new ShopDefinition(id, name, theme, subtitle, sellMultiplier, entries);
    }

    /**
     * Reads a text field that is either a plain string or a per-language object
     * ({@code {"en_us": "...", "ja_jp": "..."}}). Objects are kept as compact JSON and resolved
     * per viewer by {@link LocalizedText}.
     */
    private static String localizedText(String shopId, JsonObject object, String key, String fallback,
                                        List<ConfigMessage> warnings) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive()) {
            return truncate(shopId, key, element.getAsString(), warnings);
        }
        if (element.isJsonObject()) {
            JsonObject compact = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    compact.addProperty(entry.getKey().toLowerCase(Locale.ROOT),
                            truncate(shopId, key, entry.getValue().getAsString(), warnings));
                }
            }
            String json = compact.toString();
            if (json.length() <= MAX_LOCALIZED_JSON_LENGTH && !compact.isEmpty()) {
                return json;
            }
            warnings.add(ConfigMessage.of("text_one_language",
                    "shop '%s' field '%s' is empty or too long; using one language only", shopId, key));
            String single = LocalizedText.resolve(json, "en_us");
            return truncate(shopId, key, single.startsWith("{") ? fallback : single, warnings);
        }
        return fallback;
    }

    private static String truncate(String shopId, String key, String value, List<ConfigMessage> warnings) {
        if (value.length() <= MAX_DISPLAY_NAME_LENGTH) {
            return value;
        }
        warnings.add(ConfigMessage.of("text_cut", "shop '%s' field '%s' was cut to %s characters",
                shopId, key, MAX_DISPLAY_NAME_LENGTH));
        return value.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }

    private static Optional<ShopDefinition.Entry> parseEntry(String shopId, int index, JsonObject object,
                                                             List<ConfigMessage> warnings) {
        try {
            ResourceLocation itemId = ResourceLocation.tryParse(string(object, "item"));
            int amount = integer(object, "amount");
            long price = longValue(object, "price");
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                throw new ConfigException("unknown_item", "unknown item '%s'", string(object, "item"));
            }
            if (amount < 1 || amount > 64) {
                throw new ConfigException("bad_amount", "amount must be between 1 and 64");
            }
            if (price < 0) {
                throw new ConfigException("bad_price", "price must be zero or positive");
            }
            // A broken limit skips the whole entry: selling it without the limit could give away rare items.
            return Optional.of(new ShopDefinition.Entry(itemId, amount, price, parseLimit(object), parseStock(object)));
        } catch (RuntimeException exception) {
            warnings.add(ConfigMessage.of("entry_skipped", "shop '%s': skipped entry #%s: %s", shopId, index,
                    describe(exception)));
            return Optional.empty();
        }
    }

    static final int MAX_LIMIT = 1_000_000;
    /** One year. */
    static final int MAX_RESTOCK_MINUTES = 525_600;

    private static ShopDefinition.Limit parseLimit(JsonObject entry) {
        if (!entry.has("limit")) {
            return null;
        }
        if (!entry.get("limit").isJsonObject()) {
            throw new ConfigException("limit_not_object",
                    "'limit' must be an object like { \"per_player\": 1, \"period\": \"weekly\" }");
        }
        JsonObject limit = entry.getAsJsonObject("limit");
        int perPlayer = integer(limit, "per_player");
        if (perPlayer < 1 || perPlayer > MAX_LIMIT) {
            throw new ConfigException("bad_per_player", "limit.per_player must be between 1 and %s", MAX_LIMIT);
        }
        String periodId = limit.has("period") ? string(limit, "period") : ShopDefinition.Period.NEVER.id();
        ShopDefinition.Period period = ShopDefinition.Period.byId(periodId).orElseThrow(() ->
                new ConfigException("bad_period", "limit.period must be daily, weekly or never (was '%s')", periodId));
        return new ShopDefinition.Limit(perPlayer, period);
    }

    private static ShopDefinition.Stock parseStock(JsonObject entry) {
        if (!entry.has("stock")) {
            return null;
        }
        if (!entry.get("stock").isJsonObject()) {
            throw new ConfigException("stock_not_object",
                    "'stock' must be an object like { \"max\": 3, \"restock_minutes\": 1440 }");
        }
        JsonObject stock = entry.getAsJsonObject("stock");
        int max = integer(stock, "max");
        if (max < 1 || max > MAX_LIMIT) {
            throw new ConfigException("bad_stock_max", "stock.max must be between 1 and %s", MAX_LIMIT);
        }
        int restock = stock.has("restock_minutes") ? integer(stock, "restock_minutes") : 0;
        if (restock < 0 || restock > MAX_RESTOCK_MINUTES) {
            throw new ConfigException("bad_restock", "stock.restock_minutes must be between 0 and %s", MAX_RESTOCK_MINUTES);
        }
        return new ShopDefinition.Stock(max, restock);
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new ConfigException("missing_field", "missing '%s'", key);
        }
        return object.get(key).getAsString();
    }

    private static int integer(JsonObject object, String key) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, longValue(object, key)));
    }

    private static long longValue(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            throw new ConfigException("missing_field", "missing '%s'", key);
        }
        try {
            return object.get(key).getAsLong();
        } catch (NumberFormatException exception) {
            throw new ConfigException("not_a_number", "'%s' must be a number", key);
        }
    }

    private static Map<String, ShopDefinition> defaultShops() {
        return parse(DEFAULT_CONFIG);
    }
}
