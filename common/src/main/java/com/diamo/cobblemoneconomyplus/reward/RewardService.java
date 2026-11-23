package com.diamo.cobblemoneconomyplus.reward;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import com.diamo.cobblemoneconomyplus.api.RewardEvent;
import com.diamo.cobblemoneconomyplus.api.RewardType;
import com.diamo.cobblemoneconomyplus.economy.EconomyEvents;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pays coins for Cobblemon activity: winning wild and NPC trainer battles, captures and Pokédex milestones.
 * Amounts come from {@link RewardManager#config()}; every reward goes through {@link EconomyEvents#fireReward} first
 * so other mods can cancel or change it.
 */
public final class RewardService {
    /** NPCs with this scoreboard tag never pay a trainer battle reward. */
    public static final String NO_REWARD_TAG = "cep_no_reward";

    private static volatile MinecraftServer server;
    private static boolean registered;

    private RewardService() {
    }

    /** Subscribes to Cobblemon's events. Safe to call more than once. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.LOWEST, RewardService::onBattleVictory);
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.LOWEST, RewardService::onCaptured);
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.LOWEST, RewardService::onPokedexChanged);
    }

    public static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
    }

    public static void onServerStopped() {
        server = null;
    }

    /**
     * Pays the daily login bonus, and Pokédex milestones reached while the player was offline or before rewards
     * existed.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        guarded("login", () -> {
            if (!active()) {
                return;
            }
            payLoginBonus(player, false);
            if (RewardManager.config().pokedexEnabled()) {
                checkMilestones(player, Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player));
            }
        });
    }

    // ------------------------------------------------------------------ daily login

    /**
     * {@code /cep daily}: pays today's login bonus if it was not paid yet (e.g. the player stayed online past
     * midnight), then shows the bonus calendar. Returns false when the login bonus is switched off.
     */
    public static boolean openDailyLogin(ServerPlayer player) {
        RewardConfig.DailyLogin login = RewardManager.config().dailyLogin();
        if (!active() || !login.enabled() || login.days().isEmpty()) {
            return false;
        }
        if (!payLoginBonus(player, true)) {
            showCalendar(player, login, false);
        }
        return true;
    }

    /** Pays today's login bonus unless it was already paid; returns whether it was handled just now. */
    private static boolean payLoginBonus(ServerPlayer player, boolean alwaysShowScreen) {
        RewardConfig.DailyLogin login = RewardManager.config().dailyLogin();
        if (!login.enabled() || login.days().isEmpty()) {
            return false;
        }
        RewardSavedData data = RewardSavedData.get(player.server);
        long today = LocalDate.now().toEpochDay();
        RewardSavedData.LoginStreak last = data.lastLogin(player.getUUID());
        int count = last == null ? 1 : RewardRules.loginCount(last.day(), last.streak(), today, login.resetOnMiss());
        if (count == 0) {
            return false;
        }
        // Recorded even when a listener cancels it, so the bonus is not offered again at every login today.
        data.recordLogin(player.getUUID(), today, count);
        int index = login.dayIndex(count);
        RewardConfig.LoginDay day = login.days().get(index);

        RewardEvent event = EconomyEvents.fireReward(
                new RewardEvent(RewardType.DAILY_LOGIN, player, day.coins(), false, 0, count, null, null));
        if (event.isCancelled()) {
            return true;
        }
        long coins = event.amount();
        if (coins > 0L) {
            EconomyService.deposit(player.server, player.getUUID(), coins, "reward:" + RewardType.DAILY_LOGIN.id(),
                    "day=" + (index + 1) + ";count=" + count);
        }
        List<ItemStack> items = stacks(day);
        for (ItemStack stack : items) {
            ItemStack given = stack.copy();
            // Whatever the inventory cannot take is dropped at the player's feet.
            player.getInventory().add(given);
            if (!given.isEmpty()) {
                player.drop(given, false);
            }
        }
        EconomyService.sync(player);

        if (coins > 0L || !items.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.cobblemon_economy_plus.reward.daily_login",
                    index + 1, describe(coins, items)).withStyle(ChatFormatting.GOLD));
        }
        if (login.showScreen() || alwaysShowScreen) {
            showCalendar(player, login, true);
        }
        return true;
    }

    private static void showCalendar(ServerPlayer player, RewardConfig.DailyLogin login, boolean claimedNow) {
        RewardSavedData.LoginStreak last = RewardSavedData.get(player.server).lastLogin(player.getUUID());
        int count = last == null ? 1 : Math.max(1, last.streak());
        List<EconomyPayloads.DailyLoginDay> days = new ArrayList<>();
        for (RewardConfig.LoginDay day : login.days()) {
            days.add(new EconomyPayloads.DailyLoginDay(day.coins(), stacks(day)));
        }
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.DailyLoginPayload(
                days, login.dayIndex(count), count, login.resetOnMiss(), claimedNow));
    }

    /** The day's items that exist (unknown ids were reported when the file was loaded). */
    private static List<ItemStack> stacks(RewardConfig.LoginDay day) {
        List<ItemStack> stacks = new ArrayList<>();
        for (RewardConfig.LoginItem item : day.items()) {
            if (BuiltInRegistries.ITEM.containsKey(item.item())) {
                stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(item.item()), item.count()));
            }
        }
        return stacks;
    }

    /** "200 coins, Poké Ball ×5" for the chat message. */
    private static Component describe(long coins, List<ItemStack> items) {
        MutableComponent text = Component.empty();
        boolean first = true;
        if (coins > 0L) {
            text.append(Component.translatable("message.cobblemon_economy_plus.reward.coins", EconomyFormat.money(coins)));
            first = false;
        }
        for (ItemStack stack : items) {
            if (!first) {
                text.append(Component.translatable("message.cobblemon_economy_plus.reward.separator"));
            }
            text.append(stack.getHoverName().copy()).append(" ×" + stack.getCount());
            first = false;
        }
        return text;
    }

    // ------------------------------------------------------------------ battles

    private static void onBattleVictory(BattleVictoryEvent event) {
        guarded("battle victory", () -> {
            if (!active() || event.getWasWildCapture()) {
                return;
            }
            List<BattleActor> losers = event.getLosers();
            // Player-versus-player battles never pay.
            if (losers.stream().anyMatch(actor -> actor.getType() == ActorType.PLAYER)) {
                return;
            }
            List<ServerPlayer> players = winningPlayers(event.getWinners());
            if (players.isEmpty()) {
                return;
            }
            UUID battleId = event.getBattle().getBattleId();
            RewardConfig config = RewardManager.config();
            for (ServerPlayer player : players) {
                Payout payout = new Payout();
                for (BattleActor loser : losers) {
                    if (loser.getType() == ActorType.WILD) {
                        for (BattlePokemon battlePokemon : loser.getPokemonList()) {
                            Pokemon pokemon = battlePokemon.getOriginalPokemon();
                            boolean alpha = pokemon.isAlpha();
                            int level = pokemon.getLevel();
                            payout.add(pay(player, RewardType.WILD_BATTLE, config.wildFormula(alpha).amount(level),
                                    alpha, level, 0, battleId, null), alpha, RewardType.WILD_BATTLE);
                        }
                    } else if (loser instanceof NPCBattleActor npcActor) {
                        Entity npc = npcActor.getEntity();
                        if (npc != null && npc.getTags().contains(NO_REWARD_TAG)) {
                            continue;
                        }
                        int level = loser.getPokemonList().stream()
                                .mapToInt(battlePokemon -> battlePokemon.getOriginalPokemon().getLevel())
                                .max().orElse(1);
                        payout.add(pay(player, RewardType.TRAINER_BATTLE, config.trainerBattle().amount(level),
                                false, level, 0, battleId, npc), false, RewardType.TRAINER_BATTLE);
                    }
                }
                payout.announce(player);
            }
        });
    }

    private static List<ServerPlayer> winningPlayers(List<BattleActor> winners) {
        MinecraftServer current = server;
        List<ServerPlayer> players = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (BattleActor winner : winners) {
            if (winner.getType() != ActorType.PLAYER) {
                continue;
            }
            for (UUID playerId : winner.getPlayerUUIDs()) {
                ServerPlayer player = current == null ? null : current.getPlayerList().getPlayer(playerId);
                if (player != null && seen.add(playerId)) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    // ------------------------------------------------------------------ captures

    private static void onCaptured(PokemonCapturedEvent event) {
        guarded("capture", () -> {
            if (!active()) {
                return;
            }
            Pokemon pokemon = event.getPokemon();
            boolean alpha = pokemon.isAlpha();
            int level = pokemon.getLevel();
            Payout payout = new Payout();
            payout.add(pay(event.getPlayer(), RewardType.CAPTURE,
                    RewardManager.config().captureFormula(alpha).amount(level), alpha, level, 0, null, null), alpha, RewardType.CAPTURE);
            payout.announce(event.getPlayer());
        });
    }

    // ------------------------------------------------------------------ Pokédex

    private static void onPokedexChanged(PokedexDataChangedEvent.Post event) {
        guarded("Pokédex change", () -> {
            MinecraftServer current = server;
            if (current == null || !active() || !RewardManager.config().pokedexEnabled()) {
                return;
            }
            ServerPlayer player = current.getPlayerList().getPlayer(event.getPlayerUUID());
            if (player != null) {
                checkMilestones(player, event.getPokedexManager());
            }
        });
    }

    private static void checkMilestones(ServerPlayer player, AbstractPokedexManager pokedex) {
        RewardConfig config = RewardManager.config();
        RewardSavedData data = RewardSavedData.get(player.server);
        int caught = caughtSpecies(pokedex);
        for (int milestone : RewardRules.dueMilestones(config.milestones(), data.claimedMilestones(player.getUUID()), caught)) {
            // Claimed even when a listener cancels it, so it is not offered again on every Pokédex change.
            data.claimMilestone(player.getUUID(), milestone);
            long paid = pay(player, RewardType.POKEDEX_MILESTONE, config.milestones().get(milestone),
                    false, 0, milestone, null, null);
            if (paid > 0L) {
                player.sendSystemMessage(Component.translatable("message.cobblemon_economy_plus.reward.pokedex_milestone",
                        milestone, EconomyFormat.money(paid)).withStyle(ChatFormatting.GOLD));
            }
        }
    }

    public static int caughtSpecies(AbstractPokedexManager pokedex) {
        int count = 0;
        for (SpeciesDexRecord record : pokedex.getSpeciesRecords().values()) {
            if (record.hasAtLeast(PokedexEntryProgress.OWNED)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ paying

    private static boolean active() {
        return server != null && RewardManager.config().enabled();
    }

    /**
     * Pays one reward and returns the coins actually paid (0 when disabled, cancelled or over the daily cap).
     * Pokédex milestones are not limited by the daily cap (nor is the login bonus, which is paid separately).
     */
    private static long pay(ServerPlayer player, RewardType type, long amount, boolean alpha, int level, int milestone,
                            UUID battleId, Entity opponent) {
        if (amount <= 0L) {
            return 0L;
        }
        RewardEvent event = EconomyEvents.fireReward(
                new RewardEvent(type, player, amount, alpha, level, milestone, battleId, opponent));
        if (event.isCancelled() || event.amount() <= 0L) {
            return 0L;
        }
        long coins = event.amount();
        RewardSavedData data = RewardSavedData.get(player.server);
        long today = LocalDate.now().toEpochDay();
        if (type != RewardType.POKEDEX_MILESTONE) {
            coins = RewardRules.capped(coins, RewardManager.config().dailyCap(), data.rewardedOn(player.getUUID(), today));
            if (coins <= 0L) {
                player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.reward.daily_cap")
                        .withStyle(ChatFormatting.GRAY), true);
                return 0L;
            }
            data.addRewarded(player.getUUID(), today, coins);
        }
        String detail = "level=" + level + (alpha ? ";alpha" : "") + (milestone > 0 ? ";milestone=" + milestone : "")
                + (battleId != null ? ";battle=" + battleId : "");
        EconomyService.deposit(player.server, player.getUUID(), coins, "reward:" + type.id(), detail);
        EconomyService.sync(player);
        return coins;
    }

    /** Adds up one event's payments so the player sees a single action-bar line. */
    private static final class Payout {
        private long total;
        private RewardType type;
        private boolean alpha;

        void add(long coins, boolean alphaReward, RewardType rewardType) {
            if (coins <= 0L) {
                return;
            }
            if (type == null) {
                type = rewardType;
            }
            total += coins;
            alpha |= alphaReward;
        }

        void announce(ServerPlayer player) {
            if (total <= 0L || type == null) {
                return;
            }
            String key = "message.cobblemon_economy_plus.reward." + type.id() + (alpha ? "_alpha" : "");
            player.displayClientMessage(Component.translatable(key, EconomyFormat.money(total))
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static void guarded(String what, Runnable action) {
        MinecraftServer current = server;
        if (current != null && !current.isSameThread()) {
            current.execute(() -> guarded(what, action));
            return;
        }
        try {
            action.run();
        } catch (RuntimeException exception) {
            CobblemonEconomyPlus.LOGGER.error("Could not pay the Cobblemon {} reward", what, exception);
        }
    }
}
