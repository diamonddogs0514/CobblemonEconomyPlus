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
    private static final int HEIGHT = 120;

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

    private double shown;

    public TrainerCardScreen() {
        super(Component.translatable("item.cobblemon_economy_plus.trainer_card"));
        this.shown = ClientEconomy.balance();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        cutRect(g, x, y, WIDTH, HEIGHT, 5, OUTLINE);
        cutRect(g, x + 2, y + 2, WIDTH - 4, HEIGHT - 4, 4, BODY);

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
        cutRect(g, x + 8, y + HEIGHT - 28, WIDTH - 16, 20, 3, LINE);
        cutRect(g, x + 9, y + HEIGHT - 27, WIDTH - 18, 18, 2, BODY_DARK);
        drawCoin(g, x + 20, y + HEIGHT - 18, 6);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.card_balance"), x + 32, y + HEIGHT - 22, MUTED, false);
        double diff = ClientEconomy.balance() - shown;
        shown += Math.abs(diff) < 1.0D ? diff : diff * 0.18D;
        String amount = EconomyFormat.money(Math.round(shown));
        g.drawString(font, amount, x + WIDTH - 14 - font.width(amount), y + HEIGHT - 22, GOLD_TEXT, false);

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
