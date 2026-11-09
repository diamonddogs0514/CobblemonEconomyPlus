package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.permission.EconomyPermission;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermissions;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers this mod's permission nodes with NeoForge's PermissionAPI (which LuckPerms and other permission
 * managers plug into). The default for each node is the vanilla operator level from {@link EconomyPermission}.
 */
final class NeoForgePermissions {
    private static final Map<EconomyPermission, PermissionNode<Boolean>> NODES = new EnumMap<>(EconomyPermission.class);

    static {
        for (EconomyPermission permission : EconomyPermission.values()) {
            NODES.put(permission, new PermissionNode<>(
                    CobblemonEconomyPlus.MOD_ID,
                    permission.node(),
                    PermissionTypes.BOOLEAN,
                    (player, playerId, context) -> player != null && player.hasPermissions(permission.defaultLevel())
            ));
        }
    }

    private NeoForgePermissions() {
    }

    static void install() {
        EconomyPermissions.setChecker((player, permission) -> PermissionAPI.getPermission(player, NODES.get(permission)));
    }

    static void gather(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode<?>[0]));
    }
}
