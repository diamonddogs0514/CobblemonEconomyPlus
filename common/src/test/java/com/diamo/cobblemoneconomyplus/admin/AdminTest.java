package com.diamo.cobblemoneconomyplus.admin;

import com.diamo.cobblemoneconomyplus.TestBootstrap;
import com.diamo.cobblemoneconomyplus.config.ConfigFiles;
import com.diamo.cobblemoneconomyplus.shop.ShopDefinition;
import com.diamo.cobblemoneconomyplus.shop.ShopJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminTest {
    private static final String SHOPS = """
            { "shops": [
              { "id": "general", "name": { "en_us": "General", "ja_jp": "よろず屋", "de_de": "Laden" },
                "subtitle": { "en_us": "Everyday goods" }, "theme": "wood", "sell_multiplier": 0.5,
                "entries": [
                  { "item": "minecraft:apple", "amount": 4, "price": 12 },
                  { "item": "minecraft:diamond", "amount": 1, "price": 900,
                    "limit": { "per_player": 2, "period": "weekly" }, "stock": { "max": 5, "restock_minutes": 60 } }
                ] },
              { "id": "broken", "entries": "not a list" },
              { "id": "tools", "name": "Tools", "entries": [ { "item": "minecraft:iron_pickaxe", "amount": 1, "price": 300 } ] }
            ] }
            """;

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    private static List<ShopDefinition> shops() {
        return ShopJson.readShops(SHOPS);
    }

    // ------------------------------------------------------------------ ShopJson

    @Test
    void shopsSurviveTheTripToTheScreenAndBack() {
        List<ShopDefinition> shops = shops();
        assertEquals(2, shops.size(), "the broken shop is not sent");
        assertEquals(shops, ShopJson.readShops(ShopJson.shopsFile(shops)));
    }

    @Test
    void itemValuesSurviveTheTripToo() {
        Map<ResourceLocation, Long> values = new LinkedHashMap<>();
        values.put(ResourceLocation.parse("minecraft:diamond"), 250L);
        values.put(ResourceLocation.parse("minecraft:apple"), 2L);
        assertEquals(values, ShopJson.readItemValues(ShopJson.itemValuesFile(values)));
    }

    @Test
    void japaneseTextIsWrittenAsIs() {
        String file = ShopJson.shopsFile(shops());
        assertTrue(file.contains("よろず屋"), file);
    }

    // ------------------------------------------------------------------ ShopDraft

    @Test
    void anUntouchedDraftSavesTheSameShop() {
        for (ShopDefinition shop : shops()) {
            ShopDraft.Result result = ShopDraft.of(shop).toJson();
            assertTrue(result.ok());
            List<String> problems = new ArrayList<>();
            assertEquals(shop, ShopJson.parseShop(result.json(), problems));
            assertTrue(problems.isEmpty(), problems::toString);
        }
    }

    @Test
    void otherLanguagesAreKeptAndOneLanguageBecomesPlainText() {
        ShopDraft draft = ShopDraft.of(shops().get(0));
        ShopDraft.setText(draft.name, ShopDraft.JA, "なんでも屋");
        JsonObject json = draft.toJson().json();
        assertEquals("Laden", json.getAsJsonObject("name").get("de_de").getAsString());
        assertEquals("なんでも屋", json.getAsJsonObject("name").get("ja_jp").getAsString());

        ShopDraft tools = ShopDraft.of(shops().get(1));
        assertEquals("Tools", tools.text(tools.name, ShopDraft.EN));
        assertEquals("Tools", tools.toJson().json().get("name").getAsString());
        ShopDraft.setText(tools.name, ShopDraft.EN, "");
        assertEquals("tools", tools.toJson().json().get("name").getAsString(), "no name falls back to the id");
    }

    @Test
    void plainJapaneseTextGoesInTheJapaneseColumn() {
        ShopDefinition shop = ShopJson.readShops("""
                { "shops": [ { "id": "x", "name": "よろず屋", "subtitle": "Open daily", "entries": [] } ] }
                """).get(0);
        ShopDraft draft = ShopDraft.of(shop);
        assertEquals("よろず屋", draft.text(draft.name, ShopDraft.JA));
        assertEquals("", draft.text(draft.name, ShopDraft.EN));
        assertEquals("Open daily", draft.text(draft.subtitle, ShopDraft.EN));
        assertEquals("よろず屋", draft.toJson().json().get("name").getAsString(), "still saved as plain text");
    }

    @Test
    void aSubtitleInOneLanguageIsSavedForThatLanguageOnly() {
        // Other languages then show the theme's tagline in their own language instead of this text.
        ShopDefinition shop = ShopJson.readShops("""
                { "shops": [ { "id": "x", "subtitle": "Open daily", "entries": [] } ] }
                """).get(0);
        ShopDraft draft = ShopDraft.of(shop);
        JsonObject subtitle = draft.toJson().json().getAsJsonObject("subtitle");
        assertEquals("{\"en_us\":\"Open daily\"}", subtitle.toString());

        ShopDraft.setText(draft.subtitle, ShopDraft.EN, "");
        assertFalse(draft.toJson().json().has("subtitle"), "both blank: no subtitle, the theme's tagline is shown");
    }

    @Test
    void limitsAndStockAreEditedAsText() {
        ShopDraft draft = ShopDraft.of(shops().get(0));
        ShopDraft.EntryDraft apple = draft.entries.get(0);
        apple.limitCount = "3";
        apple.period = ShopDefinition.Period.DAILY;
        apple.stockMax = "10";
        ShopDraft.EntryDraft diamond = draft.entries.get(1);
        diamond.limitCount = " ";
        diamond.stockMax = "";

        ShopDefinition saved = ShopJson.parseShop(draft.toJson().json(), new ArrayList<>());
        assertNotNull(saved);
        assertEquals(new ShopDefinition.Limit(3, ShopDefinition.Period.DAILY), saved.entries().get(0).limit());
        assertEquals(new ShopDefinition.Stock(10, 0), saved.entries().get(0).stock());
        assertFalse(saved.entries().get(1).limited());
    }

    @Test
    void badNumbersPointAtTheEntry() {
        ShopDraft draft = ShopDraft.of(shops().get(0));
        draft.entries.get(1).price = "12x";
        ShopDraft.Result result = draft.toJson();
        assertFalse(result.ok());
        assertEquals(1, result.problem().entry());
        assertEquals("message.cobblemon_economy_plus.admin.bad_price", result.problem().key());

        draft.entries.get(1).price = "1,200";
        assertTrue(draft.toJson().ok(), "thousands separators are accepted");
        draft.entries.get(0).amount = "65";
        assertEquals("message.cobblemon_economy_plus.admin.bad_amount", draft.toJson().problem().key());
        draft.entries.get(0).amount = "4";
        draft.sellMultiplier = "abc";
        assertEquals("message.cobblemon_economy_plus.admin.bad_multiplier", draft.toJson().problem().key());
        draft.sellMultiplier = "1";
        draft.id = "Bad Id!";
        assertEquals("message.cobblemon_economy_plus.admin.bad_id", draft.toJson().problem().key());
    }

    @Test
    void aNewShopNeedsAtLeastAnIdToBeValid() {
        ShopDraft draft = ShopDraft.blank("new_shop");
        assertTrue(draft.isNew);
        ShopDraft.Result result = draft.toJson();
        assertTrue(result.ok());
        // A shop without entries is still valid in shops.json.
        assertNotNull(ShopJson.parseShop(result.json(), new ArrayList<>()));
    }

    @Test
    void theServerRejectsWhatTheFileLoaderWouldChange() {
        List<String> problems = new ArrayList<>();
        ShopJson.parseShop(JsonParser.parseString("""
                { "id": "x", "theme": "rainbow", "entries": [ { "item": "minecraft:nope", "amount": 1, "price": 1 } ] }
                """).getAsJsonObject(), problems);
        assertEquals(2, problems.size(), problems::toString);
    }

    // ------------------------------------------------------------------ AdminEdits

    @Test
    void savingOneShopLeavesTheOthersUntouched() {
        JsonObject root = AdminEdits.shopsRoot(SHOPS);
        JsonObject edited = ShopJson.toJson(shops().get(1));
        edited.addProperty("theme", "ore");
        assertTrue(AdminEdits.putShop(root, "tools", edited));
        assertFalse(AdminEdits.putShop(root, "new_one", ShopJson.toJson(shops().get(1))));

        var array = root.getAsJsonArray("shops");
        assertEquals(4, array.size());
        assertEquals("broken", array.get(1).getAsJsonObject().get("id").getAsString(), "a shop the server skipped is kept");
        assertEquals("ore", array.get(2).getAsJsonObject().get("theme").getAsString());
        assertTrue(AdminEdits.removeShop(root, "general"));
        assertFalse(AdminEdits.removeShop(root, "general"));
        assertEquals(3, root.getAsJsonArray("shops").size());
    }

    @Test
    void brokenFilesAreNotEdited() {
        assertNull(AdminEdits.shopsRoot("{ \"shops\": [ }"));
        assertNull(AdminEdits.shopsRoot("{ \"other\": [] }"));
        assertNull(AdminEdits.valuesRoot("[]"));
    }

    @Test
    void itemValuesAreSetAndRemoved() {
        JsonObject root = AdminEdits.valuesRoot("{ \"values\": { \"minecraft:apple\": 2 } }");
        AdminEdits.setItemValue(root, "minecraft:diamond", 250);
        AdminEdits.setItemValue(root, "minecraft:apple", 0);
        assertEquals("{\"values\":{\"minecraft:diamond\":250}}", root.toString());
    }

    // ------------------------------------------------------------------ files and log

    @Test
    void writingKeepsABackupOfThePreviousFile(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("config").resolve("shops.json");
        ConfigFiles.writeWithBackup(file, "first");
        assertFalse(Files.exists(ConfigFiles.backupPath(file)));
        ConfigFiles.writeWithBackup(file, "second");
        assertEquals("second\n", Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("first\n", Files.readString(ConfigFiles.backupPath(file), StandardCharsets.UTF_8));
        assertFalse(Files.exists(file.resolveSibling("shops.json.tmp")));
    }

    @Test
    void adminLogLinesAreTabSeparatedAndSingleLine() {
        String line = AdminLog.line(Instant.parse("2026-11-22T10:00:00Z"), "Admin", "uuid", "save_shop", "general\tx\ny");
        assertEquals("2026-11-22T10:00:00Z\tAdmin\tuuid\tsave_shop\tgeneral x y", line);
    }
}
