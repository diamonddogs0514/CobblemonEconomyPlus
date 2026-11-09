package com.diamo.cobblemoneconomyplus.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colour/motif definitions for {@link TrainerBoutiqueScreen}. Theme ids mirror
 * {@code com.diamo.cobblemoneconomyplus.shop.ShopThemes} on the server; an unknown id falls back to the Poke Ball.
 */
final class BoutiqueTheme {
    /** Shape drawn in the header badge (and the small "All" tab icon). */
    enum Emblem { BALL, DISC, BERRY, STONE, LOG, GRASS, ORE, IRON }

    /** Extra decoration on the header bar. */
    enum Pattern { NONE, PLANKS, RIVETS, GRASS }

    static final String DEFAULT_ID = "pokeball";
    private static final Map<String, BoutiqueTheme> THEMES = new LinkedHashMap<>();

    final String id;
    final Emblem emblem;
    /** Poke Ball lid marks: 0 none, 1 great (red), 2 ultra (yellow), 3 master (pink). */
    final int ballMark;
    final Pattern pattern;

    final int outline;
    final int accent;
    final int accentDark;
    final int accentLight;
    final int body;
    final int bodyDark;
    final int bodyLine;
    final int ink;
    final int inkMuted;
    final int pane;
    final int paneLight;
    final int paneDark;
    final int selected;
    final int headerSub;
    final int speckleA;
    final int speckleB;

    private BoutiqueTheme(String id, Emblem emblem, int ballMark, Pattern pattern, int[] c) {
        this.id = id;
        this.emblem = emblem;
        this.ballMark = ballMark;
        this.pattern = pattern;
        this.outline = c[0];
        this.accent = c[1];
        this.accentDark = c[2];
        this.accentLight = c[3];
        this.body = c[4];
        this.bodyDark = c[5];
        this.bodyLine = c[6];
        this.ink = c[7];
        this.inkMuted = c[8];
        this.pane = c[9];
        this.paneLight = c[10];
        this.paneDark = c[11];
        this.selected = c[12];
        this.headerSub = c[13];
        this.speckleA = c[14];
        this.speckleB = c[15];
    }

    static BoutiqueTheme byId(String id) {
        BoutiqueTheme theme = id == null ? null : THEMES.get(id);
        return theme != null ? theme : THEMES.get(DEFAULT_ID);
    }

    private static void register(String id, Emblem emblem, int ballMark, Pattern pattern, int... colors) {
        THEMES.put(id, new BoutiqueTheme(id, emblem, ballMark, pattern, colors));
    }

