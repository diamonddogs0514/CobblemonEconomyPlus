package com.diamo.cobblemoneconomyplus.config;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * General server settings from {@code config/cobblemon_economy_plus/config.json}. A file that cannot be read keeps
 * the previously loaded settings (the defaults on the first load).
 */
public final class ModConfig {
    static final String FILE_NAME = "config.json";
    static final String DEFAULT_JSON = """
            {
              "purchase_limits_enabled": true
            }
            """;

    /** The settings in effect. */
    public record Settings(boolean purchaseLimitsEnabled) {
        public static final Settings DEFAULTS = new Settings(true);
    }

    private static volatile Settings settings = Settings.DEFAULTS;
    private static MinecraftServer loadedFor;

    private ModConfig() {
    }

    public static Settings get() {
        return settings;
    }

    public static synchronized ConfigResult reload(MinecraftServer server) {
        boolean firstLoad = loadedFor != server;
        Path path = path(server);
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_JSON, StandardCharsets.UTF_8);
                CobblemonEconomyPlus.LOGGER.info("Created default settings at {}", path);
            }
            Settings parsed = read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
            if (parsed != null) {
                settings = parsed;
            } else if (firstLoad) {
                settings = Settings.DEFAULTS;
            }
        } catch (IOException exception) {
            errors.add(ShopManager.cannotRead(path, exception).inFile(FILE_NAME));
            if (firstLoad) {
                settings = Settings.DEFAULTS;
            }
        }
        loadedFor = server;
        warnings.forEach(warning -> CobblemonEconomyPlus.LOGGER.warn(warning.text()));
        for (ConfigMessage error : errors) {
            CobblemonEconomyPlus.LOGGER.error("Could not load {}. {}", error.text(), firstLoad
                    ? "Using the built-in defaults until it is fixed and reloaded."
                    : "Keeping the previously loaded settings.");
        }
        return new ConfigResult(errors, warnings);
    }

    /** Checks config.json without applying it. */
    public static ConfigResult validate(MinecraftServer server) {
        Path path = path(server);
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();
        if (!Files.exists(path)) {
            errors.add(ShopManager.fileNotFound(path).inFile(FILE_NAME));
        } else {
            try {
                read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
            } catch (IOException exception) {
                errors.add(ShopManager.cannotRead(path, exception).inFile(FILE_NAME));
            }
        }
        return new ConfigResult(errors, warnings);
    }

    /** Parses the file text; returns null (and adds an error) when it cannot be used. Never throws. */
    static Settings read(String source, List<ConfigMessage> errors, List<ConfigMessage> warnings) {
        try {
            JsonElement root = JsonParser.parseString(source);
            if (!root.isJsonObject()) {
                errors.add(ConfigMessage.of("root_not_object", "the root JSON value must be an object").inFile(FILE_NAME));
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            return new Settings(bool(json, "purchase_limits_enabled", Settings.DEFAULTS.purchaseLimitsEnabled(), warnings));
        } catch (JsonParseException exception) {
            errors.add(ShopManager.describe(exception).inFile(FILE_NAME));
            return null;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback, List<ConfigMessage> warnings) {
        if (!json.has(key)) {
            return fallback;
        }
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        warnings.add(ConfigMessage.of("not_boolean", "%s must be true or false; using %s", key, fallback).inFile(FILE_NAME));
        return fallback;
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve(CobblemonEconomyPlus.MOD_ID)
                .resolve(FILE_NAME);
    }
}
