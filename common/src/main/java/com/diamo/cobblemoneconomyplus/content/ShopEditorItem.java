package com.diamo.cobblemoneconomyplus.content;

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

import java.util.List;

/**
 * Operator tool for configuring a shopkeeper NPC.
 *
 * <p>Two modes, toggled by right-clicking in the air: <b>Shop</b> mode (default) cycles which shop the NPC
 * sells, <b>Theme</b> mode cycles the shop GUI's look. Right-click an NPC to go forward, Shift + right-click
 * to go back.</p>
 */
public final class ShopEditorItem extends Item {
    private static final String THEME_MODE_KEY = "theme_mode";

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
        tooltip.add(Component.translatable("item.cobblemon_economy_plus.shop_editor.current_mode",
                modeName(isThemeMode(stack))).withStyle(ChatFormatting.YELLOW));
    }

    /** Right-click in the air (or on a block): switch between Shop mode and Theme mode. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean themeMode = !isThemeMode(stack);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(THEME_MODE_KEY, themeMode));
            player.displayClientMessage(Component.translatable("message.cobblemon_economy_plus.editor_mode",
                    modeName(themeMode)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static boolean isEditor(ItemStack stack) {
        return stack.getItem() instanceof ShopEditorItem;
    }

    private static boolean isThemeMode(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(THEME_MODE_KEY);
    }

    private static Component modeName(boolean themeMode) {
        return Component.translatable(themeMode
                ? "item.cobblemon_economy_plus.shop_editor.mode_theme"
                : "item.cobblemon_economy_plus.shop_editor.mode_shop");
    }

    /** Consumes an editor interaction: cycles the NPC's shop or theme depending on the editor's mode. */
    public static boolean tryEdit(ServerPlayer player, Entity target) {
        if (!isEditor(player.getMainHandItem())) {
            return false;
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
        if (isThemeMode(player.getMainHandItem())) {
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
