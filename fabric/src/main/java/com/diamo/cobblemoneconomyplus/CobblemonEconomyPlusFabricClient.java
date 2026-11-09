package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.client.ClientEconomy;
import com.diamo.cobblemoneconomyplus.client.CurrencyHud;
import com.diamo.cobblemoneconomyplus.client.HudKeys;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.ClientPayloadBridge;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class CobblemonEconomyPlusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlatformNetworking.setShopActionSender(ClientPlayNetworking::send);
        PlatformNetworking.setSellActionSender(ClientPlayNetworking::send);
        ClientPayloadBridge.setBalanceHandler(ClientEconomy::handleBalance);
        ClientPayloadBridge.setOpenShopHandler(ClientEconomy::handleOpenShop);
        ClientPayloadBridge.setOpenCardHandler(ClientEconomy::openTrainerCard);
        KeyBindingHelper.registerKeyBinding(HudKeys.SHOW_BALANCE);

        ClientPlayNetworking.registerGlobalReceiver(EconomyPayloads.BalancePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPayloadBridge.handleBalance(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(EconomyPayloads.OpenShopPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientPayloadBridge.handleOpenShop(payload))
        );
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> CurrencyHud.render(graphics));
    }
}
