package com.diamo.cobblemoneconomyplus.economy;

import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server-side operations for balances. Every change is written to the {@link TransactionLog} with a
 * {@code source} describing who caused it; the overloads without a source log {@link #UNSPECIFIED_SOURCE}.
 */
public final class EconomyService {
    public static final String UNSPECIFIED_SOURCE = "unspecified";

    private EconomyService() {
    }

    public static long getBalance(ServerPlayer player) {
        return getBalance(player.server, player.getUUID());
    }

    public static long getBalance(ServerPlayer player, UUID playerId) {
        return getBalance(player.server, playerId);
    }

    public static long getBalance(MinecraftServer server, UUID playerId) {
        return EconomySavedData.get(server).balance(playerId);
    }

    // ------------------------------------------------------------------ set

    public static long setBalance(ServerPlayer player, UUID playerId, long amount) {
        return setBalance(player.server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static long setBalance(MinecraftServer server, UUID playerId, long amount) {
        return setBalance(server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static long setBalance(MinecraftServer server, UUID playerId, long amount, String source, String detail) {
        EconomySavedData data = EconomySavedData.get(server);
        long before = data.balance(playerId);
        data.setBalance(playerId, amount);
        long after = data.balance(playerId);
        TransactionLog.record(server, TransactionLog.Type.SET, playerId, after - before, after, source, detail);
        return after;
    }

    // ------------------------------------------------------------------ deposit

    public static long deposit(ServerPlayer player, UUID playerId, long amount) {
        return deposit(player.server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static long deposit(MinecraftServer server, UUID playerId, long amount) {
        return deposit(server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static long deposit(MinecraftServer server, UUID playerId, long amount, String source, String detail) {
        EconomySavedData data = EconomySavedData.get(server);
        long before = data.balance(playerId);
        long after = data.deposit(playerId, amount);
        if (after != before) {
            TransactionLog.record(server, TransactionLog.Type.DEPOSIT, playerId, after - before, after, source, detail);
        }
        return after;
    }

    // ------------------------------------------------------------------ withdraw

    public static boolean withdraw(ServerPlayer player, UUID playerId, long amount) {
        return withdraw(player.server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static boolean withdraw(MinecraftServer server, UUID playerId, long amount) {
        return withdraw(server, playerId, amount, UNSPECIFIED_SOURCE, "");
    }

    public static boolean withdraw(MinecraftServer server, UUID playerId, long amount, String source, String detail) {
        EconomySavedData data = EconomySavedData.get(server);
        if (!data.withdraw(playerId, amount)) {
            return false;
        }
        if (amount > 0L) {
            TransactionLog.record(server, TransactionLog.Type.WITHDRAW, playerId, -amount, data.balance(playerId), source, detail);
        }
        return true;
    }

    // ------------------------------------------------------------------ transfer

    /**
     * Moves {@code amount} from one balance to another atomically. Returns false (and changes nothing) when the
     * amount is not positive, the sender cannot afford it, both ids are the same, or the receiver's balance would overflow.
     */
    public static boolean transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        return transfer(server, from, to, amount, UNSPECIFIED_SOURCE);
    }

    public static boolean transfer(MinecraftServer server, UUID from, UUID to, long amount, String source) {
        if (amount <= 0L || from.equals(to)) {
            return false;
        }
        EconomySavedData data = EconomySavedData.get(server);
        if (data.balance(from) < amount || Long.MAX_VALUE - data.balance(to) < amount) {
            return false;
        }
        data.withdraw(from, amount);
        data.deposit(to, amount);
        TransactionLog.record(server, TransactionLog.Type.TRANSFER_OUT, from, -amount, data.balance(from), source, "to=" + to);
        TransactionLog.record(server, TransactionLog.Type.TRANSFER_IN, to, amount, data.balance(to), source, "from=" + from);
        return true;
    }

    /** Pushes the current balance to the player if they are online (no-op for offline players). */
    public static void syncIfOnline(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PlatformNetworking.sendToPlayer(player, new EconomyPayloads.BalancePayload(getBalance(player)));
    }
}
