package com.diamo.cobblemoneconomyplus.economy;

import java.util.Locale;

public final class EconomyFormat {
    private EconomyFormat() {
    }

    public static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", Math.max(0L, amount));
    }
}
