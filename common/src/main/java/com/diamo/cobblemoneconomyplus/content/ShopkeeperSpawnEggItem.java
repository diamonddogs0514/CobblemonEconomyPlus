package com.diamo.cobblemoneconomyplus.content;

import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.diamo.cobblemoneconomyplus.shop.Shopkeeper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Spawns an already configured, protected, non-despawning villager shopkeeper. Extending
 * SpawnEggItem preserves vanilla spawn-egg colours and rendering while avoiding
 * a second entity renderer on Fabric and NeoForge.
 */
public final class ShopkeeperSpawnEggItem extends SpawnEggItem {
    private static final int PRIMARY_COLOR = 0x1C4D8C;
    private static final int SECONDARY_COLOR = 0x56D4CC;

    public ShopkeeperSpawnEggItem(Item.Properties properties) {
        super(EntityType.VILLAGER, PRIMARY_COLOR, SECONDARY_COLOR, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Villager villager = EntityType.VILLAGER.spawn(
                serverLevel,
                this::configureShopkeeper,
                spawnPos,
                MobSpawnType.SPAWN_EGG,
                true,
                false
        );
        if (villager == null) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Avoids creating an unconfigured villager when the egg is used on water. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shopkeeper_spawn_egg.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private void configureShopkeeper(Villager villager) {
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        villager.setCustomName(Component.translatable("entity.cobblemon_economy_plus.shopkeeper"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        Shopkeeper.setShop(villager, ShopManager.DEFAULT_SHOP_ID);
    }
}
