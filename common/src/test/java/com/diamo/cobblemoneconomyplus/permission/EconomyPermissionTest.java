package com.diamo.cobblemoneconomyplus.permission;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyPermissionTest {
    @Test
    void nodesAreNamespacedAndUnique() {
        HashSet<String> seen = new HashSet<>();
        for (EconomyPermission permission : EconomyPermission.values()) {
            assertEquals("cobblemon_economy_plus." + permission.node(), permission.fullNode());
            seen.add(permission.fullNode());
        }
        assertEquals(EconomyPermission.values().length, seen.size());
    }

    @Test
    void playerFacingNodesDefaultToEveryoneAndAdminNodesToOps() {
        assertEquals(0, EconomyPermission.BALANCE.defaultLevel());
        assertEquals(0, EconomyPermission.PAY.defaultLevel());
        assertEquals(2, EconomyPermission.BALANCE_OTHERS.defaultLevel());
        assertEquals(2, EconomyPermission.MONEY.defaultLevel());
        assertEquals(2, EconomyPermission.SHOP.defaultLevel());
        assertEquals(2, EconomyPermission.SHOPKEEPER.defaultLevel());
        assertEquals(2, EconomyPermission.EDITOR.defaultLevel());
        assertEquals(2, EconomyPermission.HISTORY.defaultLevel());
    }

    @Test
    void theDocumentedNodeNamesAreStable() {
        // These strings are part of the public contract (LuckPerms configs, README): do not rename casually.
        assertEquals("cobblemon_economy_plus.balance", EconomyPermission.BALANCE.fullNode());
        assertEquals("cobblemon_economy_plus.balance.others", EconomyPermission.BALANCE_OTHERS.fullNode());
        assertEquals("cobblemon_economy_plus.pay", EconomyPermission.PAY.fullNode());
        assertEquals("cobblemon_economy_plus.money", EconomyPermission.MONEY.fullNode());
        assertEquals("cobblemon_economy_plus.shop", EconomyPermission.SHOP.fullNode());
        assertEquals("cobblemon_economy_plus.shopkeeper", EconomyPermission.SHOPKEEPER.fullNode());
        assertEquals("cobblemon_economy_plus.editor", EconomyPermission.EDITOR.fullNode());
        assertEquals("cobblemon_economy_plus.history", EconomyPermission.HISTORY.fullNode());
    }
}
