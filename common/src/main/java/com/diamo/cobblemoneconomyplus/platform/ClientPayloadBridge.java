package com.diamo.cobblemoneconomyplus.platform;

import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Keeps loader-common packet registration free of client-only Minecraft classes.
 * Client entry points install the handlers during their own initialization.
 */
public final class ClientPayloadBridge {
    private static Consumer<EconomyPayloads.BalancePayload> balanceHandler;
    private static Consumer<EconomyPayloads.OpenShopPayload> openShopHandler;
    private static Consumer<EconomyPayloads.ShopEntryUpdatePayload> entryUpdateHandler;
    private static Consumer<EconomyPayloads.AdminOpenPayload> adminOpenHandler;
    private static Consumer<EconomyPayloads.AdminStatusPayload> adminStatusHandler;

    private static Consumer<EconomyPayloads.TrainerCardPayload> trainerCardHandler;

    private ClientPayloadBridge() {
    }

    public static void setTrainerCardHandler(Consumer<EconomyPayloads.TrainerCardPayload> handler) {
        trainerCardHandler = Objects.requireNonNull(handler, "handler");
    }

    private static Consumer<EconomyPayloads.DailyLoginPayload> dailyLoginHandler;

    public static void setDailyLoginHandler(Consumer<EconomyPayloads.DailyLoginPayload> handler) {
        dailyLoginHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handleDailyLogin(EconomyPayloads.DailyLoginPayload payload) {
        if (dailyLoginHandler != null) {
            dailyLoginHandler.accept(payload);
        }
    }

    public static void handleTrainerCard(EconomyPayloads.TrainerCardPayload payload) {
        if (trainerCardHandler != null) {
            trainerCardHandler.accept(payload);
        }
    }

    public static void setBalanceHandler(Consumer<EconomyPayloads.BalancePayload> handler) {
        balanceHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setOpenShopHandler(Consumer<EconomyPayloads.OpenShopPayload> handler) {
        openShopHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void setAdminHandlers(Consumer<EconomyPayloads.AdminOpenPayload> open,
                                        Consumer<EconomyPayloads.AdminStatusPayload> status) {
        adminOpenHandler = Objects.requireNonNull(open, "open");
        adminStatusHandler = Objects.requireNonNull(status, "status");
    }

    public static void handleAdminOpen(EconomyPayloads.AdminOpenPayload payload) {
        if (adminOpenHandler != null) {
            adminOpenHandler.accept(payload);
        }
    }

    public static void handleAdminStatus(EconomyPayloads.AdminStatusPayload payload) {
        if (adminStatusHandler != null) {
            adminStatusHandler.accept(payload);
        }
    }

    public static void setEntryUpdateHandler(Consumer<EconomyPayloads.ShopEntryUpdatePayload> handler) {
        entryUpdateHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handleEntryUpdate(EconomyPayloads.ShopEntryUpdatePayload payload) {
        if (entryUpdateHandler != null) {
            entryUpdateHandler.accept(payload);
        }
    }

    public static void handleBalance(EconomyPayloads.BalancePayload payload) {
        if (balanceHandler != null) {
            balanceHandler.accept(payload);
        }
    }

    public static void handleOpenShop(EconomyPayloads.OpenShopPayload payload) {
        if (openShopHandler != null) {
            openShopHandler.accept(payload);
        }
    }
}
