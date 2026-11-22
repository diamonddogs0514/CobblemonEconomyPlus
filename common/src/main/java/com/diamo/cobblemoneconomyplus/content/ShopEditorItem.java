package com.diamo.cobblemoneconomyplus.content;

import com.diamo.cobblemoneconomyplus.admin.AdminService;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermission;
import com.diamo.cobblemoneconomyplus.permission.EconomyPermissions;
import com.diamo.cobblemoneconomyplus.shop.LocalizedText;
import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopManager;
import com.diamo.cobblemoneconomyplus.shop.ShopThemes;
import com.diamo.cobblemoneconomyplus.shop.Shopkeeper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator tool for configuring a shopkeeper NPC.
 *
 * <p>Three modes, cycled by right-clicking in the air: <b>Shop</b> mode (default) cycles which shop the NPC
 * sells, <b>Theme</b> mode cycles the shop GUI's look (right-click an NPC to go forward, Shift + right-click to go
 * back), and <b>Admin</b> mode opens the admin screen: on the NPC's shop when an NPC is right-clicked, or with
 * Shift + right-click in the air. Shift + left-click a shopkeeper twice removes it.</p>
 */
public final class ShopEditorItem extends Item {
    /** Before 1.1 the mode was a boolean; it is still read so existing editors keep their mode. */
    private static final String THEME_MODE_KEY = "theme_mode";
    private static final String MODE_KEY = "mode";

    /** What right-clicking an NPC does. */
    public enum Mode {
        SHOP("item.cobblemon_economy_plus.shop_editor.mode_shop"),
        THEME("item.cobblemon_economy_plus.shop_editor.mode_theme"),
        ADMIN("item.cobblemon_economy_plus.shop_editor.mode_admin");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(translationKey);
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
    /** How long a Shift + left-click stays armed before the second click must be repeated (5 seconds). */
    private static final long REMOVE_CONFIRM_TICKS = 100;
    private static final Map<UUID, PendingRemoval> PENDING_REMOVALS = new HashMap<>();

    private record PendingRemoval(UUID target, long armedAt) {
    }

    public ShopEditorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.reverse_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.mode_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.remove_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.admin_tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.current_mode",
                mode(stack).displayName()).withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Right-click in the air (or on a block): next mode. In Admin mode, Shift + right-click opens the admin screen
     * instead.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (mode(stack) == Mode.ADMIN && player.isShiftKeyDown()) {
                AdminService.open(serverPlayer, "");
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            Mode next = mode(stack).next();
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.remove(THEME_MODE_KEY);
                tag.putString(MODE_KEY, next.name());
            });
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_mode",
                    next.displayName()), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean isEditor(ItemStack stack) {
        return stack.getItem() instanceof ShopEditorItem;
    }

    public static Mode mode(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(MODE_KEY)) {
            try {
                return Mode.valueOf(tag.getString(MODE_KEY));
            } catch (IllegalArgumentException ignored) {
                return Mode.SHOP;
            }
        }
        return tag.getBoolean(THEME_MODE_KEY) ? Mode.THEME : Mode.SHOP;
    }

    /**
     * Handles a left-click on an entity with the editor in the main hand. Shift + left-click on a shopkeeper
     * arms the removal; doing it again on the same NPC within {@link #REMOVE_CONFIRM_TICKS} deletes it.
     * A plain left-click on a shopkeeper only shows that hint.
     *
     * @return true when the click was about a shopkeeper and the attack should be cancelled
     */
    public static boolean tryRemove(ServerPlayer player, Entity target) {
        if (!isEditor(player.getMainHandItem()) || !Shopkeeper.isShopkeeper(target)) {
            return false;
        }
        if (!EconomyPermissions.has(player, EconomyPermission.SHOPKEEPER_REMOVE)) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_remove_no_permission"), true);
            return true;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_remove_hint"), true);
            return true;
        }

        long now = player.serverLevel().getGameTime();
        PendingRemoval pending = PENDING_REMOVALS.get(player.getUUID());
        if (pending == null || !pending.target().equals(target.getUUID()) || now - pending.armedAt() > REMOVE_CONFIRM_TICKS) {
            PENDING_REMOVALS.put(player.getUUID(), new PendingRemoval(target.getUUID(), now));
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_remove_confirm",
                    target.getDisplayName()).withStyle(ChatFormatting.RED), true);
            return true;
        }

        PENDING_REMOVALS.remove(player.getUUID());
        Component name = target.getDisplayName();
        if (Shopkeeper.remove(target)) {
            player.displayClientMessage(Component.translatable("command.cobblemon_economy_plus.shopkeeper_removed", name), true);
        }
        return true;
    }

    public static void onDisconnect(UUID playerId) {
        PENDING_REMOVALS.remove(playerId);
    }

    /** Consumes an editor interaction: cycles the NPC's shop or theme depending on the editor's mode. */
    public static boolean tryEdit(ServerPlayer player, Entity target) {
        if (!isEditor(player.getMainHandItem())) {
            return false;
        }
        if (mode(player.getMainHandItem()) == Mode.ADMIN) {
            AdminService.open(player, Shopkeeper.shopId(target).orElse(""));
            return true;
        }
        if (!EconomyPermissions.has(player, EconomyPermission.EDITOR)) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_no_permission"), true);
            return true;
        }
        if (!Shopkeeper.canBeShopkeeper(target)) {
            player.displayClientMessage(Component.translatable("command.cobblemon_economy_plus.invalid_shopkeeper"), true);
            return true;
        }

        int direction = player.isShiftKeyDown() ? -1 : 1;
        if (mode(player.getMainHandItem()) == Mode.THEME) {
            return editTheme(player, target, direction);
        }
        return editShop(player, target, direction);
    }

    private static boolean editShop(ServerPlayer player, Entity target, int direction) {
        ShopDefinition nextShop = ShopManager.cycle(player.server, Shopkeeper.shopId(target).orElse(null), direction)
                .orElse(null);
        if (nextShop == null) {
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_no_shops"), true);
            return true;
        }
        if (!Shopkeeper.setShop(target, nextShop.id())) {
            player.displayClientMessage(Component.translatable("command.cobblemon_economy_plus.invalid_shopkeeper"), true);
            return true;
        }

        player.displayClientMessage(Component.translatable(
                "message.cobblemon_economy_plus.editor_set",
                target.getDisplayName(),
                LocalizedText.resolve(nextShop.displayName(), player.clientInformation().language()),
                nextShop.id()
        ), true);
        return true;
    }

    private static boolean editTheme(ServerPlayer player, Entity target, int direction) {
        String current = Shopkeeper.themeId(target).orElseGet(() -> Shopkeeper.shopId(target)
                .flatMap(id -> ShopManager.get(player.server, id))
                .map(ShopDefinition::theme)
                .orElse(ShopThemes.DEFAULT));
        String next = ShopThemes.cycle(current, direction);
        if (!Shopkeeper.setTheme(target, next)) {
            player.displayClientMessage(Component.translatable("command.cobblemon_economy_plus.invalid_shopkeeper"), true);
            return true;
        }
        player.displayClientMessage(Component.translatable(
                "message.cobblemon_economy_plus.editor_theme_set",
                target.getDisplayName(),
                Component.translatable("theme.cobblemon_economy_plus." + next),
                ShopThemes.IDS.indexOf(next) + 1,
                ShopThemes.IDS.size()
        ), true);
        return true;
    }
}
