package com.diamo.cobblemoneconomyplus.client;

import com.cobblemon.mod.common.CobblemonSounds;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;

/**
 * DRAFT: a Trainer Card shown when right-clicking the Trainer Card item - a first sketch of an "info item"
 * (name, trainer id, balance). More fields (party size, badges, playtime...) can be added as rows later.
 */
public final class TrainerCardScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int BASE_HEIGHT = 120;
    /** Extra height for the Battle Points strip, shown when the server has Cobblemon Battle Plus. */
    private static final int POINTS_ROW = 24;
    private static final int WALLET_Y = 92;

    private static final int OUTLINE = 0xFF16181D;
    private static final int RED = 0xFFD8393B;
    private static final int RED_DARK = 0xFF9E2428;
    private static final int RED_LIGHT = 0xFFF06A66;
    private static final int BODY = 0xFFF1EDE2;
    private static final int BODY_DARK = 0xFFE3DECF;
    private static final int LINE = 0xFFC9C3B0;
    private static final int INK = 0xFF2A2E38;
    private static final int MUTED = 0xFF7A7C85;
    private static final int GOLD = 0xFFF2C94C;
    private static final int GOLD_DARK = 0xFF9C7A1E;
    private static final int GOLD_TEXT = 0xFF9C6B00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BP_BLUE = 0xFF3D6FD6;
    private static final int BP_BLUE_DARK = 0xFF23458F;
    private static final int BP_TEXT = 0xFF2A4FA0;

    private double shown;
    private double shownPoints;

    public TrainerCardScreen() {
        super(Component.translatable("item.cobblemon_economy_plus.trainer_card"));
        this.shown = ClientEconomy.balance();
        this.shownPoints = Math.max(0, ClientEconomy.battlePoints());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x59000000);
    }

    @Override
    protected void init() {
        super.init();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(CobblemonSounds.PC_ON, 1.0F));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The inventory key (E by default, rebindable) closes the card as well as Escape.
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int battlePoints = ClientEconomy.battlePoints();
        boolean showPoints = battlePoints >= 0;
        int cardHeight = BASE_HEIGHT + (showPoints ? POINTS_ROW : 0);
        int x = (width - WIDTH) / 2;
        int y = (height - cardHeight) / 2;

        cutRect(g, x, y, WIDTH, cardHeight, 5, OUTLINE);
        cutRect(g, x + 2, y + 2, WIDTH - 4, cardHeight - 4, 4, BODY);

        // Header.
        cutRect(g, x + 2, y + 2, WIDTH - 4, 24, 4, RED);
        g.fill(x + 6, y + 4, x + WIDTH - 6, y + 5, RED_LIGHT);
        g.fill(x + 2, y + 25, x + WIDTH - 2, y + 26, RED_DARK);
        g.fill(x + 2, y + 26, x + WIDTH - 2, y + 28, OUTLINE);
        drawBall(g, x + 18, y + 14, 8);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_title"), x + 32, y + 10, WHITE, true);

        // Photo.
        int photoX = x + 12;
        int photoY = y + 38;
        cutRect(g, photoX - 2, photoY - 2, 54, 54, 2, OUTLINE);
        PlayerFaceRenderer.draw(g, player.getSkin(), photoX, photoY, 50);

        // Fields.
        int fx = x + 76;
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_name"), fx, y + 36, MUTED, false);
        g.drawString(font, abbreviate(player.getName().getString(), WIDTH - 76 - 14), fx, y + 46, INK, false);

        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_id"), fx, y + 62, MUTED, false);
        g.drawString(font, String.format("%08X", player.getUUID().hashCode()), fx, y + 72, INK, false);

        // Wallet strip.
        int walletY = y + WALLET_Y;
        drawStrip(g, x, walletY);
        drawCoin(g, x + 20, walletY + 10, 6);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_balance"), x + 32, walletY + 6, MUTED, false);
        shown = approach(shown, ClientEconomy.balance());
        String amount = EconomyFormat.money(Math.round(shown));
        g.drawString(font, amount, x + WIDTH - 14 - font.width(amount), walletY + 6, GOLD_TEXT, false);

        // Battle Points strip (Cobblemon Battle Plus).
        if (showPoints) {
            int pointsY = walletY + POINTS_ROW;
            drawStrip(g, x, pointsY);
            drawPointsBadge(g, x + 20, pointsY + 10);
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_battle_points"), x + 32, pointsY + 6, MUTED, false);
            shownPoints = approach(shownPoints, battlePoints);
            String points = EconomyFormat.money(Math.round(shownPoints)) + " BP";
            g.drawString(font, points, x + WIDTH - 14 - font.width(points), pointsY + 6, BP_TEXT, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawBall(GuiGraphics g, int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, OUTLINE);
            if (Math.abs(dy) < r) {
                int inner = (int) Math.round(Math.sqrt((double) (r - 1) * (r - 1) - (double) dy * dy));
                if (Math.abs(dy) > 1) {
                    g.fill(cx - inner, cy + dy, cx + inner + 1, cy + dy + 1, dy < 0 ? RED : WHITE);
                }
            }
        }
        disc(g, cx, cy, 3, OUTLINE);
        disc(g, cx, cy, 1, WHITE);
    }

    /** Counts a shown value towards its target, so changes roll up instead of jumping. */
    private static double approach(double shownValue, double target) {
        double diff = target - shownValue;
        return shownValue + (Math.abs(diff) < 1.0D ? diff : diff * 0.18D);
    }

    private static void drawStrip(GuiGraphics g, int x, int stripY) {
        cutRect(g, x + 8, stripY, WIDTH - 16, 20, 3, LINE);
        cutRect(g, x + 9, stripY + 1, WIDTH - 18, 18, 2, BODY_DARK);
    }

    /** A small blue shield for Battle Points, the same size as the coin. */
    private void drawPointsBadge(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 6, cy - 6, cx + 7, cy + 3, BP_BLUE_DARK);
        g.fill(cx - 5, cy + 3, cx + 6, cy + 5, BP_BLUE_DARK);
        g.fill(cx - 3, cy + 5, cx + 4, cy + 7, BP_BLUE_DARK);
        g.fill(cx - 5, cy - 5, cx + 6, cy + 3, BP_BLUE);
        g.fill(cx - 4, cy + 3, cx + 5, cy + 4, BP_BLUE);
        g.fill(cx - 2, cy + 4, cx + 3, cy + 5, BP_BLUE);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 3, WHITE);
        g.fill(cx - 3, cy - 1, cx + 4, cy + 1, WHITE);
    }

    private void drawCoin(GuiGraphics g, int cx, int cy, int r) {
        disc(g, cx, cy, r, GOLD_DARK);
        disc(g, cx, cy, r - 1, GOLD);
        g.fill(cx - 1, cy - r + 2, cx + 1, cy + r - 1, GOLD_DARK);
    }

    private void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private static void cutRect(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + w, y + h - cut, color);
    }

    private String abbreviate(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + "...") > maxWidth) {
            end--;
        }
        return value.substring(0, end) + "...";
    }
}
