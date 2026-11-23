package com.diamo.cobblemoneconomyplus.api;

/** What a {@link RewardEvent} pays for. The ids are used in the transaction log as {@code reward:<id>}. */
public enum RewardType {
    /** Winning a battle against wild Pokémon (alpha or not: see {@link RewardEvent#alpha()}). */
    WILD_BATTLE("wild_battle"),
    /** Winning a battle against a Cobblemon NPC trainer. */
    TRAINER_BATTLE("trainer_battle"),
    /** Capturing a Pokémon (alpha or not: see {@link RewardEvent#alpha()}). */
    CAPTURE("capture"),
    /** Reaching a Pokédex milestone (number of species caught: see {@link RewardEvent#milestone()}). */
    POKEDEX_MILESTONE("pokedex_milestone"),
    /**
     * The first login of the day ({@link RewardEvent#milestone()} is which login day it is). Changing the amount
     * changes the coins; cancelling it also withholds the day's items. Since API version 3.
     */
    DAILY_LOGIN("daily_login");

    private final String id;

    RewardType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
