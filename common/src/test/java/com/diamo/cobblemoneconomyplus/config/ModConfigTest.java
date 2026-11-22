package com.diamo.cobblemoneconomyplus.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModConfigTest {
    @Test
    void defaultsSwitchPurchaseLimitsOn() {
        List<String> errors = new ArrayList<>();
        ModConfig.Settings settings = ModConfig.read(ModConfig.DEFAULT_JSON, errors, new ArrayList<>());
        assertTrue(errors.isEmpty());
        assertTrue(settings.purchaseLimitsEnabled());
        assertTrue(ModConfig.read("{}", errors, new ArrayList<>()).purchaseLimitsEnabled());
    }

    @Test
    void purchaseLimitsCanBeSwitchedOff() {
        assertFalse(ModConfig.read("{ \"purchase_limits_enabled\": false }", new ArrayList<>(), new ArrayList<>())
                .purchaseLimitsEnabled());
    }

    @Test
    void badValuesWarnAndBrokenFilesAreErrors() {
        List<String> warnings = new ArrayList<>();
        assertTrue(ModConfig.read("{ \"purchase_limits_enabled\": \"no\" }", new ArrayList<>(), warnings)
                .purchaseLimitsEnabled());
        assertEquals(1, warnings.size());

        List<String> errors = new ArrayList<>();
        assertNull(ModConfig.read("{ \"purchase_limits_enabled\": false", errors, new ArrayList<>()));
        assertNull(ModConfig.read("[]", errors, new ArrayList<>()));
        assertEquals(2, errors.size(), errors::toString);
        assertTrue(errors.get(0).startsWith("config.json: invalid JSON"), errors.get(0));
    }
}
