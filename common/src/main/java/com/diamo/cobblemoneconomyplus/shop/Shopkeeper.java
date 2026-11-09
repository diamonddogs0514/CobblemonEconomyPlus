package com.diamo.cobblemoneconomyplus.shop;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * Shopkeepers are normal NPC entities marked with a persistent scoreboard tag.
 * Tags are vanilla data, so this works for villagers as well as NPC mods on both
 * Fabric and NeoForge without relying on loader-specific entity NBT APIs.
 */
public final class Shopkeeper {
    public static final String TAG_PREFIX = "cep_shop:";
    public static final String THEME_TAG_PREFIX = "cep_theme:";

    private Shopkeeper() {
    }

    public static boolean canBeShopkeeper(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player);
    }

    public static Optional<String> shopId(Entity entity) {
        return entity.getTags().stream()
                .filter(tag -> tag.startsWith(TAG_PREFIX))
                .map(tag -> tag.substring(TAG_PREFIX.length()))
                .filter(Shopkeeper::isValidShopId)
                .findFirst();
    }

    public static boolean isShopkeeper(Entity entity) {
        return canBeShopkeeper(entity) && shopId(entity).isPresent();
    }

    public static boolean setShop(Entity entity, String shopId) {
        if (!canBeShopkeeper(entity) || !isValidShopId(shopId)) {
            return false;
        }

        entity.getTags().stream()
                .filter(tag -> tag.startsWith(TAG_PREFIX))
                .toList()
                .forEach(entity::removeTag);
        return entity.addTag(TAG_PREFIX + shopId.toLowerCase(Locale.ROOT));
    }

    public static boolean clearShop(Entity entity) {
        boolean changed = false;
        for (String tag : entity.getTags().stream().filter(tag -> tag.startsWith(TAG_PREFIX)).toList()) {
            changed |= entity.removeTag(tag);
        }
        return changed;
    }

    /** Optional per-NPC GUI theme override, stored as a second scoreboard tag. */
    public static Optional<String> themeId(Entity entity) {
        return entity.getTags().stream()
                .filter(tag -> tag.startsWith(THEME_TAG_PREFIX))
                .map(tag -> tag.substring(THEME_TAG_PREFIX.length()))
                .filter(ShopThemes::isValid)
                .findFirst();
    }

    public static boolean setTheme(Entity entity, String themeId) {
        if (!canBeShopkeeper(entity) || !ShopThemes.isValid(themeId)) {
            return false;
        }
        entity.getTags().stream()
                .filter(tag -> tag.startsWith(THEME_TAG_PREFIX))
                .toList()
                .forEach(entity::removeTag);
        return entity.addTag(THEME_TAG_PREFIX + themeId);
    }

    public static boolean isValidShopId(String id) {
        return id != null && id.matches("[a-z0-9_-]{1,32}");
    }
}
