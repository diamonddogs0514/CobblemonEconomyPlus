package com.diamo.cobblemoneconomyplus.client;

import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Balance card in the top-right corner. By default it only appears for a few seconds when the balance
 * changes (with a +/- chip and a spinning coin); hold the show-balance key to peek at any time, or
 * pick "always" / "hidden" with Shift + that key (see {@link HudSettings}).
 */
public final class CurrencyHud {
    private static final long SHOW_MILLIS = 3200L;
    private static final long CHIP_MILLIS = 2200L;
    private static final long SPIN_MILLIS = 600L;
    private static final long MODE_NOTICE_MILLIS = 2500L;

    private static final int OUTLINE = 0xFF0E1218;
    private static final int BODY = 0xF21B212C;
    private static final int WELL = 0xF2121720;
    private static final int RED = 0xFFD8393B;
    private static final int GOLD = 0xFFF2C94C;
    private static final int GOLD_DARK = 0xFF9C7A1E;
    private static final int MUTED = 0xFF8E9BB3;

    private static long changeAt = Long.MIN_VALUE / 2;
    private static long lastDelta;
    private static long noticeUntil;
    private static boolean keyWasDown;
    private static float visibility;
    private static double shown = -1;
    private static LocalPlayer lastPlayer;

    // Count-up / count-down animation of the displayed number.
    private static boolean animating;
    private static double animFrom;
    private static double animTo;
    private static long animStart;
    private static long animDuration;
    private static long nextTickAt;
    private static int tickIndex;
    private static SoundEvent coinSound;
    private static boolean coinSoundResolved;

    private CurrencyHud() {
    }

    /** First balance packet after joining: show the card briefly without animation or +/- chip. */
    static void onSync() {
        changeAt = Util.getMillis();
        lastDelta = 0;
        animating = false;
        shown = ClientEconomy.balance();
    }

    static void onChange(long delta) {
        long now = Util.getMillis();
        changeAt = now;
        lastDelta = delta;

        double target = ClientEconomy.balance();
        animFrom = shown >= 0 ? shown : target - delta;
        animTo = target;
        animStart = now;
        // Bigger changes take a little longer, but never drag on.
        animDuration = Math.min(1800L, 700L + Math.round(260.0D * Math.log10(Math.abs(delta) + 1.0D)));
        animating = true;
        nextTickAt = now;
        tickIndex = 0;
    }

