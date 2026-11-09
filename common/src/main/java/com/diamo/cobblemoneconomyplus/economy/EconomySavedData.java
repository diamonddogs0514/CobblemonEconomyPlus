package com.diamo.cobblemoneconomyplus.economy;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side, world-persistent balances. This intentionally lives in SavedData
 * instead of an item inventory so the balance is loader-independent and cannot
 * be dropped, stolen, or duplicated as an item stack.
 */
public final class EconomySavedData extends SavedData {
    private static final String DATA_NAME = "cobblemon_economy_plus_balances";
    private static final String BALANCES_KEY = "balances";
    private static final String PLAYER_KEY = "player";
    private static final String BALANCE_KEY = "balance";

    private static final Factory<EconomySavedData> FACTORY = new Factory<>(
            EconomySavedData::new,
            EconomySavedData::load,
            // A SavedData type is required by Fabric's 1.21.1 mapping. The data
            // format is simple UUID/long pairs and remains self-contained.
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final Map<UUID, Long> balances = new HashMap<>();

    public static EconomySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Version of the saved layout. Version 0 = data written before versioning existed (same layout as 1).
     * Bump this and add a step in {@link #load} whenever the layout changes, so old worlds can be migrated.
     */
    private static final int CURRENT_DATA_VERSION = 1;
    private static final String VERSION_KEY = "data_version";

    static EconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomySavedData data = new EconomySavedData();
        int version = tag.contains(VERSION_KEY, Tag.TAG_INT) ? tag.getInt(VERSION_KEY) : 0;
        if (version > CURRENT_DATA_VERSION) {
            // Saved by a newer version of the mod. Read what we understand, but say so loudly: saving will
            // rewrite the file in this (older) layout.
            CobblemonEconomyPlus.LOGGER.warn(
                    "Balance data was saved by a newer version of Cobblemon Economy Plus (data version {} > {}). "
                            + "Some data may be lost if you keep using this older version.",
                    version, CURRENT_DATA_VERSION);
        }
        // Migration steps go here: if (version < 2) { ...convert tag to layout 2... }
        ListTag entries = tag.getList(BALANCES_KEY, Tag.TAG_COMPOUND);

        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!entry.hasUUID(PLAYER_KEY)) {
                continue;
            }

            long balance = entry.getLong(BALANCE_KEY);
            if (balance > 0) {
                data.balances.put(entry.getUUID(PLAYER_KEY), balance);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        balances.forEach((playerId, balance) -> {
            if (balance <= 0) {
                return;
            }

            CompoundTag entry = new CompoundTag();
            entry.putUUID(PLAYER_KEY, playerId);
            entry.putLong(BALANCE_KEY, balance);
            entries.add(entry);
        });
        tag.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
        tag.put(BALANCES_KEY, entries);
        return tag;
    }

    public long balance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    public void setBalance(UUID playerId, long amount) {
        long normalized = Math.max(0L, amount);
        if (normalized == 0L) {
            balances.remove(playerId);
        } else {
            balances.put(playerId, normalized);
        }
        setDirty();
    }

    /** Adds funds and saturates at Long.MAX_VALUE instead of overflowing. */
    public long deposit(UUID playerId, long amount) {
        if (amount <= 0) {
            return balance(playerId);
        }

        long current = balance(playerId);
        long updated = Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
        setBalance(playerId, updated);
        return updated;
    }

    public boolean withdraw(UUID playerId, long amount) {
        if (amount < 0 || balance(playerId) < amount) {
            return false;
        }

        setBalance(playerId, balance(playerId) - amount);
        return true;
    }
}
