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

    public static void sendShopAction(EconomyPayloads.ShopActionPayload payload) {
        if (shopActionSender != null) {
            shopActionSender.accept(payload);
        }
    }
}