    /** Advances the animation, playing a rising tick while counting up and a coin sound when done. */
    private static void updateAnimation(Minecraft mc, long now) {
        if (!animating) {
            shown = ClientEconomy.balance();
            return;
        }
        boolean gain = animTo > animFrom;
        double t = Math.min(1.0D, (now - animStart) / (double) animDuration);
        double eased = 1.0D - Math.pow(1.0D - t, 3.0D);
        shown = animFrom + (animTo - animFrom) * eased;

        if (gain) {
            while (now >= nextTickAt && t < 1.0D) {
                float pitch = 0.9F + 0.7F * (float) t;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.35F));
                tickIndex++;
                nextTickAt += 85L;
            }
        }
        if (t >= 1.0D) {
            animating = false;
            shown = animTo;
            if (gain) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(finishSound(), 1.0F, 0.8F));
            }
        }
    }

    /** Cobblemon's coin-sack sound if present, otherwise a vanilla chime. */
    private static SoundEvent finishSound() {
        if (!coinSoundResolved) {
            coinSoundResolved = true;
            coinSound = BuiltInRegistries.SOUND_EVENT
                    .getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "block.relic_coin_sack.hit"))
                    .orElse(SoundEvents.AMETHYST_BLOCK_CHIME);
        }
        return coinSound;
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (mc.player != lastPlayer) {
            lastPlayer = mc.player;
            ClientEconomy.markUnsynced();
            shown = -1;
            animating = false;
        }

        long now = Util.getMillis();
        updateAnimation(mc, now);
        boolean down = HudKeys.SHOW_BALANCE.isDown();
        if (down && !keyWasDown && Screen.hasShiftDown()) {
            HudSettings.cycle();
            noticeUntil = now + MODE_NOTICE_MILLIS;
        }
        keyWasDown = down;

        boolean shopScreen = mc.screen instanceof TrainerBoutiqueScreen
                || mc.screen instanceof TrainerCardScreen;
        boolean wantVisible = down
                || now < noticeUntil
                || HudSettings.mode() == HudSettings.Mode.ALWAYS
                || (HudSettings.mode() == HudSettings.Mode.ON_CHANGE
                        && now - changeAt < SHOW_MILLIS + (animating ? animDuration : 0L));
        float target = wantVisible && !shopScreen && !mc.getDebugOverlay().showDebugScreen() ? 1.0F : 0.0F;
        visibility += (target - visibility) * 0.22F;
        if (Math.abs(target - visibility) < 0.01F) {
            visibility = target;
        }
        if (visibility <= 0.02F) {
            return;
        }

        drawCard(g, mc, now);
    }

    private static void drawCard(GuiGraphics g, Minecraft mc, long now) {
        float alpha = visibility;
        Component label = Component.translatable("hud.cobblemon_economy_plus.balance_label");
        String amount = EconomyFormat.money(Math.round(shown));
        // Size the card for the larger of the shown / final value so it doesn't jitter while counting.
        String widest = EconomyFormat.money(Math.round(Math.max(shown, ClientEconomy.balance())));
        int textWidth = Math.max(mc.font.width(label), Math.max(mc.font.width(amount), mc.font.width(widest)));
        int w = 6 + 22 + 5 + textWidth + 9;
        int h = 26;
        int slide = Math.round((1.0F - alpha) * 14.0F);
        int x = g.guiWidth() - w - 6 + slide;
        int y = 6 + statusEffectOffset(mc);

        cutRect(g, x, y, w, h, 2, tint(OUTLINE, alpha));
        cutRect(g, x + 1, y + 1, w - 2, h - 2, 1, tint(BODY, alpha));
        g.fill(x + 1, y + 1, x + 27, y + h - 1, tint(WELL, alpha));
        g.fill(x + 3, y + 1, x + w - 3, y + 2, tint(RED, alpha));
        g.fill(x + 27, y + 3, x + 28, y + h - 3, tint(0xFF2B3446, alpha));

        long sinceChange = now - changeAt;
        // The coin keeps spinning for as long as the number counts up, ending face-on.
        long spinMillis = animating && animTo > animFrom ? animDuration : SPIN_MILLIS;
        long spinFlips = Math.max(1L, Math.round(spinMillis / 450.0D));
        float spin = sinceChange >= 0 && sinceChange < spinMillis
                ? Math.abs((float) Math.cos(sinceChange / (double) spinMillis * Math.PI * spinFlips))
                : 1.0F;
        drawCoin(g, x + 14, y + h / 2 + 1, 7, Math.max(0.12F, spin), alpha);

        if (alpha > 0.2F) {
            g.drawString(mc.font, label, x + 33, y + 4, tint(MUTED, alpha), false);
            // The number glows green while counting up and red while counting down.
            int amountColor = !animating ? GOLD : (animTo > animFrom ? 0xFFA8F5B8 : 0xFFFF9A94);
            g.drawString(mc.font, amount, x + 33, y + 14, tint(amountColor, alpha), true);
        }

        // +/- chip under the card.
        if (lastDelta != 0 && sinceChange >= 0 && sinceChange < CHIP_MILLIS && alpha > 0.2F) {
            float chipAlpha = alpha * Math.min(1.0F, (CHIP_MILLIS - sinceChange) / 500.0F);
            boolean gain = lastDelta > 0;
            String chip = (gain ? "+" : "-") + EconomyFormat.money(Math.abs(lastDelta));
            int cw = mc.font.width(chip) + 12;
            int cx = x + w - cw;
            int cy = y + h + 2 + Math.round(sinceChange / (float) CHIP_MILLIS * 4.0F);
            cutRect(g, cx, cy, cw, 12, 2, tint(OUTLINE, chipAlpha));
            cutRect(g, cx + 1, cy + 1, cw - 2, 10, 1, tint(BODY, chipAlpha));
            g.fill(cx + 1, cy + 3, cx + 3, cy + 9, tint(gain ? 0xFF4CAF6A : 0xFFE0524F, chipAlpha));
            if (chipAlpha > 0.2F) {
                g.drawString(mc.font, chip, cx + 6, cy + 2, tint(gain ? 0xFF8AF0A6 : 0xFFFF8A80, chipAlpha), true);
            }
        }

        // Shown briefly after Shift + key so the new mode is obvious.
        if (now < noticeUntil && alpha > 0.2F) {
            Component notice = Component.translatable("gui.cobblemon_economy_plus.hud_mode",
                    Component.translatable(HudSettings.mode().translationKey()));
            int nw = mc.font.width(notice) + 10;
            int ny = y + h + 2 + (lastDelta != 0 && sinceChange < CHIP_MILLIS ? 16 : 0);
            int nx = x + w - nw;
            cutRect(g, nx, ny, nw, 12, 2, tint(OUTLINE, alpha));
            cutRect(g, nx + 1, ny + 1, nw - 2, 10, 1, tint(BODY, alpha));
            g.drawString(mc.font, notice, nx + 5, ny + 2, tint(0xFFE6ECF7, alpha), false);
        }
    }

    /** Keeps clear of vanilla's potion-effect icons in the same corner. */
    private static int statusEffectOffset(Minecraft mc) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return (beneficial ? 26 : 0) + (harmful ? 26 : 0);
    }

    /** A coin that can "spin" by squashing its width. */
    private static void drawCoin(GuiGraphics g, int cx, int cy, int r, float squash, float alpha) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy) * squash);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, tint(GOLD_DARK, alpha));
        }
        int ri = r - 2;
        for (int dy = -ri; dy <= ri; dy++) {
            int half = (int) Math.round(Math.sqrt((double) ri * ri - (double) dy * dy) * squash);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, tint(GOLD, alpha));
        }
        if (squash > 0.5F) {
            int bar = Math.max(1, Math.round(squash));
            g.fill(cx - bar / 2, cy - 3, cx - bar / 2 + bar + (squash > 0.8F ? 1 : 0), cy + 4, tint(GOLD_DARK, alpha));
            g.fill(cx - 2, cy - 1, cx + 3, cy + 1, tint(GOLD_DARK, alpha));
        }
    }

    private static void cutRect(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + w, y + h - cut, color);
    }

    private static int tint(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
