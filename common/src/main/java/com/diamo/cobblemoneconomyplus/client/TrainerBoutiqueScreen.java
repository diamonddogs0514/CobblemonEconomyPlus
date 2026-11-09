package com.diamo.cobblemoneconomyplus.client;

import com.cobblemon.mod.common.CobblemonSounds;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import com.diamo.cobblemoneconomyplus.shop.SellableStacks;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * "Trainer Boutique" - a list + showcase shop screen with selectable visual themes
 * (Poke Ball variants, TM discs, berries and Minecraft block looks; see {@link BoutiqueTheme}).
 *
 * <p>Layout: a one-category pager (balls / medicine / stones / other) above a scrolling item list on the
 * left, and a showcase pane on the right with a big item preview, quantity stepper, running total, balance
 * after purchase and a Buy button. Everything is drawn from vanilla primitives so it looks identical on
 * Fabric and NeoForge.</p>
 */
public final class TrainerBoutiqueScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 228;
    private static final int HEADER_HEIGHT = 32;

    private static final int TAB_Y = 38;
    private static final int TAB_HEIGHT = 18;
    private static final int ARROW_WIDTH = 16;

    private static final int LIST_X = 8;
    private static final int LIST_Y = 58;
    private static final int LIST_WIDTH = 170;
    private static final int LIST_HEIGHT = 142;
    private static final int ROW_HEIGHT = 23;
    private static final int VISIBLE_ROWS = 6;

    private static final int DETAIL_X = 184;
    private static final int DETAIL_Y = 38;
    private static final int DETAIL_WIDTH = 108;
    private static final int DETAIL_HEIGHT = 162;

    private static final int FOOTER_Y = 204;

    // Theme-independent colours.
    private static final int GOLD = 0xFFF2C94C;
    private static final int GOLD_DARK = 0xFF9C7A1E;
    private static final int GREEN = 0xFF4CAF6A;
    private static final int GREEN_DARK = 0xFF2E7A47;
    private static final int GRASS = 0xFF5FA23A;
    private static final int GRASS_DARK = 0xFF3F7A26;
    private static final int WHITE = 0xFFFFFFFF;

    private final EconomyPayloads.OpenShopPayload shop;
    private final BoutiqueTheme c;
    private final List<Category> tabs = new ArrayList<>();
    private final List<Integer> visible = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();

    private Category category = Category.ALL;
    private int scrollRow;
    private int selectedPos;
    private int quantity = 1;

    /** Buy mode lists what the shop sells; sell mode lists what the player can sell to it. */
    private boolean sellMode;
    /** The rows currently shown: the shop's own entries (buy) or the sellable inventory items (sell, amount = 1). */
    private List<EconomyPayloads.ShopEntryPayload> current;
    /** Sell mode only: how many of each item the player holds (pristine stacks in the main inventory). */
    private final Map<String, Integer> owned = new HashMap<>();
    private int ticks;

    private double displayedBalance;
    private long lastBalance;
    private boolean openingSoundPlayed;
    private boolean closingSoundPlayed;

    public TrainerBoutiqueScreen(EconomyPayloads.OpenShopPayload shop) {
        this(shop, false);
    }

    /** @param refresh true when this replaces an already open shop screen (no opening sound). */
    public TrainerBoutiqueScreen(EconomyPayloads.OpenShopPayload shop, boolean refresh) {
        super(Component.literal(shop.shopName()));
        this.shop = shop;
        this.openingSoundPlayed = refresh;
        this.c = BoutiqueTheme.byId(shop.theme());
        this.lastBalance = ClientEconomy.balance();
        this.displayedBalance = lastBalance;
        this.current = shop.entries();

        tabs.add(Category.ALL);
        for (Category candidate : Category.values()) {
            if (candidate == Category.ALL) {
                continue;
            }
            for (EconomyPayloads.ShopEntryPayload entry : shop.entries()) {
                if (Category.of(entry.itemId()) == candidate) {
                    tabs.add(candidate);
                    break;
                }
            }
        }
        rebuildVisible(-1);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Soft vignette instead of vanilla's blur, so the shopkeeper stays visible behind the panel.
        graphics.fill(0, 0, width, height, 0x59000000);
    }

    @Override
    protected void init() {
        super.init();
        if (!openingSoundPlayed) {
            openingSoundPlayed = true;
            playUiSound(CobblemonSounds.PC_ON);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closingSoundPlayed) {
            closingSoundPlayed = true;
            playUiSound(CobblemonSounds.PC_OFF);
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        trackBalance();
        int px = panelX();
        int py = panelY();

        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawFrame(graphics, px, py, mouseX, mouseY);
        drawTabs(graphics, px, py, mouseX, mouseY);
        drawList(graphics, px, py, mouseX, mouseY);
        drawDetail(graphics, px, py, mouseX, mouseY);
        drawFooter(graphics, px, py);
        drawFloatingTexts(graphics, px, py);
        super.render(graphics, mouseX, mouseY, partialTick);

        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            ItemStack stack = stackFor(current.get(visible.get(hovered)));
            if (!stack.isEmpty()) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    private void drawFrame(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        cutRect(g, x, y, PANEL_WIDTH, PANEL_HEIGHT, 5, c.outline);
        cutRect(g, x + 2, y + 2, PANEL_WIDTH - 4, PANEL_HEIGHT - 4, 4, c.body);

        // Header bar with themed texture.
        cutRect(g, x + 2, y + 2, PANEL_WIDTH - 4, HEADER_HEIGHT, 4, c.accent);
        if (c.speckleA != 0) {
            speckle(g, x + 4, y + 4, PANEL_WIDTH - 8, HEADER_HEIGHT - 4, 2, c.speckleA, c.speckleB, 7);
        }
        g.fill(x + 6, y + 4, x + PANEL_WIDTH - 6, y + 5, c.accentLight);
        drawHeaderPattern(g, x, y);
        g.fill(x + 2, y + HEADER_HEIGHT - 1, x + PANEL_WIDTH - 2, y + HEADER_HEIGHT + 1, c.accentDark);
        g.fill(x + 2, y + HEADER_HEIGHT + 1, x + PANEL_WIDTH - 2, y + HEADER_HEIGHT + 3, c.outline);

        drawEmblem(g, x + 22, y + 17, 11);
        // With a Buy/Sell switch in the header the title has less room.
        int titleWidth = canSell() ? 148 : 190;
        g.drawString(font, abbreviate(shop.shopName(), titleWidth), x + 40, y + 9, WHITE, true);
        Component subtitle = shop.subtitle().isBlank()
                ? Component.translatable("gui.cobblemon_economy_plus.boutique_subtitle." + c.id)
                : Component.literal(shop.subtitle());
        g.drawString(font, abbreviate(subtitle.getString(), titleWidth), x + 40, y + 20, c.headerSub, true);

        if (canSell()) {
            drawModeButton(g, modeBuyX(x), y + 10, "gui.cobblemon_economy_plus.mode_buy", !sellMode, mouseX, mouseY);
            drawModeButton(g, modeSellX(x), y + 10, "gui.cobblemon_economy_plus.mode_sell", sellMode, mouseX, mouseY);
        }

        boolean closeHover = inside(mouseX, mouseY, closeX(x), y + 9, 16, 16);
        cutRect(g, closeX(x), y + 9, 16, 16, 3, c.outline);
        cutRect(g, closeX(x) + 1, y + 10, 14, 14, 2, closeHover ? c.accentLight : c.accentDark);
        g.drawString(font, "x", closeX(x) + 5, y + 13, WHITE, false);

        g.fill(x + 8, y + FOOTER_Y - 2, x + PANEL_WIDTH - 8, y + FOOTER_Y - 1, c.bodyLine);
    }

    private static final int MODE_BUTTON_WIDTH = 34;
    private static final int MODE_BUTTON_HEIGHT = 14;

    private boolean canSell() {
        return !shop.sellEntries().isEmpty();
    }

    private int modeBuyX(int px) {
        return px + PANEL_WIDTH - 26 - 6 - 2 * MODE_BUTTON_WIDTH;
    }

    private int modeSellX(int px) {
        return modeBuyX(px) + MODE_BUTTON_WIDTH;
    }

    private void drawModeButton(GuiGraphics g, int x, int y, String key, boolean active, int mouseX, int mouseY) {
        boolean hovered = !active && inside(mouseX, mouseY, x, y, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT);
        cutRect(g, x, y, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT, 2, c.outline);
        cutRect(g, x + 1, y + 1, MODE_BUTTON_WIDTH - 2, MODE_BUTTON_HEIGHT - 2, 1,
                active ? WHITE : (hovered ? c.accentLight : c.accentDark));
        Component label = Component.translatable(key);
        g.drawString(font, label, x + (MODE_BUTTON_WIDTH - font.width(label)) / 2, y + 3,
                active ? c.ink : WHITE, false);
    }

    private void drawHeaderPattern(GuiGraphics g, int x, int y) {
        switch (c.pattern) {
            case PLANKS -> {
                int line = (c.accentDark & 0xFFFFFF) | 0x99000000;
                g.fill(x + 4, y + 12, x + PANEL_WIDTH - 4, y + 13, line);
                g.fill(x + 4, y + 22, x + PANEL_WIDTH - 4, y + 23, line);
                for (int px = 30; px < PANEL_WIDTH - 10; px += 61) {
                    g.fill(x + px, y + 5, x + px + 1, y + 12, line);
                    g.fill(x + px + 33, y + 13, x + px + 34, y + 22, line);
                    g.fill(x + px + 11, y + 23, x + px + 12, y + HEADER_HEIGHT - 1, line);
                }
            }
            case RIVETS -> {
                int[][] spots = {{7, 7}, {PANEL_WIDTH - 10, 7}, {7, HEADER_HEIGHT - 6}, {PANEL_WIDTH - 10, HEADER_HEIGHT - 6}};
                for (int[] spot : spots) {
                    g.fill(x + spot[0], y + spot[1], x + spot[0] + 3, y + spot[1] + 3, c.accentDark);
                    g.fill(x + spot[0], y + spot[1], x + spot[0] + 2, y + spot[1] + 2, c.accentLight);
                }
            }
            case GRASS -> {
                g.fill(x + 4, y + 3, x + PANEL_WIDTH - 4, y + 7, GRASS);
                for (int px = 4; px < PANEL_WIDTH - 4; px += 3) {
                    int drop = ((px * 7) % 5 == 0) ? 3 : 1;
                    g.fill(x + px, y + 7, x + px + 2, y + 7 + drop, GRASS_DARK);
                }
            }
            case NONE -> {
            }
        }
    }

    /** Single-category pager: [<]  icon + name (n/m)  [>]. Click or wheel to cycle. */
    private void drawTabs(GuiGraphics g, int px, int py, int mouseX, int mouseY) {
        int x = px + LIST_X;
        int y = py + TAB_Y;
        if (sellMode) {
            // No categories while selling: a single banner explains what the list is.
            cutRect(g, x, y, LIST_WIDTH, TAB_HEIGHT, 3, c.outline);
            cutRect(g, x + 1, y + 1, LIST_WIDTH - 2, TAB_HEIGHT - 2, 2, c.accent);
            Component banner = Component.translatable("gui.cobblemon_economy_plus.sell_banner");
            g.drawString(font, banner, x + (LIST_WIDTH - font.width(banner)) / 2, y + 5, WHITE, true);
            return;
        }
        boolean multiple = tabs.size() > 1;
        int centerX = x + (multiple ? ARROW_WIDTH + 2 : 0);
        int centerW = LIST_WIDTH - (multiple ? 2 * (ARROW_WIDTH + 2) : 0);

        if (multiple) {
            drawArrow(g, x, y, "<", mouseX, mouseY);
            drawArrow(g, x + LIST_WIDTH - ARROW_WIDTH, y, ">", mouseX, mouseY);
        }

        boolean hovered = multiple && inside(mouseX, mouseY, centerX, y, centerW, TAB_HEIGHT);
        cutRect(g, centerX, y, centerW, TAB_HEIGHT, 3, c.outline);
        cutRect(g, centerX + 1, y + 1, centerW - 2, TAB_HEIGHT - 2, 2, hovered ? c.accentLight : c.accent);

        Component label = Component.translatable(category.translationKey);
        int iconWidth = category == Category.ALL ? 11 : (category.iconItem != null ? 14 : 0);
        int contentWidth = iconWidth + font.width(label);
        int cursor = centerX + (centerW - contentWidth) / 2;
        if (category == Category.ALL) {
            drawEmblem(g, cursor + 4, y + TAB_HEIGHT / 2, 4);
        } else if (category.iconItem != null) {
            ItemStack icon = iconFor(category.iconItem);
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(cursor, y + 3, 0);
                g.pose().scale(0.75F, 0.75F, 1.0F);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
            }
        }
        g.drawString(font, label, cursor + iconWidth, y + 5, WHITE, true);

        if (multiple) {
            String page = (tabs.indexOf(category) + 1) + "/" + tabs.size();
            g.drawString(font, page, centerX + centerW - 5 - font.width(page), y + 5, c.headerSub, false);
        }
    }

    private void drawArrow(GuiGraphics g, int x, int y, String label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, ARROW_WIDTH, TAB_HEIGHT);
        cutRect(g, x, y, ARROW_WIDTH, TAB_HEIGHT, 3, c.outline);
        cutRect(g, x + 1, y + 1, ARROW_WIDTH - 2, TAB_HEIGHT - 2, 2, hovered ? c.accentLight : c.accentDark);
        g.drawString(font, label, x + (ARROW_WIDTH - font.width(label)) / 2, y + 5, WHITE, false);
    }

    private void drawList(GuiGraphics g, int px, int py, int mouseX, int mouseY) {
        int x = px + LIST_X;
        int y = py + LIST_Y;
        cutRect(g, x, y, LIST_WIDTH, LIST_HEIGHT, 3, c.bodyLine);
        cutRect(g, x + 1, y + 1, LIST_WIDTH - 2, LIST_HEIGHT - 2, 2, c.bodyDark);

        if (visible.isEmpty()) {
            Component empty = Component.translatable(sellMode
                    ? "gui.cobblemon_economy_plus.sell_empty"
                    : "gui.cobblemon_economy_plus.boutique_empty");
            g.drawString(font, empty, x + (LIST_WIDTH - font.width(empty)) / 2, y + LIST_HEIGHT / 2 - 4, c.inkMuted, false);
            return;
        }

        for (int local = 0; local < VISIBLE_ROWS; local++) {
            int pos = scrollRow + local;
            if (pos >= visible.size()) {
                break;
            }
            EconomyPayloads.ShopEntryPayload entry = current.get(visible.get(pos));
            int rowX = x + 2;
            int rowY = y + 2 + local * ROW_HEIGHT;
            int rowW = LIST_WIDTH - 4 - (maxScroll() > 0 ? 5 : 0);
            boolean isSelected = pos == selectedPos;
            boolean hovered = inside(mouseX, mouseY, rowX, rowY, rowW, ROW_HEIGHT - 1);
            // Selling never needs funds, so a sale price is always shown as "fine".
            boolean affordable = sellMode || displayedBalance + 0.5 >= entry.price();

            if (isSelected) {
                cutRect(g, rowX, rowY, rowW, ROW_HEIGHT - 1, 2, c.outline);
                cutRect(g, rowX + 1, rowY + 1, rowW - 2, ROW_HEIGHT - 3, 1, c.selected);
                g.fill(rowX + 1, rowY + 2, rowX + 4, rowY + ROW_HEIGHT - 4, c.accent);
            } else {
                g.fill(rowX, rowY, rowX + rowW, rowY + ROW_HEIGHT - 1,
                        hovered ? WHITE : (local % 2 == 0 ? c.body : mix(c.body, c.bodyDark, 0.5F)));
            }

            ItemStack stack = stackFor(entry);
            int iconX = rowX + 7;
            int iconY = rowY + (ROW_HEIGHT - 1 - 16) / 2;
            if (!stack.isEmpty()) {
                g.renderItem(stack, iconX, iconY);
            }

            String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
            String price = EconomyFormat.money(entry.price());
            int priceWidth = font.width(price) + 10;
            g.drawString(font, abbreviate(name, rowW - 28 - priceWidth - 6), rowX + 28, rowY + 4, c.ink, false);
            String secondLine = sellMode
                    ? Component.translatable("gui.cobblemon_economy_plus.sell_owned", ownedCount(entry)).getString()
                    : "×" + entry.amount();
            g.drawString(font, secondLine, rowX + 28, rowY + 13, c.inkMuted, false);

            int priceX = rowX + rowW - 6 - font.width(price);
            drawCoin(g, priceX - 7, rowY + 11, 3);
            g.drawString(font, price, priceX, rowY + 8, affordable ? c.ink : 0xFFC0392B, false);
        }

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = x + LIST_WIDTH - 6;
            int trackY = y + 3;
            int trackH = LIST_HEIGHT - 6;
            int thumbH = Math.max(14, trackH * VISIBLE_ROWS / visible.size());
            int thumbY = trackY + Math.round((float) (trackH - thumbH) * scrollRow / maxScroll);
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, c.bodyLine);
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, c.accent);
        }
    }

    private void drawDetail(GuiGraphics g, int px, int py, int mouseX, int mouseY) {
        int x = px + DETAIL_X;
        int y = py + DETAIL_Y;
        cutRect(g, x, y, DETAIL_WIDTH, DETAIL_HEIGHT, 4, c.outline);
        cutRect(g, x + 2, y + 2, DETAIL_WIDTH - 4, DETAIL_HEIGHT - 4, 3, c.pane);

        drawBackdrop(g, x + DETAIL_WIDTH / 2, y + 31, 27);

        EconomyPayloads.ShopEntryPayload entry = selectedEntry();
        if (entry == null) {
            return;
        }

        ItemStack stack = stackFor(entry);
        if (!stack.isEmpty()) {
            float bob = (float) Math.sin(Util.getMillis() / 380.0D) * 1.5F;
            g.pose().pushPose();
            g.pose().translate(x + DETAIL_WIDTH / 2 - 24, y + 7 + bob, 0);
            g.pose().scale(3.0F, 3.0F, 1.0F);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }

        String name = stack.isEmpty() ? entry.itemId() : stack.getHoverName().getString();
        List<FormattedCharSequence> lines = font.split(FormattedText.of(name), DETAIL_WIDTH - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            int w = font.width(lines.get(i));
            g.drawString(font, lines.get(i), x + (DETAIL_WIDTH - w) / 2, y + 58 + i * 10, WHITE, true);
        }

        int muted = 0xFFB7C0D1;
        int nameLines = Math.min(2, lines.size());
        int infoY = y + (nameLines == 1 ? 74 : 80);
        g.drawString(font, sellMode
                        ? Component.translatable("gui.cobblemon_economy_plus.sell_owned_detail", ownedCount(entry))
                        : Component.translatable("gui.cobblemon_economy_plus.boutique_bundle", entry.amount()),
                x + 8, infoY, muted, false);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.boutique_unit_price"),
                x + 8, infoY + 10, muted, false);
        String unitPrice = EconomyFormat.money(entry.price());
        g.drawString(font, unitPrice, x + DETAIL_WIDTH - 8 - font.width(unitPrice), infoY + 10, GOLD, false);
        drawCoin(g, x + DETAIL_WIDTH - 8 - font.width(unitPrice) - 7, infoY + 14, 3);

        int stepperY = y + 102;
        int max = maxQuantity(entry);
        boolean minusHover = inside(mouseX, mouseY, minusX(px), py + DETAIL_Y + 102, 16, 16);
        boolean plusHover = inside(mouseX, mouseY, plusX(px), py + DETAIL_Y + 102, 16, 16);
        drawStepperButton(g, minusX(px), stepperY, "-", quantity > 1, minusHover);
        drawStepperButton(g, plusX(px), stepperY, "+", quantity < max, plusHover);
        int boxX = x + 8 + 20;
        int boxW = DETAIL_WIDTH - 16 - 40;
        cutRect(g, boxX, stepperY, boxW, 16, 2, c.paneDark);
        String q = quantity + (quantity >= max ? " MAX" : "");
        g.drawString(font, q, boxX + (boxW - font.width(q)) / 2, stepperY + 4, WHITE, false);

        long total = totalPrice(entry);
        // Selling always "affords" itself; buying needs enough coins.
        boolean affordable = sellMode || displayedBalance + 0.5 >= total;
        g.drawString(font, Component.translatable(sellMode
                        ? "gui.cobblemon_economy_plus.sell_proceeds"
                        : "gui.cobblemon_economy_plus.boutique_total"),
                x + 8, y + 124, muted, false);
        String totalText = EconomyFormat.money(total);
        g.drawString(font, totalText, x + DETAIL_WIDTH - 8 - font.width(totalText), y + 124, affordable ? GOLD : 0xFFFF8A80, true);
        drawCoin(g, x + DETAIL_WIDTH - 8 - font.width(totalText) - 7, y + 128, 3);

        long after = sellMode ? ClientEconomy.balance() + total : ClientEconomy.balance() - total;
        g.drawString(font, Component.translatable(sellMode
                        ? "gui.cobblemon_economy_plus.sell_after"
                        : "gui.cobblemon_economy_plus.boutique_after"),
                x + 8, y + 134, 0xFF8E97A8, false);
        String afterText = after >= 0 ? EconomyFormat.money(after) : "-" + EconomyFormat.money(-after);
        g.drawString(font, afterText, x + DETAIL_WIDTH - 8 - font.width(afterText), y + 134,
                after >= 0 ? muted : 0xFFFF8A80, false);

        int bx = x + 8;
        int by = y + 146;
        int bw = DETAIL_WIDTH - 16;
        boolean hover = affordable && inside(mouseX, mouseY, bx, by, bw, 14);
        cutRect(g, bx, by, bw, 14, 3, affordable ? c.outline : c.paneDark);
        // Selling uses an amber button so the two modes are easy to tell apart.
        int buttonColor = sellMode ? (hover ? 0xFFF5C25B : 0xFFE0A93A) : (hover ? 0xFF6BD08A : GREEN);
        int buttonEdge = sellMode ? 0xFF9C7A1E : GREEN_DARK;
        cutRect(g, bx + 1, by + 1, bw - 2, 12, 2, affordable ? buttonColor : c.paneLight);
        if (affordable) {
            g.fill(bx + 3, by + 11, bx + bw - 3, by + 12, buttonEdge);
        }
        Component label = Component.translatable(sellMode
                ? "gui.cobblemon_economy_plus.sell_button"
                : (affordable ? "gui.cobblemon_economy_plus.boutique_buy"
                        : "gui.cobblemon_economy_plus.boutique_cant_afford"));
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 3, affordable ? WHITE : 0xFF8A93A6, affordable);
    }

    private void drawFooter(GuiGraphics g, int px, int py) {
        int x = px + 10;
        int y = py + FOOTER_Y + 4;
        cutRect(g, x, y, 112, 16, 3, c.outline);
        cutRect(g, x + 1, y + 1, 110, 14, 2, c.pane);
        drawCoin(g, x + 9, y + 8, 5);
        String balance = EconomyFormat.money(Math.round(displayedBalance));
        g.drawString(font, balance, x + 108 - font.width(balance), y + 4, GOLD, true);

        Component hint = Component.translatable("gui.cobblemon_economy_plus.boutique_hint");
        g.drawString(font, abbreviate(hint.getString(), PANEL_WIDTH - 140), x + 120, y + 4, c.inkMuted, false);
    }

    private void drawFloatingTexts(GuiGraphics g, int px, int py) {
        long now = Util.getMillis();
        floatingTexts.removeIf(t -> now - t.startMillis > 1400L);
        for (FloatingText text : floatingTexts) {
            float progress = (now - text.startMillis) / 1400.0F;
            int alpha = (int) (255 * (1.0F - progress * progress));
            int color = (Math.max(8, Math.min(255, alpha)) << 24) | (text.color & 0xFFFFFF);
            g.drawString(font, text.text, px + 14, py + FOOTER_Y - (int) (progress * 22), color, true);
        }
    }

    // ------------------------------------------------------------------ emblems & backdrops

    private void drawEmblem(GuiGraphics g, int cx, int cy, int r) {
        switch (c.emblem) {
            case BALL -> drawBall(g, cx, cy, r);
            case DISC -> {
                discFill(g, cx, cy, r, c.outline);
                discFill(g, cx, cy, r - 1, c.accent);
                discFill(g, cx, cy, Math.max(1, r * 2 / 3), c.accentLight);
                discFill(g, cx, cy, Math.max(1, r / 2), c.accent);
                discFill(g, cx, cy, Math.max(1, r / 4 + 1), c.outline);
                discFill(g, cx, cy, Math.max(0, r / 4), c.body);
            }
            case BERRY -> {
                int br = r >= 6 ? r - 2 : r;
                discFill(g, cx, cy + (r >= 6 ? 1 : 0), br, 0xFF7A1F2B);
                discFill(g, cx, cy + (r >= 6 ? 1 : 0), br - 1, 0xFFD8393B);
                int hl = Math.max(1, br / 4);
                g.fill(cx - br / 2, cy - br / 2 + 1, cx - br / 2 + hl, cy - br / 2 + 1 + hl, 0xFFFF9A96);
                int leafTop = cy + (r >= 6 ? 1 : 0) - br - 1;
                g.fill(cx - 1, leafTop, cx + 1 + Math.max(1, r / 3), leafTop + Math.max(2, r / 4), GREEN);
                g.fill(cx - 1, leafTop + 1, cx, leafTop + Math.max(3, r / 3), GREEN_DARK);
            }
            case STONE -> {
                blockBase(g, cx, cy, r - 1, c.accent);
                speckleBlock(g, cx, cy, r - 1, c.speckleA, c.speckleB);
            }
            case LOG -> {
                int half = r - 1;
                blockBase(g, cx, cy, half, c.accentLight);
                boolean dark = true;
                for (int inset = Math.max(2, half / 3); inset < half - 1; inset += Math.max(1, half / 4)) {
                    int s = half - inset;
                    squareFrame(g, cx, cy, s, dark ? c.accentDark : c.accent);
                    dark = !dark;
                }
            }
            case GRASS -> {
                int half = r - 1;
                blockBase(g, cx, cy, half, c.accent);
                speckleBlock(g, cx, cy, half, c.speckleA, c.speckleB);
                int top = Math.max(2, half * 2 / 3);
                g.fill(cx - half + 1, cy - half + 1, cx + half, cy - half + 1 + top / 2 + 1, GRASS);
                g.fill(cx - half + 1, cy - half + 1 + top / 2 + 1, cx + half, cy - half + 2 + top / 2 + 1, GRASS_DARK);
            }
            case ORE -> {
                int half = r - 1;
                blockBase(g, cx, cy, half, c.accent);
                int s = Math.max(1, half / 3);
                int[][] gems = {{-half / 2, -half / 2}, {half / 4, -half / 3}, {-half / 3, half / 4}, {half / 2, half / 2 - 1}};
                for (int i = 0; i < gems.length; i++) {
                    int gx = cx + gems[i][0];
                    int gy = cy + gems[i][1];
                    g.fill(gx, gy, gx + s + 1, gy + s + 1, i % 2 == 0 ? c.speckleA : c.speckleB);
                }
            }
            case IRON -> {
                int half = r - 1;
                blockBase(g, cx, cy, half, c.accentLight);
                squareFrame(g, cx, cy, Math.max(1, half - 2), c.accent);
                if (half > 5) {
                    squareFrame(g, cx, cy, half - 3, c.accentDark);
                }
            }
        }
    }

    /** Poke Ball from scanlines; the lid colour is the theme accent. */
    private void drawBall(GuiGraphics g, int cx, int cy, int r) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            int innerR = r - 1;
            int inner = Math.abs(dy) > innerR ? -1 : (int) Math.round(Math.sqrt((double) innerR * innerR - (double) dy * dy));
            g.fill(cx - outer, cy + dy, cx + outer + 1, cy + dy + 1, c.outline);
            if (inner >= 0) {
                boolean band = Math.abs(dy) <= Math.max(0, r / 6);
                if (!band) {
                    g.fill(cx - inner, cy + dy, cx + inner + 1, cy + dy + 1, dy < 0 ? c.accent : WHITE);
                }
            }
        }
        int button = Math.max(1, r / 3);
        discFill(g, cx, cy, button + 1, c.outline);
        discFill(g, cx, cy, Math.max(0, button - 1), WHITE);

        if (c.ballMark != 0) {
            int markColor = switch (c.ballMark) {
                case 1 -> 0xFFD8393B;
                case 2 -> 0xFFF2C94C;
                default -> 0xFFF06AB0;
            };
            int w = Math.max(2, r / 3);
            int h = Math.max(1, r / 3);
            int offX = Math.max(2, r * 55 / 100);
            int markY = cy - Math.max(2, r * 55 / 100);
            g.fill(cx - offX - w / 2, markY, cx - offX - w / 2 + w, markY + h, markColor);
            g.fill(cx + offX - w / 2, markY, cx + offX - w / 2 + w, markY + h, markColor);
            if (c.ballMark == 2 && r >= 6) {
                g.fill(cx - 1, cy - r + 2, cx + 1, cy - r + 2 + Math.max(2, r / 3), markColor);
            }
        } else if (r >= 8) {
            g.fill(cx - r / 2, cy - r / 2 - 1, cx - r / 4, cy - r / 2, c.accentLight);
        }
    }

    /** Dim shape behind the showcase item: a ball for ball themes, a ringed disc otherwise. */
    private void drawBackdrop(GuiGraphics g, int cx, int cy, int r) {
        if (c.emblem == BoutiqueTheme.Emblem.BALL) {
            int lower = mix(c.pane, c.paneLight, 0.5F);
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
                int color = Math.abs(dy) <= 2 ? c.paneDark : (dy < 0 ? c.paneLight : lower);
                g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
            }
            discFill(g, cx, cy, 8, c.paneDark);
            discFill(g, cx, cy, 5, c.pane);
        } else {
            discFill(g, cx, cy, r, c.paneDark);
            discFill(g, cx, cy, r - 2, c.paneLight);
            discFill(g, cx, cy, r - 6, c.pane);
            if (c.speckleA != 0) {
                // A few themed flecks so block/berry themes feel textured.
                for (int i = 0; i < 10; i++) {
                    int fx = cx + ((i * 37) % (2 * r - 10)) - (r - 5);
                    int fy = cy + ((i * 53) % (2 * r - 10)) - (r - 5);
                    g.fill(fx, fy, fx + 2, fy + 2, mix(c.pane, i % 2 == 0 ? c.speckleA : c.speckleB, 0.35F));
                }
            }
        }
    }

    private void blockBase(GuiGraphics g, int cx, int cy, int half, int base) {
        g.fill(cx - half - 1, cy - half - 1, cx + half + 2, cy + half + 2, c.outline);
        g.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, base);
        g.fill(cx - half, cy - half, cx + half + 1, cy - half + 1, mix(base, WHITE, 0.3F));
        g.fill(cx - half, cy - half, cx - half + 1, cy + half + 1, mix(base, WHITE, 0.3F));
        g.fill(cx - half, cy + half, cx + half + 1, cy + half + 1, mix(base, c.outline, 0.4F));
        g.fill(cx + half, cy - half, cx + half + 1, cy + half + 1, mix(base, c.outline, 0.4F));
    }

    private void squareFrame(GuiGraphics g, int cx, int cy, int half, int color) {
        g.fill(cx - half, cy - half, cx + half + 1, cy - half + 1, color);
        g.fill(cx - half, cy + half, cx + half + 1, cy + half + 1, color);
        g.fill(cx - half, cy - half, cx - half + 1, cy + half + 1, color);
        g.fill(cx + half, cy - half, cx + half + 1, cy + half + 1, color);
    }

    private void speckleBlock(GuiGraphics g, int cx, int cy, int half, int a, int b) {
        if (a == 0) {
            return;
        }
        int cell = half >= 8 ? 2 : 1;
        speckle(g, cx - half + 1, cy - half + 1, half * 2 - 1, half * 2 - 1, cell, a, b, 11);
    }

    /** Deterministic sparse fleck texture; positions are stable between frames. */
    private void speckle(GuiGraphics g, int x, int y, int w, int h, int cell, int a, int b, int seed) {
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = 0; xx < w; xx += cell) {
                int value = hash(xx + seed * 31, yy + seed * 17) & 63;
                if (value < 3) {
                    g.fill(x + xx, y + yy, x + xx + cell, y + yy + cell, a);
                } else if (value < 6 && b != 0) {
                    g.fill(x + xx, y + yy, x + xx + cell, y + yy + cell, b);
                }
            }
        }
    }

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static int mix(int from, int to, float amount) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - amount) + ((to >> 16) & 0xFF) * amount);
        int gr = (int) (((from >> 8) & 0xFF) * (1 - amount) + ((to >> 8) & 0xFF) * amount);
        int b = (int) ((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private void drawCoin(GuiGraphics g, int cx, int cy, int r) {
        discFill(g, cx, cy, r, GOLD_DARK);
        discFill(g, cx, cy, Math.max(1, r - 1), GOLD);
        if (r >= 4) {
            g.fill(cx - 1, cy - r + 2, cx + 1, cy + r - 1, GOLD_DARK);
        }
    }

    private void discFill(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    private void drawStepperButton(GuiGraphics g, int x, int y, String label, boolean enabled, boolean hovered) {
        cutRect(g, x, y, 16, 16, 2, enabled ? c.outline : c.paneDark);
        cutRect(g, x + 1, y + 1, 14, 14, 1, enabled ? (hovered ? c.accentLight : c.accent) : c.paneLight);
        g.drawString(font, label, x + (16 - font.width(label)) / 2, y + 4, enabled ? WHITE : 0xFF6A7387, false);
    }

    private static void cutRect(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + w, y + h - cut, color);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int px = panelX();
        int py = panelY();

        if (inside(mouseX, mouseY, closeX(px), py + 9, 16, 16)) {
            onClose();
            return true;
        }

        if (canSell()) {
            if (inside(mouseX, mouseY, modeBuyX(px), py + 10, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT)) {
                setSellMode(false);
                return true;
            }
            if (inside(mouseX, mouseY, modeSellX(px), py + 10, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT)) {
                setSellMode(true);
                return true;
            }
        }

        if (!sellMode && tabs.size() > 1 && inside(mouseX, mouseY, px + LIST_X, py + TAB_Y, LIST_WIDTH, TAB_HEIGHT)) {
            boolean left = mouseX < px + LIST_X + ARROW_WIDTH;
            cycleCategory(left ? -1 : 1);
            return true;
        }

        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            selectPosition(row);
            return true;
        }

        EconomyPayloads.ShopEntryPayload entry = selectedEntry();
        if (entry != null) {
            if (inside(mouseX, mouseY, minusX(px), py + DETAIL_Y + 102, 16, 16)) {
                changeQuantity(hasShiftDown() ? -5 : -1);
                return true;
            }
            if (inside(mouseX, mouseY, plusX(px), py + DETAIL_Y + 102, 16, 16)) {
                changeQuantity(hasShiftDown() ? 5 : 1);
                return true;
            }
            if (inside(mouseX, mouseY, px + DETAIL_X + 8 + 20, py + DETAIL_Y + 102, DETAIL_WIDTH - 16 - 40, 16)) {
                // Clicking the quantity box jumps to the maximum (or back to 1): handy for "sell everything".
                int max = maxQuantity(entry);
                quantity = quantity >= max ? 1 : max;
                playUiSound(CobblemonSounds.GUI_CLICK);
                return true;
            }
            if (inside(mouseX, mouseY, px + DETAIL_X + 8, py + DETAIL_Y + 146, DETAIL_WIDTH - 16, 14)) {
                buy();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int px = panelX();
        int py = panelY();
        if (inside(mouseX, mouseY, px + LIST_X, py + TAB_Y, LIST_WIDTH, TAB_HEIGHT)) {
            cycleCategory(scrollY > 0 ? -1 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, px + LIST_X, py + LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            int next = Math.max(0, Math.min(scrollRow + (scrollY > 0 ? -1 : 1), maxScroll()));
            if (next != scrollRow) {
                scrollRow = next;
                playUiSound(CobblemonSounds.GUI_CLICK);
            }
            return true;
        }
        if (inside(mouseX, mouseY, px + DETAIL_X, py + DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT)) {
            changeQuantity(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                selectPosition(selectedPos - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                selectPosition(selectedPos + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                changeQuantity(hasShiftDown() ? -5 : -1);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                changeQuantity(hasShiftDown() ? 5 : 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                buy();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                cycleCategory(hasShiftDown() ? -1 : 1);
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ state

    private void trackBalance() {
        long balance = ClientEconomy.balance();
        if (balance != lastBalance) {
            long delta = balance - lastBalance;
            if (delta < 0) {
                floatingTexts.add(new FloatingText("-" + EconomyFormat.money(-delta), 0xFFFF8A80, Util.getMillis()));
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            } else {
                floatingTexts.add(new FloatingText("+" + EconomyFormat.money(delta), 0xFF8AF0A6, Util.getMillis()));
            }
            lastBalance = balance;
        }
        double diff = balance - displayedBalance;
        displayedBalance += Math.abs(diff) < 1.0D ? diff : diff * 0.18D;
    }

    /** The main action: buys the selected entry, or sells the selected item, depending on the mode. */
    private void buy() {
        EconomyPayloads.ShopEntryPayload entry = selectedEntry();
        if (entry == null) {
            return;
        }
        if (sellMode) {
            if (ownedCount(entry) < quantity) {
                playUiSound(CobblemonSounds.GUI_CLICK);
                return;
            }
            PlatformNetworking.sendSellAction(new EconomyPayloads.SellActionPayload(
                    shop.shopId(), entry.itemId(), quantity, entry.price()));
            return;
        }
        if (ClientEconomy.balance() < totalPrice(entry)) {
            playUiSound(CobblemonSounds.GUI_CLICK);
            return;
        }
        PlatformNetworking.sendShopAction(new EconomyPayloads.ShopActionPayload(
                shop.shopId(), visible.get(selectedPos), quantity,
                entry.itemId(), entry.amount(), entry.price()));
    }

    // ------------------------------------------------------------------ sell mode

    private int ownedCount(EconomyPayloads.ShopEntryPayload entry) {
        return owned.getOrDefault(entry.itemId(), 0);
    }

    private void setSellMode(boolean sell) {
        if (sell == sellMode || (sell && !canSell())) {
            return;
        }
        sellMode = sell;
        scrollRow = 0;
        if (sell) {
            category = Category.ALL;
            refreshSellEntries();
        } else {
            current = shop.entries();
        }
        rebuildVisible(-1);
        playUiSound(CobblemonSounds.GUI_CLICK);
    }

    /**
     * Rebuilds the sell list from the player's inventory: only items this shop buys, and only pristine stacks
     * in the main inventory (matching what the server will actually take).
     */
    private void refreshSellEntries() {
        owned.clear();
        Minecraft minecraft = Minecraft.getInstance();
        List<EconomyPayloads.ShopEntryPayload> list = new ArrayList<>();
        if (minecraft.player != null) {
            for (EconomyPayloads.ShopEntryPayload offer : shop.sellEntries()) {
                ResourceLocation id = ResourceLocation.tryParse(offer.itemId());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                    continue;
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                int count = 0;
                for (ItemStack stack : minecraft.player.getInventory().items) {
                    if (SellableStacks.isPristine(stack, item)) {
                        count += stack.getCount();
                    }
                }
                if (count > 0) {
                    owned.put(offer.itemId(), count);
                    list.add(offer);
                }
            }
        }
        current = list;
    }

    @Override
    public void tick() {
        super.tick();
        // Keep the sell list in step with the real inventory (sales, pickups, drops).
        if (sellMode && ++ticks % 10 == 0) {
            EconomyPayloads.ShopEntryPayload selected = selectedEntry();
            String selectedId = selected == null ? null : selected.itemId();
            int oldQuantity = quantity;
            refreshSellEntries();
            visible.clear();
            for (int i = 0; i < current.size(); i++) {
                visible.add(i);
            }
            selectedPos = 0;
            for (int i = 0; i < current.size(); i++) {
                if (current.get(i).itemId().equals(selectedId)) {
                    selectedPos = i;
                    break;
                }
            }
            scrollRow = Math.max(0, Math.min(scrollRow, maxScroll()));
            EconomyPayloads.ShopEntryPayload now = selectedEntry();
            quantity = now == null ? 1 : Math.max(1, Math.min(oldQuantity, maxQuantity(now)));
        }
    }

    private void changeQuantity(int delta) {
        EconomyPayloads.ShopEntryPayload entry = selectedEntry();
        if (entry == null) {
            return;
        }
        int next = Math.max(1, Math.min(quantity + delta, maxQuantity(entry)));
        if (next != quantity) {
            quantity = next;
            playUiSound(CobblemonSounds.GUI_CLICK);
        }
    }

    private void cycleCategory(int direction) {
        if (sellMode || tabs.size() < 2) {
            return;
        }
        category = tabs.get(Math.floorMod(tabs.indexOf(category) + direction, tabs.size()));
        scrollRow = 0;
        rebuildVisible(-1);
        playUiSound(CobblemonSounds.GUI_CLICK);
    }

    private void selectPosition(int pos) {
        if (pos < 0 || pos >= visible.size()) {
            return;
        }
        if (pos != selectedPos) {
            selectedPos = pos;
            quantity = 1;
            playUiSound(CobblemonSounds.GUI_CLICK);
        }
        if (pos < scrollRow) {
            scrollRow = pos;
        } else if (pos >= scrollRow + VISIBLE_ROWS) {
            scrollRow = pos - VISIBLE_ROWS + 1;
        }
    }

    private void rebuildVisible(int keepEntry) {
        visible.clear();
        for (int i = 0; i < current.size(); i++) {
            if (sellMode || category == Category.ALL || Category.of(current.get(i).itemId()) == category) {
                visible.add(i);
            }
        }
        selectedPos = Math.max(0, visible.indexOf(keepEntry));
        quantity = 1;
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private EconomyPayloads.ShopEntryPayload selectedEntry() {
        if (selectedPos < 0 || selectedPos >= visible.size()) {
            return null;
        }
        return current.get(visible.get(selectedPos));
    }

    private int maxScroll() {
        return Math.max(0, visible.size() - VISIBLE_ROWS);
    }

    private int maxQuantity(EconomyPayloads.ShopEntryPayload entry) {
        if (sellMode) {
            // A whole main inventory at most (36 stacks of 64), matching the server's limit.
            return Math.max(1, Math.min(ownedCount(entry), 36 * 64));
        }
        return Math.max(1, 64 / Math.max(1, entry.amount()));
    }

    private long totalPrice(EconomyPayloads.ShopEntryPayload entry) {
        return entry.price() * quantity;
    }

    private int rowAt(double mouseX, double mouseY) {
        int px = panelX();
        int py = panelY();
        for (int local = 0; local < VISIBLE_ROWS; local++) {
            int pos = scrollRow + local;
            if (pos >= visible.size()) {
                return -1;
            }
            if (inside(mouseX, mouseY, px + LIST_X + 2, py + LIST_Y + 2 + local * ROW_HEIGHT,
                    LIST_WIDTH - 4 - (maxScroll() > 0 ? 5 : 0), ROW_HEIGHT - 1)) {
                return pos;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ misc

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private int closeX(int px) {
        return px + PANEL_WIDTH - 26;
    }

    private int minusX(int px) {
        return px + DETAIL_X + 8;
    }

    private int plusX(int px) {
        return px + DETAIL_X + DETAIL_WIDTH - 8 - 16;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String abbreviate(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end == 0 ? suffix : value.substring(0, end) + suffix;
    }

    private void playUiSound(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    private static ItemStack stackFor(EconomyPayloads.ShopEntryPayload entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id), entry.amount());
    }

    private static ItemStack iconFor(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private record FloatingText(String text, int color, long startMillis) {
    }

    /** Categories are derived from item ids so shops.json needs no extra metadata. */
    private enum Category {
        ALL("gui.cobblemon_economy_plus.cat_all", null),
        BALLS("gui.cobblemon_economy_plus.cat_balls", "cobblemon:poke_ball"),
        MEDICINE("gui.cobblemon_economy_plus.cat_medicine", "cobblemon:potion"),
        STONES("gui.cobblemon_economy_plus.cat_stones", "cobblemon:fire_stone"),
        OTHER("gui.cobblemon_economy_plus.cat_other", "cobblemon:rare_candy");

        private final String translationKey;
        private final String iconItem;

        Category(String translationKey, String iconItem) {
            this.translationKey = translationKey;
            this.iconItem = iconItem;
        }

        static Category of(String itemId) {
            String lower = itemId.toLowerCase(Locale.ROOT);
            boolean modded = !lower.startsWith("minecraft:");
            if (lower.endsWith("_ball") && modded) {
                return BALLS;
            }
            if (modded && lower.endsWith("_stone")) {
                return STONES;
            }
            if (modded && (lower.contains("potion") || lower.contains("revive") || lower.contains("heal")
                    || lower.contains("antidote") || lower.contains("awakening") || lower.contains("ether")
                    || lower.contains("elixir") || lower.contains("restore"))) {
                return MEDICINE;
            }
            return OTHER;
        }
    }
}
