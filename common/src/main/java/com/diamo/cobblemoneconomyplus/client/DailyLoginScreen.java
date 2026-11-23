package com.diamo.cobblemoneconomyplus.client;

import com.cobblemon.mod.common.CobblemonSounds;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The login bonus calendar: every day of the bonus with its coins and items, the days already received ticked off
 * and today highlighted. Opened by the server when the bonus is paid, and by {@code /cep daily}.
 */
public final class DailyLoginScreen extends Screen {
    private static final int COLUMNS = 7;
    /** Up to four weeks at once; fewer rows (and page buttons) when the window is too small. */
    private static final int MAX_ROWS = 4;
    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 46;
    private static final int GAP = 4;
    private static final int GRID_WIDTH = COLUMNS * CELL_WIDTH + (COLUMNS - 1) * GAP;
    private static final int WIDTH = GRID_WIDTH + 24;
    private static final int HEADER = 28;

    private static final int OUTLINE = 0xFF16181D;
    private static final int RED = 0xFFD8393B;
    private static final int RED_DARK = 0xFF9E2428;
    private static final int RED_LIGHT = 0xFFF06A66;
    private static final int BODY = 0xFFF1EDE2;
    private static final int LINE = 0xFFC9C3B0;
    private static final int INK = 0xFF2A2E38;
    private static final int MUTED = 0xFF7A7C85;
    private static final int GOLD = 0xFFF2C94C;
    private static final int GOLD_DARK = 0xFF9C7A1E;
    private static final int GOLD_TEXT = 0xFF9C6B00;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int CELL_FUTURE = 0xFFF8F5EC;
    private static final int CELL_CLAIMED = 0xFFDCD6C5;
    private static final int CELL_TODAY = 0xFFFFF1C2;
    private static final int CHECK = 0xFF2E9E4F;

    private final EconomyPayloads.DailyLoginPayload payload;
    private int rows;
    private int perPage;
    private int pages;
    private int page;
    private int left;
    private int top;
    private int panelHeight;
    private Button previous;
    private Button next;

    public DailyLoginScreen(EconomyPayloads.DailyLoginPayload payload) {
        super(Component.translatable("gui.cobblemon_economy_plus.daily_title"));
        this.payload = payload;
    }

    private static int panelHeight(int rows) {
        return HEADER + 20 + rows * CELL_HEIGHT + (rows - 1) * GAP + 46;
    }

