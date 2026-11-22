package com.diamo.cobblemoneconomyplus.client;

import com.diamo.cobblemoneconomyplus.admin.ShopDraft;
import com.diamo.cobblemoneconomyplus.economy.EconomyFormat;
import com.diamo.cobblemoneconomyplus.network.EconomyPayloads;
import com.diamo.cobblemoneconomyplus.platform.PlatformNetworking;
import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopJson;
import com.diamo.cobblemoneconomyplus.shop.ShopThemes;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The in-game admin screen ({@code /cep admin}, or the Shop Editor's admin mode): shops, their entries (price, bundle
 * size, purchase limit, stock) and the base values of sellable items. Edits are sent to the server, which validates
 * them, writes shops.json / item_values.json (keeping a .bak) and reloads. Nothing here is trusted by the server.
 */
public final class AdminScreen extends Screen {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 232;
    private static final int ROW = 12;
    private static final int ENTRY_ROW = 18;
    private static final int SHOP_ROWS = 13;
    private static final int ENTRY_ROWS = 4;
    private static final int VALUE_ROWS = 8;
    private static final long CONFIRM_MILLIS = 3_000L;
    private static final int RED = 0xFFFF7A6E;
    private static final int GREEN = 0xFF8AF0A6;
    private static final int WHITE = 0xFFFFFFFF;

    private enum Tab { SHOPS, VALUES }

    private final BoutiqueTheme c = BoutiqueTheme.byId("iron");

    private int revision;
    private List<ShopDefinition> shops = List.of();
    private Map<ResourceLocation, Long> values = Map.of();

    private Tab tab = Tab.SHOPS;
    private ShopDraft draft;
    /** The draft's JSON when it was loaded, to tell whether there are unsaved changes. */
    private String draftBaseline = "";
    private int selectedEntry = -1;
    private int shopScroll;
    private int entryScroll;
    private boolean awaitingSave;

    private String valueFilter = "";
    private String selectedValue;
    private String valueInput = "";
    private int valueScroll;
    private String resetPlayer = "";

    private Component status = Component.empty();
    private boolean statusOk = true;
    private long statusAt;
    /** A destructive button pressed once; pressing it again within {@link #CONFIRM_MILLIS} confirms. */
    private String armed = "";
    private long armedAt;

    public AdminScreen(EconomyPayloads.AdminOpenPayload payload) {
        super(Component.translatable("gui.cobblemon_economy_plus.admin.title"));
        apply(payload);
    }

    // ------------------------------------------------------------------ data from the server

    /** New data from the server (first open, after a save, or after a conflict). */
    public void apply(EconomyPayloads.AdminOpenPayload payload) {
        revision = payload.revision();
        shops = ShopJson.readShops(payload.shopsJson());
        values = ShopJson.readItemValues(payload.itemValuesJson());

        String wanted = !payload.focusShopId().isEmpty() ? payload.focusShopId()
                : draft != null ? draft.id : (shops.isEmpty() ? null : shops.get(0).id());
        boolean keepEdits = draft != null && dirty() && !awaitingSave && payload.focusShopId().isEmpty();
        awaitingSave = false;
        if (!keepEdits) {
            ShopDefinition shop = find(wanted);
            if (shop == null && !shops.isEmpty()) {
                shop = shops.get(0);
            }
            loadDraft(shop == null ? null : ShopDraft.of(shop));
        }
        if (minecraft != null) {
            rebuildWidgets();
        }
    }

    public void showStatus(EconomyPayloads.AdminStatusPayload payload) {
        if (!payload.success()) {
            // A refused save keeps the edits: the server may now send fresh data (conflict), which must not replace them.
            awaitingSave = false;
        }
        setStatus(Component.translatable(payload.key(), payload.args().toArray()), payload.success());
    }

    private void setStatus(Component message, boolean ok) {
        status = message;
        statusOk = ok;
        statusAt = Util.getMillis();
    }

