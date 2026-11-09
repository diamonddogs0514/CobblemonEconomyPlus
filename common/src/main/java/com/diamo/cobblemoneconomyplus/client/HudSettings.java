package com.diamo.cobblemoneconomyplus.client;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Client-side display preference for the balance HUD, stored in
 * {@code config/cobblemon_economy_plus_client.json} ({@code {"hud_mode": "on_change"}}).
 * Cycled in game with Shift + the "show balance" key.
 */
final class HudSettings {
    enum Mode {
        /** Always visible. */
        ALWAYS,
        /** Only for a few seconds after the balance changes (default). */
        ON_CHANGE,
        /** Never, except while the show-balance key is held. */
        HIDDEN;

        String translationKey() {
            return "gui.cobblemon_economy_plus.hud_mode." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static Mode mode;

    private HudSettings() {
    }

    static Mode mode() {
        if (mode == null) {
            mode = load();
        }
        return mode;
    }

    static Mode cycle() {
        Mode[] values = Mode.values();
        mode = values[(mode().ordinal() + 1) % values.length];
        save();
        return mode;
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config")
                .resolve("cobblemon_economy_plus_client.json");
    }

    private static Mode load() {
        try {
            Path path = path();
            if (Files.exists(path)) {
                JsonObject object = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (object.has("hud_mode")) {
                    return Mode.valueOf(object.get("hud_mode").getAsString().toUpperCase(Locale.ROOT));
                }
            }
        } catch (IOException | RuntimeException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not read the client HUD setting; using the default", exception);
        }
        return Mode.ON_CHANGE;
    }

    private static void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            JsonObject object = new JsonObject();
            object.addProperty("hud_mode", mode.name().toLowerCase(Locale.ROOT));
            Files.writeString(path, object.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not save the client HUD setting", exception);
        }
    }
}
