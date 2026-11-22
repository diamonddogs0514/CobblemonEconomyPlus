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

    private static Runnable openCardHandler;

    private ClientPayloadBridge() {
    }

    public static void setOpenCardHandler(Runnable handler) {
        openCardHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void openCard() {
        if (openCardHandler != null) {
            openCardHandler.run();
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