    @Override
    protected void init() {
        int days = Math.max(1, payload.days().size());
        int weeks = (days + COLUMNS - 1) / COLUMNS;
        rows = Math.min(weeks, MAX_ROWS);
        while (rows > 1 && panelHeight(rows) > height - 8) {
            rows--;
        }
        perPage = rows * COLUMNS;
        pages = (days + perPage - 1) / perPage;
        page = payload.todayIndex() / perPage;
        panelHeight = panelHeight(rows);
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        int buttonY = top + panelHeight - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 40, buttonY, 80, 20).build());
        if (pages > 1) {
            previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                    .bounds(left + 12, buttonY, 20, 20).build());
            next = addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                    .bounds(left + WIDTH - 32, buttonY, 20, 20).build());
            updatePageButtons();
        }
    }

    @Override
    public void added() {
        super.added();
        Minecraft.getInstance().getSoundManager().play(payload.claimedNow()
                ? SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.6F)
                : SimpleSoundInstance.forUI(CobblemonSounds.PC_ON, 1.0F));
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(pages - 1, page + delta));
        updatePageButtons();
    }

    private void updatePageButtons() {
        previous.active = page > 0;
        next.active = page < pages - 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn in render() before the panel, so the widgets are not dimmed.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);

        cutRect(g, left, top, WIDTH, panelHeight, 5, OUTLINE);
        cutRect(g, left + 2, top + 2, WIDTH - 4, panelHeight - 4, 4, BODY);
        cutRect(g, left + 2, top + 2, WIDTH - 4, 24, 4, RED);
        g.fill(left + 6, top + 4, left + WIDTH - 6, top + 5, RED_LIGHT);
        g.fill(left + 2, top + 25, left + WIDTH - 2, top + 26, RED_DARK);
        g.fill(left + 2, top + 26, left + WIDTH - 2, top + 28, OUTLINE);
        drawCoin(g, left + 18, top + 14, 7);
        g.drawString(font, title, left + 32, top + 10, WHITE, true);
        if (pages > 1) {
            String pageText = (page + 1) + " / " + pages;
            g.drawString(font, pageText, left + WIDTH - 12 - font.width(pageText), top + 10, WHITE, true);
        }

        Component count = payload.resetOnMiss()
                ? Component.translatable("gui.cobblemon_economy_plus.daily_streak", payload.loginCount())
                : Component.translatable("gui.cobblemon_economy_plus.daily_count", payload.loginCount());
        g.drawString(font, count, (width - font.width(count)) / 2, top + HEADER + 6, INK, false);

        int gridTop = top + HEADER + 20;
        EconomyPayloads.DailyLoginDay hovered = null;
        int hoveredIndex = -1;
        int first = page * perPage;
        int last = Math.min(payload.days().size(), first + perPage);
        for (int index = first; index < last; index++) {
            int slot = index - first;
            int cellX = left + 12 + (slot % COLUMNS) * (CELL_WIDTH + GAP);
            int cellY = gridTop + (slot / COLUMNS) * (CELL_HEIGHT + GAP);
            drawDay(g, cellX, cellY, index, payload.days().get(index));
            if (mouseX >= cellX && mouseX < cellX + CELL_WIDTH && mouseY >= cellY && mouseY < cellY + CELL_HEIGHT) {
                hovered = payload.days().get(index);
                hoveredIndex = index;
            }
        }

        Component message = Component.translatable(payload.claimedNow()
                ? "gui.cobblemon_economy_plus.daily_claimed_now"
                : "gui.cobblemon_economy_plus.daily_claimed_before");
        int messageY = top + panelHeight - 42;
        g.drawString(font, message, (width - font.width(message)) / 2, messageY, payload.claimedNow() ? GOLD_TEXT : MUTED, false);

        super.render(g, mouseX, mouseY, partialTick);

        if (hovered != null) {
            g.renderComponentTooltip(font, tooltip(hoveredIndex, hovered), mouseX, mouseY);
        }
    }

    private void drawDay(GuiGraphics g, int x, int y, int index, EconomyPayloads.DailyLoginDay day) {
        boolean today = index == payload.todayIndex();
        boolean claimed = index <= payload.todayIndex();
        int border = today ? GOLD_DARK : LINE;
        cutRect(g, x, y, CELL_WIDTH, CELL_HEIGHT, 3, border);
        if (today) {
            cutRect(g, x + 1, y + 1, CELL_WIDTH - 2, CELL_HEIGHT - 2, 2, GOLD);
        }
        int inset = today ? 2 : 1;
        cutRect(g, x + inset, y + inset, CELL_WIDTH - 2 * inset, CELL_HEIGHT - 2 * inset, 2,
                today ? CELL_TODAY : claimed ? CELL_CLAIMED : CELL_FUTURE);

        String label = abbreviate(Component.translatable("gui.cobblemon_economy_plus.daily_day", index + 1).getString(),
                CELL_WIDTH - 4);
        g.drawString(font, label, x + (CELL_WIDTH - font.width(label)) / 2, y + 4, today ? GOLD_TEXT : MUTED, false);

        int iconX = x + (CELL_WIDTH - 16) / 2;
        int iconY = y + 14;
        if (!day.items().isEmpty()) {
            ItemStack stack = day.items().get(0);
            g.renderItem(stack, iconX, iconY);
            g.renderItemDecorations(font, stack, iconX, iconY);
            if (day.items().size() > 1) {
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 200.0F);
                g.drawString(font, "+", x + CELL_WIDTH - 8, iconY - 2, GOLD_TEXT, false);
                g.pose().popPose();
            }
        } else if (day.coins() > 0L) {
            drawCoin(g, x + CELL_WIDTH / 2, iconY + 8, 6);
        }
        if (day.coins() > 0L) {
            String coins = abbreviate(EconomyFormat.money(day.coins()), CELL_WIDTH - 4);
            g.drawString(font, coins, x + (CELL_WIDTH - font.width(coins)) / 2, y + 34, GOLD_TEXT, false);
        }

        if (claimed) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 250.0F);
            drawCheck(g, x + CELL_WIDTH - 11, y + CELL_HEIGHT - 10);
            g.pose().popPose();
        }
    }

    private List<Component> tooltip(int index, EconomyPayloads.DailyLoginDay day) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.cobblemon_economy_plus.daily_day", index + 1).withStyle(ChatFormatting.GOLD));
        if (day.coins() > 0L) {
            lines.add(Component.translatable("message.cobblemon_economy_plus.reward.coins", EconomyFormat.money(day.coins()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        for (ItemStack stack : day.items()) {
            lines.add(stack.getHoverName().copy().append(" ×" + stack.getCount()));
        }
        if (day.coins() <= 0L && day.items().isEmpty()) {
            lines.add(Component.translatable("gui.cobblemon_economy_plus.daily_nothing").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** A small green tick in a white circle, 9 × 9 pixels. */
    private void drawCheck(GuiGraphics g, int x, int y) {
        disc(g, x + 4, y + 4, 5, WHITE);
        g.fill(x + 1, y + 4, x + 3, y + 6, CHECK);
        g.fill(x + 2, y + 5, x + 4, y + 7, CHECK);
        g.fill(x + 4, y + 4, x + 6, y + 6, CHECK);
        g.fill(x + 5, y + 3, x + 7, y + 5, CHECK);
        g.fill(x + 6, y + 2, x + 8, y + 4, CHECK);
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
        while (end > 0 && font.width(value.substring(0, end) + "…") > maxWidth) {
            end--;
        }
        return value.substring(0, end) + "…";
    }
}
