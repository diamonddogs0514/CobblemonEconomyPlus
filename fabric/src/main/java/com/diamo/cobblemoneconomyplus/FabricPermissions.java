package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.permission.EconomyPermission;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Bridges to the Fabric permissions API (the API LuckPerms implements on Fabric). It is called by reflection so
 * the mod neither requires nor bundles it: when it is not installed, vanilla operator levels apply.
 */
final class FabricPermissions {
    private static final String API_CLASS = "me.lucko.fabric.api.permissions.v0.Permissions";

    private FabricPermissions() {
    }

    static void install() {
        Method check;
        try {
            check = Class.forName(API_CLASS).getMethod("check", Entity.class, String.class, int.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CobblemonEconomyPlus.LOGGER.info("Fabric permissions API not found; using vanilla operator levels");
            return;
        }

        CobblemonEconomyPlus.LOGGER.info("Using the Fabric permissions API for permission checks");
        EconomyPermissions.setChecker((ServerPlayer player, EconomyPermission permission) -> {
            try {
                return (Boolean) check.invoke(null, player, permission.fullNode(), permission.defaultLevel());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return null;
            }
        });
    }
}
