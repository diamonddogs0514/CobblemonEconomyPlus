package com.diamo.cobblemoneconomyplus.permission;

import com.diamo.cobblemoneconomyplus.CobblemonEconomyPlus;

/**
 * Permission nodes (usable with LuckPerms and other permission managers). Each node is
 * {@code cobblemon_economy_plus.<node>}. When no permission manager has an opinion, the vanilla
 * operator level in {@link #defaultLevel} applies (0 = everyone).
 */
public enum EconomyPermission {
    /** {@code /cep balance} (own balance). */
    BALANCE("balance", 0),
    /** {@code /cep balance <player>}. */
    BALANCE_OTHERS("balance.others", 2),
    /** {@code /cep pay} and {@code /pay}. */
    PAY("pay", 0),
    /** {@code /cep money get|set|add|take}. */
    MONEY("money", 2),
    /** {@code /cep shop reload|list}. */
    SHOP("shop", 2),
    /** {@code /cep shopkeeper set|clear}. */
    SHOPKEEPER("shopkeeper", 2),
    /** {@code /cep history}. */
    HISTORY("history", 2),
    /** Using the Shop Editor item on NPCs. */
    EDITOR("editor", 2);

    private final String node;
    private final int defaultLevel;

    EconomyPermission(String node, int defaultLevel) {
        this.node = node;
        this.defaultLevel = defaultLevel;
    }

    /** The node without the mod id prefix, e.g. {@code money}. */
    public String node() {
        return node;
    }

    /** Full node string, e.g. {@code cobblemon_economy_plus.money}. */
    public String fullNode() {
        return CobblemonEconomyPlus.MOD_ID + "." + node;
    }

    public int defaultLevel() {
        return defaultLevel;
    }
}
