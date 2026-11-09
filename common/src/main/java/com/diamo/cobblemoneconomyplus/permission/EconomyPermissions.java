package com.diamo.cobblemoneconomyplus.permission;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Permission checks shared by both loaders. Each loader installs a {@link Checker} that talks to its
 * permission API (NeoForge PermissionAPI, Fabric permissions API); without one, vanilla operator levels apply.
 */
public final class EconomyPermissions {
    /** Returns the permission manager's verdict, or {@code null} when it has no opinion. */
    @FunctionalInterface
    public interface Checker {
        Boolean check(ServerPlayer player, EconomyPermission permission);
    }

    private static Checker checker;

    private EconomyPermissions() {
    }

    public static void setChecker(Checker checker) {
        EconomyPermissions.checker = checker;
    }

    public static boolean has(ServerPlayer player, EconomyPermission permission) {
        if (checker != null) {
            Boolean verdict = checker.check(player, permission);
            if (verdict != null) {
                return verdict;
            }
        }
        return player.hasPermissions(permission.defaultLevel());
    }

    /**
     * Command-source check. Consoles and command blocks use the source's operator level. A player source is
     * checked against the permission manager; the one exception is a source elevated above the player's own
     * level (for example a command block running {@code execute as @p run cep money add ...}), which keeps
     * working as in vanilla.
     */
    public static boolean has(CommandSourceStack source, EconomyPermission permission) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return source.hasPermission(permission.defaultLevel());
        }
        if (has(player, permission)) {
            return true;
        }
        int level = permission.defaultLevel();
        return level > 0 && source.hasPermission(level) && !player.hasPermissions(level);
    }
}
