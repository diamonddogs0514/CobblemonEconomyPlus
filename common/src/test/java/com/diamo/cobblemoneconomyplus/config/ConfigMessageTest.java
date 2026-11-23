package com.diamo.cobblemoneconomyplus.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.diamo.cobblemoneconomyplus.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMessageTest {
    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void englishTextIsUsedForTheLog() {
        ConfigMessage message = ConfigMessage.of("shop_skipped", "skipped shop #%s: %s", 2,
                ConfigMessage.of("unknown_item", "unknown item '%s'", "minecraft:nope")).inFile("shops.json");
        assertEquals("shops.json: skipped shop #2: unknown item 'minecraft:nope'", message.text());
    }

    @Test
    void chatGetsATranslationKeyWithTheEnglishFallback() {
        Component component = ConfigMessage.of("unknown_item", "unknown item '%s'", "x").component();
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals("config.cobblemon_economy_plus.unknown_item", contents.getKey());
        assertEquals("unknown item '%s'", contents.getFallback());
        assertEquals("x", contents.getArgs()[0]);
    }

    /** Every message the code can produce has a Japanese translation with the same number of placeholders. */
    @Test
    void everyMessageIsTranslatedToJapanese() throws IOException {
        Map<String, String> english = messagesInSource(Path.of("src/main/java"));
        assertTrue(english.size() > 30, "found " + english.size() + " messages; is the source path right?");
        JsonObject japanese;
        try (var reader = Files.newBufferedReader(
                Path.of("src/main/resources/assets/cobblemon_economy_plus/lang/ja_jp.json"), StandardCharsets.UTF_8)) {
            japanese = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<String> problems = new ArrayList<>();
        english.forEach((key, text) -> {
            String translationKey = ConfigMessage.PREFIX + key;
            if (!japanese.has(translationKey)) {
                problems.add("missing " + translationKey);
            } else if (placeholders(japanese.get(translationKey).getAsString()) != placeholders(text)) {
                problems.add("placeholder count differs for " + translationKey);
            }
        });
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static final Pattern MESSAGE = Pattern.compile(
            "(?:ConfigMessage\\.of|(?<![.\\w])of|new ConfigException)\\(\\s*\"([a-z_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static Map<String, String> messagesInSource(Path root) throws IOException {
        Map<String, String> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = MESSAGE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return found;
    }

    private static int placeholders(String text) {
        Matcher matcher = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
