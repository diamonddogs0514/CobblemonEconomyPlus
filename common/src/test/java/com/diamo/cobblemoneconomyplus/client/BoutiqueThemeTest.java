package com.diamo.cobblemoneconomyplus.client;

import com.diamo.cobblemoneconomyplus.shop.ShopThemes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Keeps the server's theme ids and the client's theme definitions in step. */
class BoutiqueThemeTest {
    @Test
    void everyServerThemeIdHasAClientDefinition() {
        for (String id : ShopThemes.IDS) {
            assertEquals(id, BoutiqueTheme.byId(id).id, "missing client theme for '" + id + "'");
        }
    }

    @Test
    void unknownOrMissingIdsFallBackToTheDefault() {
        assertSame(BoutiqueTheme.byId(ShopThemes.DEFAULT), BoutiqueTheme.byId("nonsense"));
        assertSame(BoutiqueTheme.byId(ShopThemes.DEFAULT), BoutiqueTheme.byId(null));
    }
}
