package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.client.ClientEconomy;
import com.diamo.cobblemoneconomyplus.client.CurrencyHud;
import com.diamo.cobblemoneconomyplus.client.HudKeys;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import com.diamo.cobblemoneconomyplus.platform.ClientPayloadBridge;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only bridge for NeoForge networking and HUD rendering. */
public final class CobblemonEconomyPlusNeoForgeClient {
    private CobblemonEconomyPlusNeoForgeClient() {
    }

    @EventBusSubscriber(
            modid = CobblemonEconomyPlus.MOD_ID,
            value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD
    )
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                PlatformNetworking.setShopActionSender(PacketDistributor::sendToServer);
                PlatformNetworking.setSellActionSender(PacketDistributor::sendToServer);
                ClientPayloadBridge.setBalanceHandler(ClientEconomy::handleBalance);
                ClientPayloadBridge.setOpenShopHandler(ClientEconomy::handleOpenShop);
                ClientPayloadBridge.setOpenCardHandler(ClientEconomy::openTrainerCard);
            });
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(HudKeys.SHOW_BALANCE);
        }
    }

    @EventBusSubscriber(modid = CobblemonEconomyPlus.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiEvent.Post event) {
            CurrencyHud.render(event.getGuiGraphics());
        }
    }
}