    private ShopDefinition find(String id) {
        if (id == null) {
            return null;
        }
        for (ShopDefinition shop : shops) {
            if (shop.id().equals(id)) {
                return shop;
            }
        }
        return null;
    }

    private void loadDraft(ShopDraft newDraft) {
        draft = newDraft;
        draftBaseline = draft == null ? "" : draftJson();
        selectedEntry = draft != null && !draft.entries.isEmpty() ? Math.min(Math.max(selectedEntry, 0), draft.entries.size() - 1) : -1;
        entryScroll = Math.max(0, Math.min(entryScroll, maxEntryScroll()));
    }

    private String draftJson() {
        ShopDraft.Result result = draft.toJson();
        return result.ok() ? result.json().toString() : "invalid:" + System.identityHashCode(result);
    }

    private boolean dirty() {
        return draft != null && (draft.isNew || !draftJson().equals(draftBaseline));
    }

    // ------------------------------------------------------------------ layout

    private int px() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int py() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int x = px();
        int y = py();
        addRenderableWidget(button(x + PANEL_WIDTH - 140, y + 3, 64, "gui.cobblemon_economy_plus.admin.tab_shops",
                () -> switchTab(Tab.SHOPS))).active = tab != Tab.SHOPS;
        addRenderableWidget(button(x + PANEL_WIDTH - 72, y + 3, 66, "gui.cobblemon_economy_plus.admin.tab_values",
                () -> switchTab(Tab.VALUES))).active = tab != Tab.VALUES;

        if (tab == Tab.SHOPS) {
            initShops(x, y);
        } else {
            initValues(x, y);
        }

