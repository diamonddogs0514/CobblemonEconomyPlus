package com.diamo.cobblemoneconomyplus.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client key mappings; each loader registers {@link #SHOW_BALANCE} with its own API. */
public final class HudKeys {
    /** Hold to show the balance card. Shift + press cycles the HUD display mode. */
    public static final KeyMapping SHOW_BALANCE = new KeyMapping(
            "key.cobblemon_economy_plus.show_balance",
            GLFW.GLFW_KEY_B,
            "key.categories.cobblemon_economy_plus"
    );

    private HudKeys() {
    }
}
