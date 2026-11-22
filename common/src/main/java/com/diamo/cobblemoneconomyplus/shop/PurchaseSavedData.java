package com.diamo.cobblemoneconomyplus.shop;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Purchase limits and stock, stored in {@code data/cobblemon_economy_plus_purchases.dat}: how many bundles each player
 * bought of each limited entry (per period), and how much of each stock has been sold. Entries are identified by
 * {@link ShopDefinition.Entry#counterKey}.
 */
public final class PurchaseSavedData extends SavedData {
    private static final String DATA_NAME = "cobblemon_economy_plus_purchases";
    private static final String VERSION_KEY = "data_version";
    /** Bump this and add a migration step in {@link #load} whenever the layout changes. */
    private static final int CURRENT_DATA_VERSION = 1;

    private static final Factory<PurchaseSavedData> FACTORY = new Factory<>(
            PurchaseSavedData::new,
            PurchaseSavedData::load,
            DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final Map<UUID, Map<String, PurchaseRules.Counter>> counters = new HashMap<>();
    private final Map<String, PurchaseRules.StockState> stocks = new HashMap<>();

    public static PurchaseSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static PurchaseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PurchaseSavedData data = new PurchaseSavedData();
        int version = tag.getInt(VERSION_KEY);
        if (version > CURRENT_DATA_VERSION) {
            CobblemonEconomyPlus.LOGGER.warn(
                    "Purchase data was saved by a newer version of Cobblemon Economy Plus (data version {} > {}). "
                            + "Some data may be lost if you keep using this older version.",
                    version, CURRENT_DATA_VERSION);
        }
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag player = players.getCompound(index);
            if (!player.hasUUID("player")) {
                continue;
            }
            ListTag bought = player.getList("bought", Tag.TAG_COMPOUND);
            Map<String, PurchaseRules.Counter> map = new HashMap<>();
            for (int i = 0; i < bought.size(); i++) {
                CompoundTag counter = bought.getCompound(i);
                if (!counter.getString("entry").isEmpty() && counter.getInt("count") > 0) {
                    map.put(counter.getString("entry"),
                            new PurchaseRules.Counter(counter.getLong("period"), counter.getInt("count")));
                }
            }
            if (!map.isEmpty()) {
                data.counters.put(player.getUUID("player"), map);
            }
        }
        ListTag stockList = tag.getList("stock", Tag.TAG_COMPOUND);
        for (int index = 0; index < stockList.size(); index++) {
            CompoundTag stock = stockList.getCompound(index);
            if (!stock.getString("entry").isEmpty() && stock.getInt("sold") > 0) {
                data.stocks.put(stock.getString("entry"),
                        new PurchaseRules.StockState(stock.getInt("sold"), stock.getLong("since")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        counters.forEach((playerId, map) -> {
            ListTag bought = new ListTag();
            map.forEach((entry, counter) -> {
                CompoundTag item = new CompoundTag();
                item.putString("entry", entry);
                item.putLong("period", counter.periodStart());
                item.putInt("count", counter.count());
                bought.add(item);
            });
            if (!bought.isEmpty()) {
                CompoundTag player = new CompoundTag();
                player.putUUID("player", playerId);
                player.put("bought", bought);
                players.add(player);
            }
        });
        ListTag stockList = new ListTag();
        stocks.forEach((entry, state) -> {
            CompoundTag stock = new CompoundTag();
            stock.putString("entry", entry);
            stock.putInt("sold", state.sold());
            stock.putLong("since", state.windowStart());
            stockList.add(stock);
        });
        tag.putInt(VERSION_KEY, CURRENT_DATA_VERSION);
        tag.put("players", players);
        tag.put("stock", stockList);
        return tag;
    }

    @Nullable PurchaseRules.Counter counter(UUID playerId, String entryKey) {
        Map<String, PurchaseRules.Counter> map = counters.get(playerId);
        return map == null ? null : map.get(entryKey);
    }

    void setCounter(UUID playerId, String entryKey, PurchaseRules.Counter counter) {
        counters.computeIfAbsent(playerId, id -> new HashMap<>()).put(entryKey, counter);
        setDirty();
    }

    @Nullable PurchaseRules.StockState stock(String entryKey) {
        return stocks.get(entryKey);
    }

    void setStock(String entryKey, PurchaseRules.StockState state) {
        if (state.sold() <= 0) {
            stocks.remove(entryKey);
        } else {
            stocks.put(entryKey, state);
        }
        setDirty();
    }

    /** Forgets what one player bought (all entries), e.g. for an admin reset. Returns whether anything was removed. */
    public boolean resetPlayer(UUID playerId) {
        boolean removed = counters.remove(playerId) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Refills every stock. Returns how many stocks were not full. */
    public int restockAll() {
        int count = stocks.size();
        if (count > 0) {
            stocks.clear();
            setDirty();
        }
        return count;
    }
}
