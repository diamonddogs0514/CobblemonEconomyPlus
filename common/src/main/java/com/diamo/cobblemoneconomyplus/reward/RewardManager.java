package com.diamo.cobblemoneconomyplus.reward;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.config.ConfigMessage;
import com.diamo.cobblemoneconomyplus.config.ConfigResult;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@code rewards.json}. Like the shop files, a file that cannot be read keeps the previously loaded settings
 * (or the defaults on the first load), so a typo never switches rewards off or changes amounts by surprise.
 */
public final class RewardManager {
    static final String FILE_NAME = "rewards.json";

    private static volatile RewardConfig config = RewardConfig.defaults();
    private static MinecraftServer loadedFor;

    private RewardManager() {
    }

    public static RewardConfig config() {
        return config;
    }

    public static synchronized ConfigResult reload(MinecraftServer server) {
        boolean firstLoad = loadedFor != server;
        Path path = path(server);
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, RewardConfig.DEFAULT_JSON, StandardCharsets.UTF_8);
                CobblemonEconomyPlus.LOGGER.info("Created default reward settings at {}", path);
            }
            RewardConfig parsed = read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
            if (parsed != null) {
                checkItems(parsed, warnings);
                config = parsed;
            } else if (firstLoad) {
                config = RewardConfig.defaults();
            }
        } catch (IOException exception) {
            errors.add(ShopManager.cannotRead(path, exception).inFile(FILE_NAME));
            if (firstLoad) {
                config = RewardConfig.defaults();
            }
        }
        loadedFor = server;
        for (ConfigMessage warning : warnings) {
            CobblemonEconomyPlus.LOGGER.warn(warning.text());
        }
        for (ConfigMessage error : errors) {
            CobblemonEconomyPlus.LOGGER.error("Could not load {}. {}", error.text(), firstLoad
                    ? "Using the built-in reward defaults until it is fixed and reloaded."
                    : "Keeping the previously loaded reward settings.");
        }
        return new ConfigResult(errors, warnings);
    }

    /** Checks rewards.json without applying it. */
    public static ConfigResult validate(MinecraftServer server) {
        Path path = path(server);
        List<ConfigMessage> errors = new ArrayList<>();
        List<ConfigMessage> warnings = new ArrayList<>();
        if (!Files.exists(path)) {
            errors.add(ShopManager.fileNotFound(path).inFile(FILE_NAME));
            return new ConfigResult(errors, warnings);
        }
        try {
            RewardConfig parsed = read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
            if (parsed != null) {
                checkItems(parsed, warnings);
            }
        } catch (IOException exception) {
            errors.add(ShopManager.cannotRead(path, exception).inFile(FILE_NAME));
        }
        return new ConfigResult(errors, warnings);
    }

    /** Parses the file text; returns null (and adds an error) when it cannot be used. Never throws. */
    static RewardConfig read(String source, List<ConfigMessage> errors, List<ConfigMessage> warnings) {
        List<ConfigMessage> fileWarnings = new ArrayList<>();
        try {
            RewardConfig parsed = RewardConfig.parse(source, fileWarnings);
            fileWarnings.forEach(warning -> warnings.add(warning.inFile(FILE_NAME)));
            return parsed;
        } catch (RuntimeException exception) {
            fileWarnings.forEach(warning -> warnings.add(warning.inFile(FILE_NAME)));
            errors.add(ShopManager.describe(exception).inFile(FILE_NAME));
            return null;
        }
    }

    /** Reports login bonus items that do not exist (they are skipped when the bonus is paid). */
    private static void checkItems(RewardConfig parsed, List<ConfigMessage> warnings) {
        List<RewardConfig.LoginDay> days = parsed.dailyLogin().days();
        for (int index = 0; index < days.size(); index++) {
            for (RewardConfig.LoginItem item : days.get(index).items()) {
                if (!BuiltInRegistries.ITEM.containsKey(item.item())) {
                    warnings.add(ConfigMessage.of("login_unknown_item",
                            "daily_login.days: unknown item '%s' on day %s; it is not given",
                            item.item().toString(), index + 1).inFile(FILE_NAME));
                }
            }
        }
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve(CobblemonEconomyPlus.MOD_ID)
                .resolve(FILE_NAME);
    }
}