        // Footer tools (they run the matching /cep commands, which check permissions on the server).
        int fy = y + PANEL_HEIGHT - 18;
        addRenderableWidget(button(x + 206, fy, 54, "gui.cobblemon_economy_plus.admin.restock", () -> {
            if (confirm("restock")) {
                runCommand("cep shop restock");
            }
        }));
        addRenderableWidget(textBox(x + 264, fy, 72, resetPlayer, 16, value -> resetPlayer = value,
                "gui.cobblemon_economy_plus.admin.player_hint"));
        addRenderableWidget(button(x + 340, fy, 54, "gui.cobblemon_economy_plus.admin.reset_limits", () -> {
            if (resetPlayer.isBlank()) {
                setStatus(Component.translatable("message.cobblemon_economy_plus.admin.need_player"), false);
            } else if (confirm("reset:" + resetPlayer)) {
                runCommand("cep shop limits reset " + resetPlayer.trim());
            }
        }));
    }

    private void initShops(int x, int y) {
        addRenderableWidget(button(x + 6, y + 194, 90, "gui.cobblemon_economy_plus.admin.new_shop", this::newShop));
        if (draft == null) {
            return;
        }
        int rx = x + 100;

        EditBox idBox = textBox(rx + 14, y + 22, 76, draft.id, 32, value -> draft.id = value.toLowerCase(Locale.ROOT), null);
        idBox.setEditable(draft.isNew);
        addRenderableWidget(idBox);
        addRenderableWidget(Button.builder(Component.translatable("theme.cobblemon_economy_plus." + draft.theme),
                b -> {
                    draft.theme = ShopThemes.cycle(draft.theme, hasShiftDown() ? -1 : 1);
                    b.setMessage(Component.translatable("theme.cobblemon_economy_plus." + draft.theme));
                }).bounds(rx + 132, y + 22, 76, 14).build());
        addRenderableWidget(textBox(rx + 254, y + 22, 40, draft.sellMultiplier, 8, value -> draft.sellMultiplier = value, null));

        addRenderableWidget(textBox(rx + 40, y + 38, 120, draft.text(draft.name, ShopDraft.EN), 96,
                value -> ShopDraft.setText(draft.name, ShopDraft.EN, value), "gui.cobblemon_economy_plus.admin.hint_en"));
        addRenderableWidget(textBox(rx + 166, y + 38, 128, draft.text(draft.name, ShopDraft.JA), 96,
                value -> ShopDraft.setText(draft.name, ShopDraft.JA, value), "gui.cobblemon_economy_plus.admin.hint_ja"));
        addRenderableWidget(textBox(rx + 40, y + 54, 120, draft.text(draft.subtitle, ShopDraft.EN), 96,
                value -> ShopDraft.setText(draft.subtitle, ShopDraft.EN, value), "gui.cobblemon_economy_plus.admin.hint_en"));
        addRenderableWidget(textBox(rx + 166, y + 54, 128, draft.text(draft.subtitle, ShopDraft.JA), 96,
                value -> ShopDraft.setText(draft.subtitle, ShopDraft.JA, value), "gui.cobblemon_economy_plus.admin.hint_ja"));

        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            ShopDraft.EntryDraft entry = draft.entries.get(selectedEntry);
            int ey = y + 156;
            addRenderableWidget(textBox(rx, ey, 58, entry.price, 19, value -> entry.price = value, null));
            addRenderableWidget(textBox(rx + 62, ey, 26, entry.amount, 2, value -> entry.amount = value, null));
            addRenderableWidget(textBox(rx + 92, ey, 36, entry.limitCount, 7, value -> entry.limitCount = value,
                    "gui.cobblemon_economy_plus.admin.hint_none"));
            addRenderableWidget(Button.builder(periodLabel(entry.period), b -> {
                entry.period = ShopDefinition.Period.values()[(entry.period.ordinal() + 1) % ShopDefinition.Period.values().length];
                b.setMessage(periodLabel(entry.period));
            }).bounds(rx + 130, ey, 46, 14).build());
            addRenderableWidget(textBox(rx + 180, ey, 36, entry.stockMax, 7, value -> entry.stockMax = value,
                    "gui.cobblemon_economy_plus.admin.hint_none"));
            addRenderableWidget(textBox(rx + 220, ey, 44, entry.restockMinutes, 6, value -> entry.restockMinutes = value,
                    "gui.cobblemon_economy_plus.admin.hint_zero"));
        }

        int by = y + 175;
        addRenderableWidget(button(rx, by, 92, "gui.cobblemon_economy_plus.admin.add_held", this::addHeldEntry));
        boolean hasEntry = selectedEntry >= 0;
        addRenderableWidget(button(rx + 96, by, 20, "gui.cobblemon_economy_plus.admin.up", () -> moveEntry(-1))).active = hasEntry;
        addRenderableWidget(button(rx + 118, by, 20, "gui.cobblemon_economy_plus.admin.down", () -> moveEntry(1))).active = hasEntry;
        addRenderableWidget(button(rx + 142, by, 70, "gui.cobblemon_economy_plus.admin.remove_entry", this::removeEntry)).active = hasEntry;

        addRenderableWidget(button(rx, y + 194, 70, "gui.cobblemon_economy_plus.admin.save", this::save));
        addRenderableWidget(button(rx + 74, y + 194, 70, "gui.cobblemon_economy_plus.admin.revert", this::revert));
        addRenderableWidget(button(rx + 214, y + 194, 80, "gui.cobblemon_economy_plus.admin.delete_shop", this::deleteShop))
                .active = !draft.isNew;
    }

    private void initValues(int x, int y) {
        addRenderableWidget(textBox(x + 8, y + 22, 180, valueFilter, 64, value -> {
            valueFilter = value;
            valueScroll = 0;
        }, "gui.cobblemon_economy_plus.admin.search"));
        addRenderableWidget(button(x + 192, y + 22, 100, "gui.cobblemon_economy_plus.admin.add_held_value", this::selectHeldValue));
        if (selectedValue != null) {
            addRenderableWidget(textBox(x + 200, y + 194, 80, valueInput, 13, value -> valueInput = value, null));
            addRenderableWidget(button(x + 284, y + 194, 52, "gui.cobblemon_economy_plus.admin.set_value", this::setValue));
            addRenderableWidget(button(x + 340, y + 194, 54, "gui.cobblemon_economy_plus.admin.remove_value", this::removeValue))
                    .active = values.containsKey(ResourceLocation.tryParse(selectedValue));
        }
    }

    private Button button(int x, int y, int width, String key, Runnable action) {
        return Button.builder(Component.translatable(key), b -> action.run()).bounds(x, y, width, 14).build();
    }

    private EditBox textBox(int x, int y, int width, String value, int maxLength, Consumer<String> responder, String hintKey) {
        EditBox box = new EditBox(font, x, y, width, 14, Component.empty());
        box.setMaxLength(maxLength);
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        if (hintKey != null) {
            box.setHint(Component.translatable(hintKey));
        }
        return box;
    }

    private static Component periodLabel(ShopDefinition.Period period) {
        return Component.translatable("gui.cobblemon_economy_plus.admin.period_" + period.id());
    }

    // ------------------------------------------------------------------ actions

    private void switchTab(Tab newTab) {
        tab = newTab;
        rebuildWidgets();
    }

    /** True on the second press of the same destructive button within a few seconds. */
    private boolean confirm(String action) {
        long now = Util.getMillis();
        if (action.equals(armed) && now - armedAt <= CONFIRM_MILLIS) {
            armed = "";
            return true;
        }
        armed = action;
        armedAt = now;
        setStatus(Component.translatable("message.cobblemon_economy_plus.admin.confirm"), false);
        return false;
    }

    private void selectShop(ShopDefinition shop) {
        if (draft != null && shop.id().equals(draft.id) && !draft.isNew) {
            return;
        }
        if (dirty() && !confirm("discard")) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.unsaved"), false);
            return;
        }
        selectedEntry = 0;
        entryScroll = 0;
        loadDraft(ShopDraft.of(shop));
        rebuildWidgets();
    }

    private void newShop() {
        if (dirty() && !confirm("discard")) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.unsaved"), false);
            return;
        }
        String id = "new_shop";
        for (int n = 2; find(id) != null; n++) {
            id = "new_shop_" + n;
        }
        selectedEntry = -1;
        entryScroll = 0;
        loadDraft(ShopDraft.blank(id));
        rebuildWidgets();
    }

    private void addHeldEntry() {
        ItemStack held = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.hold_item"), false);
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        int amount = Math.max(1, Math.min(64, held.getCount()));
        draft.entries.add(new ShopDraft.EntryDraft(itemId, Integer.toString(amount), "100"));
        selectedEntry = draft.entries.size() - 1;
        entryScroll = maxEntryScroll();
        rebuildWidgets();
    }

    private void moveEntry(int direction) {
        int target = selectedEntry + direction;
        if (selectedEntry < 0 || target < 0 || target >= draft.entries.size()) {
            return;
        }
        ShopDraft.EntryDraft moved = draft.entries.remove(selectedEntry);
        draft.entries.add(target, moved);
        selectedEntry = target;
        if (selectedEntry < entryScroll) {
            entryScroll = selectedEntry;
        } else if (selectedEntry >= entryScroll + ENTRY_ROWS) {
            entryScroll = selectedEntry - ENTRY_ROWS + 1;
        }
        rebuildWidgets();
    }

    private void removeEntry() {
        if (selectedEntry < 0) {
            return;
        }
        draft.entries.remove(selectedEntry);
        selectedEntry = Math.min(selectedEntry, draft.entries.size() - 1);
        entryScroll = Math.max(0, Math.min(entryScroll, maxEntryScroll()));
        rebuildWidgets();
    }

    private void save() {
        ShopDraft.Result result = draft.toJson();
        if (!result.ok()) {
            ShopDraft.Problem problem = result.problem();
            if (problem.entry() >= 0) {
                selectedEntry = problem.entry();
                rebuildWidgets();
            }
            setStatus(Component.translatable(problem.key(), problem.entry() + 1), false);
            return;
        }
        String json = result.json().toString();
        if (json.getBytes(StandardCharsets.UTF_8).length > EconomyPayloads.MAX_ADMIN_SHOP_JSON) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.too_large"), false);
            return;
        }
        awaitingSave = true;
        if (draft.isNew) {
            // Select the new shop once the server has created it.
            draft.id = result.json().get("id").getAsString();
        }
        PlatformNetworking.sendAdminSaveShop(new EconomyPayloads.AdminSaveShopPayload(revision,
                draft.isNew ? "" : draft.id, json));
        setStatus(Component.translatable("message.cobblemon_economy_plus.admin.saving"), true);
    }

    private void revert() {
        ShopDefinition shop = draft.isNew ? null : find(draft.id);
        if (shop == null && !shops.isEmpty()) {
            shop = shops.get(0);
        }
        loadDraft(shop == null ? null : ShopDraft.of(shop));
        rebuildWidgets();
    }

    private void deleteShop() {
        if (draft == null || draft.isNew || !confirm("delete:" + draft.id)) {
            return;
        }
        PlatformNetworking.sendAdminDeleteShop(new EconomyPayloads.AdminDeleteShopPayload(revision, draft.id));
        draft = null;
        awaitingSave = true;
        rebuildWidgets();
    }

    private void selectHeldValue() {
        ItemStack held = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.hold_item"), false);
            return;
        }
        selectValue(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
    }

    private void selectValue(String itemId) {
        selectedValue = itemId;
        Long current = values.get(ResourceLocation.tryParse(itemId));
        valueInput = current == null ? "" : Long.toString(current);
        rebuildWidgets();
    }

    private void setValue() {
        long value;
        try {
            value = Long.parseLong(valueInput.trim().replace(",", ""));
        } catch (NumberFormatException exception) {
            value = -1L;
        }
        if (value < 1L) {
            setStatus(Component.translatable("message.cobblemon_economy_plus.admin.bad_value"), false);
            return;
        }
        PlatformNetworking.sendAdminItemValue(new EconomyPayloads.AdminItemValuePayload(revision, selectedValue, value));
    }

    private void removeValue() {
        if (confirm("remove_value:" + selectedValue)) {
            PlatformNetworking.sendAdminItemValue(new EconomyPayloads.AdminItemValuePayload(revision, selectedValue, 0L));
        }
    }

    private void runCommand(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
            setStatus(Component.literal("/" + command), true);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int x = px();
        int y = py();
        if (tab == Tab.SHOPS) {
            int shopRow = rowAt(mouseX, mouseY, x + 6, y + 22, 90, ROW, SHOP_ROWS);
            if (shopRow >= 0 && shopScroll + shopRow < shops.size()) {
                selectShop(shops.get(shopScroll + shopRow));
                return true;
            }
            int entryRow = rowAt(mouseX, mouseY, x + 100, y + 72, 294, ENTRY_ROW, ENTRY_ROWS);
            if (draft != null && entryRow >= 0 && entryScroll + entryRow < draft.entries.size()) {
                selectedEntry = entryScroll + entryRow;
                rebuildWidgets();
                return true;
            }
        } else {
            int valueRow = rowAt(mouseX, mouseY, x + 8, y + 40, 386, ENTRY_ROW, VALUE_ROWS);
            List<ResourceLocation> listed = filteredValues();
            if (valueRow >= 0 && valueScroll + valueRow < listed.size()) {
                selectValue(listed.get(valueScroll + valueRow).toString());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : 1;
        int x = px();
        if (tab == Tab.SHOPS) {
            if (mouseX < x + 98) {
                shopScroll = Math.max(0, Math.min(shopScroll + step, Math.max(0, shops.size() - SHOP_ROWS)));
            } else {
                entryScroll = Math.max(0, Math.min(entryScroll + step, maxEntryScroll()));
            }
        } else {
            valueScroll = Math.max(0, Math.min(valueScroll + step, Math.max(0, filteredValues().size() - VALUE_ROWS)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The inventory key closes the screen like the shop does, but only while no text box is being typed in.
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)
                && !(getFocused() instanceof EditBox box && box.isFocused())) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int rowAt(double mouseX, double mouseY, int x, int y, int width, int rowHeight, int rows) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + rowHeight * rows) {
            return -1;
        }
        return (int) ((mouseY - y) / rowHeight);
    }

    private int maxEntryScroll() {
        return draft == null ? 0 : Math.max(0, draft.entries.size() - ENTRY_ROWS);
    }

    private List<ResourceLocation> filteredValues() {
        String filter = valueFilter.trim().toLowerCase(Locale.ROOT);
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation id : values.keySet()) {
            if (filter.isEmpty() || id.toString().contains(filter)
                    || itemName(id.toString()).toLowerCase(Locale.ROOT).contains(filter)) {
                result.add(id);
            }
        }
        result.sort((a, b) -> a.toString().compareTo(b.toString()));
        return result;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn in render() before the panel, so the widgets' own background pass does not cover the panel.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x80000000);
        int x = px();
        int y = py();
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, c.outline);
        g.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + PANEL_HEIGHT - 2, c.body);
        g.fill(x + 2, y + 2, x + PANEL_WIDTH - 2, y + 19, c.accent);
        g.drawString(font, title, x + 8, y + 6, WHITE, true);

        if (tab == Tab.SHOPS) {
            renderShops(g, x, y, mouseX, mouseY);
        } else {
            renderValues(g, x, y, mouseX, mouseY);
        }

        // Status line.
        if (!status.getString().isEmpty() && Util.getMillis() - statusAt < 12_000L) {
            g.drawString(font, abbreviate(status.getString(), 196), x + 8, y + PANEL_HEIGHT - 14,
                    statusOk ? GREEN : RED, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderShops(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Shop list.
        g.fill(x + 6, y + 22, x + 96, y + 22 + ROW * SHOP_ROWS, c.bodyDark);
        for (int row = 0; row < SHOP_ROWS && shopScroll + row < shops.size(); row++) {
            ShopDefinition shop = shops.get(shopScroll + row);
            int ry = y + 22 + row * ROW;
            boolean selected = draft != null && !draft.isNew && shop.id().equals(draft.id);
            if (selected) {
                g.fill(x + 6, ry, x + 96, ry + ROW, c.selected);
            } else if (rowAt(mouseX, mouseY, x + 6, ry, 90, ROW, 1) == 0) {
                g.fill(x + 6, ry, x + 96, ry + ROW, 0x30FFFFFF);
            }
            g.drawString(font, abbreviate(shop.id(), 86), x + 9, ry + 2, selected ? c.ink : c.inkMuted, false);
        }
        if (draft == null) {
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.no_shop"), x + 104, y + 40,
                    c.inkMuted, false);
            return;
        }

        int rx = x + 100;
        g.drawString(font, "ID", rx, y + 25, c.ink, false);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.theme"), rx + 96, y + 25, c.ink, false);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.multiplier"), rx + 212, y + 25, c.ink, false);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.name"), rx, y + 41, c.ink, false);
        g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.subtitle"), rx, y + 57, c.ink, false);

        // Entries.
        int ly = y + 72;
        g.fill(rx, ly, rx + 294, ly + ENTRY_ROW * ENTRY_ROWS, c.bodyDark);
        if (draft.entries.isEmpty()) {
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.no_entries"), rx + 6, ly + 6,
                    c.inkMuted, false);
        }
        for (int row = 0; row < ENTRY_ROWS && entryScroll + row < draft.entries.size(); row++) {
            int index = entryScroll + row;
            ShopDraft.EntryDraft entry = draft.entries.get(index);
            int ry = ly + row * ENTRY_ROW;
            if (index == selectedEntry) {
                g.fill(rx, ry, rx + 294, ry + ENTRY_ROW, c.selected);
            } else if (rowAt(mouseX, mouseY, rx, ry, 294, ENTRY_ROW, 1) == 0) {
                g.fill(rx, ry, rx + 294, ry + ENTRY_ROW, 0x30FFFFFF);
            }
            g.renderItem(stack(entry.itemId), rx + 2, ry + 1);
            int textColor = index == selectedEntry ? c.ink : c.inkMuted;
            g.drawString(font, abbreviate(itemName(entry.itemId), 104), rx + 22, ry + 5, textColor, false);
            g.drawString(font, "×" + entry.amount, rx + 130, ry + 5, textColor, false);
            g.drawString(font, abbreviate(entry.price, 54), rx + 152, ry + 5, textColor, false);
            g.drawString(font, abbreviate(limitSummary(entry), 84), rx + 208, ry + 5, textColor, false);
        }
        if (draft.entries.size() > ENTRY_ROWS) {
            String more = (entryScroll + 1) + "-" + Math.min(draft.entries.size(), entryScroll + ENTRY_ROWS)
                    + " / " + draft.entries.size();
            g.drawString(font, more, rx + 294 - font.width(more), ly + ENTRY_ROW * ENTRY_ROWS + 1, c.inkMuted, false);
        }

        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            int labelY = y + 147;
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.price"), rx, labelY, c.ink, false);
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.amount"), rx + 62, labelY, c.ink, false);
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.limit"), rx + 92, labelY, c.ink, false);
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.stock"), rx + 180, labelY, c.ink, false);
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.restock_minutes"), rx + 220, labelY,
                    c.ink, false);
        }
        if (dirty()) {
            Component unsaved = Component.translatable("gui.cobblemon_economy_plus.admin.unsaved_mark");
            g.drawString(font, unsaved, rx + 150, y + 197, 0xFFFFD166, false);
        }
    }

    private void renderValues(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<ResourceLocation> listed = filteredValues();
        int ly = y + 40;
        g.fill(x + 8, ly, x + 394, ly + ENTRY_ROW * VALUE_ROWS, c.bodyDark);
        if (listed.isEmpty()) {
            g.drawString(font, Component.translatable("gui.cobblemon_economy_plus.admin.no_values"), x + 14, ly + 6,
                    c.inkMuted, false);
        }
        for (int row = 0; row < VALUE_ROWS && valueScroll + row < listed.size(); row++) {
            ResourceLocation id = listed.get(valueScroll + row);
            int ry = ly + row * ENTRY_ROW;
            boolean selected = id.toString().equals(selectedValue);
            if (selected) {
                g.fill(x + 8, ry, x + 394, ry + ENTRY_ROW, c.selected);
            } else if (rowAt(mouseX, mouseY, x + 8, ry, 386, ENTRY_ROW, 1) == 0) {
                g.fill(x + 8, ry, x + 394, ry + ENTRY_ROW, 0x30FFFFFF);
            }
            g.renderItem(stack(id.toString()), x + 10, ry + 1);
            int color = selected ? c.ink : c.inkMuted;
            g.drawString(font, abbreviate(itemName(id.toString()), 140), x + 30, ry + 5, color, false);
            g.drawString(font, abbreviate(id.toString(), 140), x + 176, ry + 5, c.inkMuted, false);
            String value = EconomyFormat.money(values.get(id));
            g.drawString(font, value, x + 388 - font.width(value), ry + 5, color, false);
        }
        String count = Component.translatable("gui.cobblemon_economy_plus.admin.value_count", listed.size(), values.size())
                .getString();
        g.drawString(font, count, x + 394 - font.width(count), ly + ENTRY_ROW * VALUE_ROWS + 2, c.inkMuted, false);

        if (selectedValue != null) {
            g.renderItem(stack(selectedValue), x + 8, y + 193);
            g.drawString(font, abbreviate(itemName(selectedValue), 160), x + 28, y + 197, c.ink, false);
        }
    }

    private String limitSummary(ShopDraft.EntryDraft entry) {
        StringBuilder text = new StringBuilder();
        if (!entry.limitCount.isBlank()) {
            text.append(entry.limitCount).append('/').append(periodLabel(entry.period).getString());
        }
        if (!entry.stockMax.isBlank()) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Component.translatable("gui.cobblemon_economy_plus.stock_left", entry.stockMax).getString());
        }
        return text.toString();
    }

    private static ItemStack stack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static String itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return itemId;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
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
        return value.substring(0, end) + suffix;
    }
}
