package com.diamo.cobblemoneconomyplus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for loader-independent initialization. */
public final class CobblemonEconomyPlus {
    public static final String MOD_ID = "cobblemon_economy_plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private CobblemonEconomyPlus() {
    }

    public static void init() {
        LOGGER.info("{} initialized", MOD_ID);
    }
}
