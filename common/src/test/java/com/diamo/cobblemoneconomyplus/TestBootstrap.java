package com.diamo.cobblemoneconomyplus;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Initialises Minecraft's registries once so tests can create items and item stacks. */
public final class TestBootstrap {
    private static boolean done;

    private TestBootstrap() {
    }

    public static synchronized void init() {
        if (!done) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            done = true;
        }
    }
}
