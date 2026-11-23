package com.diamo.cobblemoneconomyplus.reward;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Per-player reward bookkeeping, stored in its own file ({@code data/cobblemon_economy_plus_rewards.dat}) next to the
 * balances: which Pokédex milestones were already paid, how many reward coins were paid today (for
 * {@code daily_cap}) and the daily login streak. Kept separate from the balances so an older mod version never rewrites (and loses) it.
 */
public final class RewardSavedData extends SavedData {
    private static final String DATA_NAME = "cobblemon_economy_plus_rewards";
    private static final String VERSION_KEY = "data_version";
    /** Bump this and add a migration step in {@link #load} whenever the layout changes. */
    private static final int CURRENT_DATA_VERSION = 2;
    private static final String PLAYERS_KEY = "players";
    private static final String PLAYER_KEY = "player";
    private static final String MILESTONES_KEY = "milestones";
    private static final String DAY_KEY = "day";
    private static final String TODAY_KEY = "today";
    private static final String LOGIN_DAY_KEY = "login_day";
    private static final String LOGIN_STREAK_KEY = "login_streak";

    private static final Factory<RewardSavedData> FACTORY = new Factory<>(
            RewardSavedData::new,
            RewardSavedData::load,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final Map<UUID, Set<Integer>> claimedMilestones = new HashMap<>();
    private final Map<UUID, DailyTotal> daily = new HashMap<>();
    private final Map<UUID, LoginStreak> logins = new HashMap<>();

    private record DailyTotal(long day, long amount) {
    }

    /** The day (epoch day) of the last paid login bonus and the streak it counted. */
    public record LoginStreak(long day, int streak) {
    }

    public static RewardSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static RewardSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RewardSavedData data = new RewardSavedData();
        int version = tag.getInt(VERSION_KEY);
        if (version > CURRENT_DATA_VERSION) {
            CobblemonEconomyPlus.LOGGER.warn(
                    "Reward data was saved by a newer version of Cobblemon Economy Plus (data version {} > {}). "
                            + "Some data may be lost if you keep using this older version.",
                    version, CURRENT_DATA_VERSION);
        }
        ListTag players = tag.getList(PLAYERS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (!entry.hasUUID(PLAYER_KEY)) {
                continue;
            }
            UUID playerId = entry.getUUID(PLAYER_KEY);
            int[] milestones = entry.getIntArray(MILESTONES_KEY);
            if (milestones.length > 0) {
                Set<Integer> claimed = new TreeSet<>();
                for (int milestone : milestones) {
                    claimed.add(milestone);
                }
                data.claimedMilestones.put(playerId, claimed);
            }
            if (entry.contains(DAY_KEY, Tag.TAG_LONG) && entry.getLong(TODAY_KEY) > 0L) {
                data.daily.put(playerId, new DailyTotal(entry.getLong(DAY_KEY), entry.getLong(TODAY_KEY)));
            }
            if (entry.contains(LOGIN_DAY_KEY, Tag.TAG_LONG)) {
                data.logins.put(playerId, new LoginStreak(entry.getLong(LOGIN_DAY_KEY), entry.getInt(LOGIN_STREAK_KEY)));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Map<UUID, CompoundTag> entries = new HashMap<>();
        claimedMilestones.forEach((playerId, claimed) -> {
            if (!claimed.isEmpty()) {
                entry(entries, playerId).put(MILESTONES_KEY,
                        new IntArrayTag(claimed.stream().mapToInt(Integer::intValue).toArray()));
            }
        });
        daily.forEach((playerId, total) -> {
            CompoundTag entry = entry(entries, playerId);
            entry.putLong(DAY_KEY, total.day());
            entry.putLong(TODAY_KEY, total.amount());
        });
        logins.forEach((playerId, login) -> {
            CompoundTag entry = entry(entries, playerId);
            entry.putLong(LOGIN_DAY_KEY, login.day());
            entry.putInt(LOGIN_STREAK_KEY, login.streak());
        });
        ListTag players = new ListTag();
        players.addAll(entries.values());
        tag.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
        tag.put(PLAYERS_KEY, players);
        return tag;
    }

    private static CompoundTag entry(Map<UUID, CompoundTag> entries, UUID playerId) {
        return entries.computeIfAbsent(playerId, id -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER_KEY, id);
            return entry;
        });
    }

    public Set<Integer> claimedMilestones(UUID playerId) {
        return Set.copyOf(claimedMilestones.getOrDefault(playerId, Set.of()));
    }

    public void claimMilestone(UUID playerId, int milestone) {
        if (claimedMilestones.computeIfAbsent(playerId, id -> new TreeSet<>()).add(milestone)) {
            setDirty();
        }
    }

    /** The player's last paid login bonus, or null when they never received one. */
    public LoginStreak lastLogin(UUID playerId) {
        return logins.get(playerId);
    }

    public void recordLogin(UUID playerId, long day, int streak) {
        logins.put(playerId, new LoginStreak(day, streak));
        setDirty();
    }

    /** Reward coins paid to the player on {@code day} (an epoch day); 0 for any other day. */
    public long rewardedOn(UUID playerId, long day) {
        DailyTotal total = daily.get(playerId);
        return total != null && total.day() == day ? total.amount() : 0L;
    }

    public void addRewarded(UUID playerId, long day, long amount) {
        if (amount <= 0L) {
            return;
        }
        long before = rewardedOn(playerId, day);
        long after = Long.MAX_VALUE - before < amount ? Long.MAX_VALUE : before + amount;
        daily.put(playerId, new DailyTotal(day, after));
        setDirty();
    }
}
