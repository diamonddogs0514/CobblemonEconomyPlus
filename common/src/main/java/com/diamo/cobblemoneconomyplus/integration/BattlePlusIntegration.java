package com.diamo.cobblemoneconomyplus.integration;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional integration with Cobblemon Battle Plus: the Trainer Card shows the player's Battle Points. This mod does
 * not depend on Battle Plus; its public API ({@code BattlePlusApi}) is looked up at runtime, so everything here is a
 * no-op when it is not installed.
 *
 * <p>Battle Points are pushed to the client together with the balance. Battle Plus 3.1.0+ reports every change, so
 * the card stays up to date; with older versions the points are refreshed when the balance is synced.</p>
 */
public final class BattlePlusIntegration {
    /** Sent to the client when Battle Plus is not installed: the card then hides the Battle Points row. */
    public static final int NOT_AVAILABLE = -1;

    private static final String API_CLASS = "jp.foxhound.cobblemonbattleplus.api.BattlePlusApi";
    private static final String CHANGE_CLASS = "jp.foxhound.cobblemonbattleplus.api.BattlePointsChange";
    private static final Logger LOGGER = LoggerFactory.getLogger(CobblemonEconomyPlus.MOD_ID);

    private static boolean initialised;
    private static Method getBattlePoints;
    private static volatile MinecraftServer server;

    private BattlePlusIntegration() {
    }

    /** Looks up Battle Plus once; call when the server has started (every mod is loaded by then). */
    public static synchronized void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        if (initialised) {
            return;
        }
        initialised = true;
        Class<?> api;
        try {
            api = Class.forName(API_CLASS);
        } catch (ClassNotFoundException | LinkageError notInstalled) {
            return;
        }
        try {
            getBattlePoints = api.getMethod("getBattlePoints", MinecraftServer.class, UUID.class);
        } catch (NoSuchMethodException exception) {
            LOGGER.warn("Cobblemon Battle Plus is installed but its API could not be used; the Trainer Card will not show Battle Points");
            return;
        }
        registerChangeListener(api);
        LOGGER.info("Cobblemon Battle Plus found: the Trainer Card shows Battle Points");
    }

    public static void onServerStopped() {
        server = null;
    }

    /** The player's Battle Points, or {@link #NOT_AVAILABLE} when Battle Plus is not installed. */
    public static int battlePoints(MinecraftServer server, UUID playerId) {
        Method method = getBattlePoints;
        if (method == null || server == null) {
            return NOT_AVAILABLE;
        }
        try {
            return Math.max(0, (Integer) method.invoke(null, server, playerId));
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException | NullPointerException exception) {
            LOGGER.error("Failed to read Battle Points for {}", playerId, exception);
            return NOT_AVAILABLE;
        }
    }

    /** Battle Plus 3.1.0+ reports Battle Point changes; push them to the player's Trainer Card right away. */
    private static void registerChangeListener(Class<?> api) {
        try {
            Method onChanged = api.getMethod("onBattlePointsChanged", Consumer.class);
            Method playerId = Class.forName(CHANGE_CLASS).getMethod("playerId");
            Consumer<Object> listener = change -> {
                MinecraftServer current = server;
                if (current == null) {
                    return;
                }
                try {
                    EconomyService.syncIfOnline(current, (UUID) playerId.invoke(change));
                } catch (IllegalAccessException | InvocationTargetException | ClassCastException exception) {
                    LOGGER.error("Failed to handle a Battle Points change", exception);
                }
            };
            onChanged.invoke(null, listener);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.info("This Cobblemon Battle Plus version does not report Battle Point changes (3.1.0 or newer does); "
                    + "the Trainer Card refreshes them with the balance");
        }
    }
}