    // Colour order: outline, accent, accentDark, accentLight, body, bodyDark, bodyLine, ink, inkMuted,
    //               pane, paneLight, paneDark, selected, headerSub, speckleA, speckleB (0 = none)
    static {
        register("pokeball", Emblem.BALL, 0, Pattern.NONE,
                0xFF16181D, 0xFFD8393B, 0xFF9E2428, 0xFFF06A66, 0xFFF1EDE2, 0xFFE3DECF, 0xFFC9C3B0,
                0xFF2A2E38, 0xFF7A7C85, 0xFF2A3140, 0xFF3A4458, 0xFF1B212C, 0xFFFBE3E0, 0xFFFFD6D2, 0, 0);
        register("greatball", Emblem.BALL, 1, Pattern.NONE,
                0xFF121A2B, 0xFF2F6FD0, 0xFF1F4A96, 0xFF6C9CE8, 0xFFE8EEF6, 0xFFD5DEEC, 0xFFAEBBD1,
                0xFF22304A, 0xFF6F7C93, 0xFF1F2C45, 0xFF2D3F61, 0xFF141D30, 0xFFD9E6FA, 0xFFD2E2FF, 0, 0);
        register("ultraball", Emblem.BALL, 2, Pattern.NONE,
                0xFF111111, 0xFF34343D, 0xFF1B1B21, 0xFF6A6A78, 0xFFF3EFDC, 0xFFE6E0C4, 0xFFCFC7A3,
                0xFF2B2B33, 0xFF7B7762, 0xFF2B2B33, 0xFF3C3C47, 0xFF1A1A20, 0xFFFFF1B8, 0xFFFFE680, 0, 0);
        register("masterball", Emblem.BALL, 3, Pattern.NONE,
                0xFF1A1226, 0xFF7A3FB8, 0xFF53257F, 0xFFB07AE6, 0xFFEFE8F6, 0xFFE0D4EE, 0xFFBFAFD4,
                0xFF2E2340, 0xFF7E7192, 0xFF2E2340, 0xFF41325A, 0xFF1E1630, 0xFFEBD7FA, 0xFFE7D0FF, 0, 0);
        register("tm", Emblem.DISC, 0, Pattern.NONE,
                0xFF0F1F22, 0xFF1F9E8A, 0xFF136B5E, 0xFF5CD6C0, 0xFFE6F2F0, 0xFFD2E5E2, 0xFFA9C8C3,
                0xFF1E3338, 0xFF6D8A88, 0xFF1E3338, 0xFF2D4A50, 0xFF13222A, 0xFFCCEFE8, 0xFFC8FFF3, 0, 0);
        register("berry", Emblem.BERRY, 0, Pattern.NONE,
                0xFF1F2A14, 0xFF5B9E3F, 0xFF3C7228, 0xFF8BCB68, 0xFFF2F0DC, 0xFFE4E1C4, 0xFFC8C49E,
                0xFF3A2A3E, 0xFF857A6A, 0xFF3A2A3E, 0xFF52405A, 0xFF261A2A, 0xFFE3F2C9, 0xFFE3FFC6, 0xFFE8C34A, 0xFFD8393B);
        register("stone", Emblem.STONE, 0, Pattern.NONE,
                0xFF2B2B2B, 0xFF7D7D7D, 0xFF555555, 0xFFA8A8A8, 0xFFD9D9D9, 0xFFC6C6C6, 0xFF9C9C9C,
                0xFF2B2B2B, 0xFF6B6B6B, 0xFF3B3B3B, 0xFF4F4F4F, 0xFF262626, 0xFFEDEDED, 0xFFE6E6E6, 0xFF9A9A9A, 0xFF626262);
        register("wood", Emblem.LOG, 0, Pattern.PLANKS,
                0xFF2E1D0F, 0xFF9C6B3A, 0xFF6B4423, 0xFFC69A62, 0xFFF0E2C6, 0xFFE2CFA8, 0xFFB79A6B,
                0xFF3B2713, 0xFF7F6A4B, 0xFF4A3220, 0xFF62452D, 0xFF31200F, 0xFFF6D9A5, 0xFFF7DDB5, 0xFFB88650, 0xFF7A5230);
        register("dirt", Emblem.GRASS, 0, Pattern.GRASS,
                0xFF2A1A0C, 0xFF8B5A2B, 0xFF5E3B1B, 0xFF5FA23A, 0xFFE9DCC3, 0xFFD8C8A8, 0xFFB39F7A,
                0xFF3B2812, 0xFF7F6B4D, 0xFF4A3320, 0xFF63462C, 0xFF2F1F12, 0xFFF1E0B8, 0xFFF0DDB8, 0xFFA57A45, 0xFF6E4522);
        register("ore", Emblem.ORE, 0, Pattern.NONE,
                0xFF14141A, 0xFF4A4A52, 0xFF303036, 0xFF6E6E78, 0xFFD6D8DC, 0xFFC5C8CE, 0xFFA4A8B0,
                0xFF22232A, 0xFF6B6D75, 0xFF26262C, 0xFF3A3A44, 0xFF17171C, 0xFFDDF3F5, 0xFFC9F6F8, 0xFF4FE0E8, 0xFFF2C94C);
        register("iron", Emblem.IRON, 0, Pattern.RIVETS,
                0xFF2A2D33, 0xFF8E98A4, 0xFF646E7A, 0xFFD6DCE3, 0xFFECEFF2, 0xFFDDE2E7, 0xFFB6BEC7,
                0xFF2A2D33, 0xFF6F7782, 0xFF3A424D, 0xFF4D5764, 0xFF262C34, 0xFFDDE8F3, 0xFFEEF2F6, 0, 0);
    }
}
