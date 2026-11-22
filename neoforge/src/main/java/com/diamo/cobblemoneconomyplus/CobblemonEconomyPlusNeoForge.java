package com.diamo.cobblemoneconomyplus;

import com.diamo.cobblemoneconomyplus.command.EconomyCommands;
import com.diamo.cobblemoneconomyplus.content.ShopEditorItem;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.ClientPayloadBridge;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import com.diamo.cobblemoneconomyplus.shop.ShopService;
import com.diamo.cobblemoneconomyplus.shop.Shopkeeper;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

@Mod(CobblemonEconomyPlus.MOD_ID)
public final class CobblemonEconomyPlusNeoForge {

    public CobblemonEconomyPlusNeoForge(IEventBus modEventBus) {
        CobblemonEconomyPlus.init();
        PlatformNetworking.setServerSender(PacketDistributor::sendToPlayer);

        NeoForgePermissions.install();
        NeoForge.EVENT_BUS.addListener(NeoForgePermissions::gather);
        EconomyContentNeoForge.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(this::onNpcInteraction);
        NeoForge.EVENT_BUS.addListener(this::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(this::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EconomyPayloads.PROTOCOL_VERSION);
        registrar.playToClient(EconomyPayloads.BalancePayload.TYPE, EconomyPayloads.BalancePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleBalance(payload))
        );
        registrar.playToClient(EconomyPayloads.OpenShopPayload.TYPE, EconomyPayloads.OpenShopPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleOpenShop(payload))
        );
        registrar.playToClient(EconomyPayloads.ShopEntryUpdatePayload.TYPE, EconomyPayloads.ShopEntryUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadBridge.handleEntryUpdate(payload))
        );
        registrar.playToServer(EconomyPayloads.SellActionPayload.TYPE, EconomyPayloads.SellActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ShopService.handleSale(player, payload);
                    }
                })
        );
        registrar.playToServer(EconomyPayloads.ShopActionPayload.TYPE, EconomyPayloads.ShopActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ShopService.handlePurchase(player, payload);
                    }
                })
        );
    }

    private void registerCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CobblemonEconomyPlus.onPlayerJoin(player);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.onDisconnect(player.getUUID());
            ShopEditorItem.onDisconnect(player.getUUID());
        }
    }

    private void onNpcInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (ShopEditorItem.isEditor(event.getEntity().getItemInHand(event.getHand()))) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
                ShopEditorItem.tryEdit(player, event.getTarget());
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (ShopService.tryOpen(player, event.getTarget())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private void onAttackEntity(AttackEntityEvent event) {
        // Scoreboard tags are server-side only, so the attack is cancelled on the server.
        if (event.getEntity().level().isClientSide() || !Shopkeeper.isShopkeeper(event.getTarget())) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopEditorItem.tryRemove(player, event.getTarget());
        }
        event.setCanceled(true);
    }

    private void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (Shopkeeper.blocksDamage(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        CobblemonEconomyPlus.onServerStarted(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        CobblemonEconomyPlus.onServerStopped(event.getServer());
    }
}
