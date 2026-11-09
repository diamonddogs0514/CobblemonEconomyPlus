package com.diamo.cobblemoneconomyplus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import com.diamo.cobblemoneconomyplus.command.EconomyCommands;
import com.diamo.cobblemoneconomyplus.content.ShopEditorItem;
import com.diamo.cobblemoneconomyplus.economy.EconomyService;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.diamo.cobblemoneconomyplus.shop.ShopService;

public final class CobblemonEconomyPlusFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CobblemonEconomyPlus.init();
        EconomyContentFabric.register();
        FabricPermissions.install();

        PlatformNetworking.setServerSender(ServerPlayNetworking::send);
        PayloadTypeRegistry.playS2C().register(EconomyPayloads.BalancePayload.TYPE, EconomyPayloads.BalancePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(EconomyPayloads.OpenShopPayload.TYPE, EconomyPayloads.OpenShopPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(EconomyPayloads.ShopActionPayload.TYPE, EconomyPayloads.ShopActionPayload.STREAM_CODEC);

        PayloadTypeRegistry.playC2S().register(EconomyPayloads.SellActionPayload.TYPE, EconomyPayloads.SellActionPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.ShopActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ShopService.handlePurchase(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(EconomyPayloads.SellActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> ShopService.handleSale(context.player(), payload))
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> EconomyService.sync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ShopService.onDisconnect(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STARTED.register(ShopManager::reload);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> EconomyCommands.register(dispatcher));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (ShopEditorItem.isEditor(player.getItemInHand(hand))) {
                if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    ShopEditorItem.tryEdit(serverPlayer, entity);
                }
                return InteractionResult.SUCCESS;
            }
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && ShopService.tryOpen(serverPlayer, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
