package com.diamo.cobblemoneconomyplus.client;

import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.shop.LocalizedText;
import net.minecraft.client.Minecraft;

/** Client-only balance cache and entry point for shop-opening packets. */
public final class ClientEconomy {
    private static long balance;
    private static boolean synced;
    /** Cobblemon Battle Plus Battle Points, or -1 when the server does not have Battle Plus. */
    private static int battlePoints = -1;

    private ClientEconomy() {
    }

    public static long balance() {
        return balance;
    }

    /** The player's Battle Points, or -1 when the server does not have Cobblemon Battle Plus. */
    public static int battlePoints() {
        return battlePoints;
    }

    /** Called when the local player changes (new world / respawn): the next balance is not a "change". */
    static void markUnsynced() {
        synced = false;
        battlePoints = -1;
    }

    private static void applyBalance(long newBalance) {
        long old = balance;
        balance = newBalance;
        if (!synced) {
            synced = true;
            CurrencyHud.onSync();
        } else if (old != newBalance) {
            CurrencyHud.onChange(newBalance - old);
        }
    }

    public static void handleBalance(EconomyPayloads.BalancePayload payload) {
        battlePoints = payload.battlePoints();
        applyBalance(payload.balance());
    }

    public static void openTrainerCard(EconomyPayloads.TrainerCardPayload stats) {
        Minecraft.getInstance().setScreen(new TrainerCardScreen(stats));
    }

    /** The login bonus calendar waiting for the world to finish loading (or for another screen to close). */
    private static EconomyPayloads.DailyLoginPayload pendingDailyLogin;

    public static void handleDailyLogin(EconomyPayloads.DailyLoginPayload payload) {
        pendingDailyLogin = payload;
        openPendingDailyLogin();
    }

    /** Called every client tick by the loaders. */
    public static void clientTick() {
        if (pendingDailyLogin != null) {
            openPendingDailyLogin();
        }
    }

    private static void openPendingDailyLogin() {
        Minecraft minecraft = Minecraft.getInstance();
        // Sent at login, while the loading screen is still up: open it once nothing else is on screen.
        if (minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            minecraft.setScreen(new DailyLoginScreen(pendingDailyLogin));
            pendingDailyLogin = null;
        }
    }

    /** Updates the remaining counts of a limited entry in the open shop screen (ignored when another screen is open). */
    public static void handleEntryUpdate(EconomyPayloads.ShopEntryUpdatePayload payload) {
        if (Minecraft.getInstance().screen instanceof TrainerBoutiqueScreen screen) {
            screen.applyEntryUpdate(payload);
        }
    }

    /** Opens the admin screen, or refreshes it in place when it is already open. */
    public static void handleAdminOpen(EconomyPayloads.AdminOpenPayload payload) {
        if (Minecraft.getInstance().screen instanceof AdminScreen screen) {
            screen.apply(payload);
        } else {
            Minecraft.getInstance().setScreen(new AdminScreen(payload));
        }
    }

    /** Shows the result of an admin edit in the admin screen, or in chat when the screen was closed. */
    public static void handleAdminStatus(EconomyPayloads.AdminStatusPayload payload) {
        if (Minecraft.getInstance().screen instanceof AdminScreen screen) {
            screen.showStatus(payload);
        } else if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    payload.message(), false);
        }
    }

    public static void handleOpenShop(EconomyPayloads.OpenShopPayload payload) {
        applyBalance(payload.balance());
        // Names/subtitles may be per-language objects; pick the viewer's language once, here.
        String language = Minecraft.getInstance().options.languageCode;
        payload = new EconomyPayloads.OpenShopPayload(
                payload.shopId(),
                LocalizedText.resolve(payload.shopName(), language),
                payload.theme(),
                // A subtitle missing for this language falls back to the theme's tagline (in this language).
                LocalizedText.resolveOwnLanguage(payload.subtitle(), language),
                payload.balance(),
                payload.entries(),
                payload.sellEntries());
        // The server re-sends the shop after a content mismatch; swap quietly instead of replaying the open sound.
        boolean refresh = Minecraft.getInstance().screen instanceof TrainerBoutiqueScreen;
        Minecraft.getInstance().setScreen(new TrainerBoutiqueScreen(payload, refresh));
    }
}
