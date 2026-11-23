package com.diamo.cobblemoneconomyplus.content;

import com.cobblemon.mod.common.Cobblemon;
import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.economy.BalanceRanking;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import com.diamo.cobblemoneconomyplus.reward.RewardService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/** Collects the Trainer Card's server-side stats and opens the card on the player's client. */
public final class TrainerCardService {
    private TrainerCardService() {
    }

    public static void open(ServerPlayer player) {
        // Fresh balance and Battle Points first, so the card does not count up from stale values.
        EconomyService.sync(player);
        BalanceRanking ranking = BalanceRanking.cached(player.server);
        int rank = ranking.find(player.getUUID()).map(BalanceRanking.Entry::rank).orElse(0);
        long playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.TrainerCardPayload(
                caughtSpecies(player), playTicks, rank, ranking.size()));
    }

    private static int caughtSpecies(ServerPlayer player) {
        try {
            return RewardService.caughtSpecies(Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player));
        } catch (RuntimeException exception) {
            CobblemonEconomyPlus.LOGGER.warn("Could not read the Pokédex of {} for the Trainer Card",
                    player.getGameProfile().getName(), exception);
            return 0;
        }
    }
}
