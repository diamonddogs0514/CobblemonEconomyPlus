package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.integration.BattlePlusIntegration;
import com.diamo.cobblemoneconomyplus.config.ModConfig;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.reward.RewardManager;
import com.diamo.cobblemoneconomyplus.reward.RewardService;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for loader-independent initialization. */
public final class CobblemonEconomyPlus {
    public static final String MOD_ID = "cobblemon_economy_plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CobblemonEconomyPlus() {
    }

    public static void init() {
        RewardService.register();
        LOGGER.info("{} initialized", MOD_ID);
    }

    /** Called by each loader once the server has started. */
    public static void onServerStarted(MinecraftServer server) {
        ShopManager.reload(server);
        ModConfig.reload(server);
        RewardManager.reload(server);
        RewardService.onServerStarted(server);
        BattlePlusIntegration.onServerStarted(server);
    }

    /** Called by each loader when the server is stopping. */
    public static void onServerStopped(MinecraftServer server) {
        RewardService.onServerStopped();
        BattlePlusIntegration.onServerStopped();
    }

    /** Called by each loader when a player joins the server. */
    public static void onPlayerJoin(ServerPlayer player) {
        EconomyService.sync(player);
        RewardService.onPlayerJoin(player);
    }
}
