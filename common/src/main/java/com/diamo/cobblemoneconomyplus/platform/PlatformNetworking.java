package com.diamo.cobblemoneconomyplus.platform;

import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Small loader bridge; packet definitions and business logic remain in common. */
public final class PlatformNetworking {
    private static BiConsumer<ServerPlayer, CustomPacketPayload> serverSender;
    private static Consumer<EconomyPayloads.ShopActionPayload> shopActionSender;

    private PlatformNetworking() {
    }

    public static void setServerSender(BiConsumer<ServerPlayer, CustomPacketPayload> sender) {
        serverSender = Objects.requireNonNull(sender, "sender");
    }

    private static Consumer<EconomyPayloads.SellActionPayload> sellActionSender;

    public static void setSellActionSender(Consumer<EconomyPayloads.SellActionPayload> sender) {
        sellActionSender = Objects.requireNonNull(sender, "sender");
    }

    public static void sendSellAction(EconomyPayloads.SellActionPayload payload) {
        if (sellActionSender != null) {
            sellActionSender.accept(payload);
        }
    }

    public static void setShopActionSender(Consumer<EconomyPayloads.ShopActionPayload> sender) {
        shopActionSender = Objects.requireNonNull(sender, "sender");
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (serverSender != null) {
            serverSender.accept(player, payload);
        }
    }

    private static Consumer<CustomPacketPayload> clientSender;

    /** Sends any client-to-server payload (set by the client entry points). */
    public static void setClientSender(Consumer<CustomPacketPayload> sender) {
        clientSender = Objects.requireNonNull(sender, "sender");
    }

    public static void sendAdminSaveShop(EconomyPayloads.AdminSaveShopPayload payload) {
        sendToServer(payload);
    }

    public static void sendAdminDeleteShop(EconomyPayloads.AdminDeleteShopPayload payload) {
        sendToServer(payload);
    }

    public static void sendAdminItemValue(EconomyPayloads.AdminItemValuePayload payload) {
        sendToServer(payload);
    }

    private static void sendToServer(CustomPacketPayload payload) {
        if (clientSender != null) {
            clientSender.accept(payload);
        }
    }

    public static void sendShopAction(EconomyPayloads.ShopActionPayload payload) {
        if (shopActionSender != null) {
            shopActionSender.accept(payload);
        }
    }
}
