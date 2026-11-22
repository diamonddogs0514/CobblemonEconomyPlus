package com.diamo.cobblemoneconomyplus.reward;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.google.gson.JsonParseException;
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

    /** Problems found while loading. {@code errors} mean the file was not used at all. */
    public record Result(List<String> errors, List<String> warnings) {
        public Result {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    public static RewardConfig config() {
        return config;
    }

    public static synchronized Result reload(MinecraftServer server) {
        boolean firstLoad = loadedFor != server;
        Path path = path(server);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, RewardConfig.DEFAULT_JSON, StandardCharsets.UTF_8);
                CobblemonEconomyPlus.LOGGER.info("Created default reward settings at {}", path);
            }
            RewardConfig parsed = read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
            if (parsed != null) {
                config = parsed;
            } else if (firstLoad) {
                config = RewardConfig.defaults();
            }
        } catch (IOException exception) {
            errors.add(FILE_NAME + ": could not read " + path + ": " + exception.getMessage());
            if (firstLoad) {
                config = RewardConfig.defaults();
            }
        }
        loadedFor = server;
        for (String warning : warnings) {
            CobblemonEconomyPlus.LOGGER.warn(warning);
        }
        for (String error : errors) {
            CobblemonEconomyPlus.LOGGER.error("Could not load {}. {}", error, firstLoad
                    ? "Using the built-in reward defaults until it is fixed and reloaded."
                    : "Keeping the previously loaded reward settings.");
        }
        return new Result(errors, warnings);
    }

    /** Checks rewards.json without applying it. */
    public static Result validate(MinecraftServer server) {
        Path path = path(server);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.exists(path)) {
            errors.add(FILE_NAME + ": file not found (" + path + "); it is created with defaults on the next reload");
            return new Result(errors, warnings);
        }
        try {
            read(Files.readString(path, StandardCharsets.UTF_8), errors, warnings);
        } catch (IOException exception) {
            errors.add(FILE_NAME + ": could not read " + path + ": " + exception.getMessage());
        }
        return new Result(errors, warnings);
    }

    /** Parses the file text; returns null (and adds an error) when it cannot be used. Never throws. */
    static RewardConfig read(String source, List<String> errors, List<String> warnings) {
        List<String> fileWarnings = new ArrayList<>();
        try {
            RewardConfig parsed = RewardConfig.parse(source, fileWarnings);
            fileWarnings.forEach(warning -> warnings.add(FILE_NAME + ": " + warning));
            return parsed;
        } catch (RuntimeException exception) {
            fileWarnings.forEach(warning -> warnings.add(FILE_NAME + ": " + warning));
            errors.add(FILE_NAME + ": " + describe(exception));
            return null;
        }
    }

    private static String describe(RuntimeException exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
        return exception instanceof JsonParseException ? "invalid JSON (" + message + ")" : message;
    }

    public static Path path(MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve(CobblemonEconomyPlus.MOD_ID)
                .resolve(FILE_NAME);
    }
}
