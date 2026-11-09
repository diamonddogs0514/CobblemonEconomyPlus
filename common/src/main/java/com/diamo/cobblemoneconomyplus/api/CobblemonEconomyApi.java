package com.diamo.cobblemoneconomyplus.api;

import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Public, server-side API for other mods. Balances belong to player UUIDs, so online and offline players are
 * handled the same way, and every change is pushed to the player's client when they are online.
 *
 * <p>Call these from the server thread only (event handlers, commands, scheduled server tasks). All amounts
 * are whole coins. Methods never throw for invalid amounts; they report failure through their return value.</p>
 *
 * <p>Every change is recorded in the transaction log ({@code logs/cobblemon_economy_plus/transactions.log}).
 * The overloads taking a {@code reason} let you label your changes there (for example {@code "mymod:quest_reward"});
 * the others log the reason {@code "api"}.</p>
 *
 * <p>For command-based integration (command blocks, datapack functions) see {@code /cep money} and {@code /cep pay}.</p>
 */
public final class CobblemonEconomyApi {
    private static final String DEFAULT_REASON = "api";

    private CobblemonEconomyApi() {
    }

    /** The player's balance in coins (0 if they have none). */
    public static long getBalance(MinecraftServer server, UUID playerId) {
        return EconomyService.getBalance(server, playerId);
    }

    /** Whether the player has at least {@code amount} coins. */
    public static boolean has(MinecraftServer server, UUID playerId, long amount) {
        return amount <= 0L || EconomyService.getBalance(server, playerId) >= amount;
    }

    /** Sets the balance (negative values become 0). Returns the new balance. */
    public static long setBalance(MinecraftServer server, UUID playerId, long amount) {
        return setBalance(server, playerId, amount, DEFAULT_REASON);
    }

    public static long setBalance(MinecraftServer server, UUID playerId, long amount, String reason) {
        long result = EconomyService.setBalance(server, playerId, amount, reason, "");
        EconomyService.syncIfOnline(server, playerId);
        return result;
    }

    /** Adds coins (saturating at {@link Long#MAX_VALUE}). A non-positive amount changes nothing. Returns the new balance. */
    public static long deposit(MinecraftServer server, UUID playerId, long amount) {
        return deposit(server, playerId, amount, DEFAULT_REASON);
    }

    public static long deposit(MinecraftServer server, UUID playerId, long amount, String reason) {
        long result = EconomyService.deposit(server, playerId, amount, reason, "");
        EconomyService.syncIfOnline(server, playerId);
        return result;
    }

    /** Removes coins. Returns false and changes nothing if the player cannot afford {@code amount} (or it is negative). */
    public static boolean withdraw(MinecraftServer server, UUID playerId, long amount) {
        return withdraw(server, playerId, amount, DEFAULT_REASON);
    }

    public static boolean withdraw(MinecraftServer server, UUID playerId, long amount, String reason) {
        boolean success = EconomyService.withdraw(server, playerId, amount, reason, "");
        if (success) {
            EconomyService.syncIfOnline(server, playerId);
        }
        return success;
    }

    /** Moves coins between two players atomically. Returns false and changes nothing if it cannot be done. */
    public static boolean transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        return transfer(server, from, to, amount, DEFAULT_REASON);
    }

    public static boolean transfer(MinecraftServer server, UUID from, UUID to, long amount, String reason) {
        boolean success = EconomyService.transfer(server, from, to, amount, reason);
        if (success) {
            EconomyService.syncIfOnline(server, from);
            EconomyService.syncIfOnline(server, to);
        }
        return success;
    }

    /** Formats an amount with thousands separators, as shown in the mod's UI (for example {@code 12,500}). */
    public static String format(long amount) {
        return EconomyFormat.money(amount);
    }
}
