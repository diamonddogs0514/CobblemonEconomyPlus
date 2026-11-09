package com.diamo.cobblemoneconomyplus.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalizedTextTest {
    private static final String BOTH = "{\"en_us\":\"Poke Mart\",\"ja_jp\":\"ポケマート\"}";

    @Test
    void plainStringsAreReturnedAsIs() {
        assertEquals("Hello", LocalizedText.resolve("Hello", "ja_jp"));
        assertEquals("", LocalizedText.resolve(null, "en_us"));
        assertEquals("", LocalizedText.resolve("", "en_us"));
    }

    @Test
    void exactLanguageWins() {
        assertEquals("ポケマート", LocalizedText.resolve(BOTH, "ja_jp"));
        assertEquals("Poke Mart", LocalizedText.resolve(BOTH, "en_us"));
    }

    @Test
    void languageAndKeyMatchingIgnoresCase() {
        assertEquals("ポケマート", LocalizedText.resolve("{\"JA_JP\":\"ポケマート\"}", "ja_JP"));
    }

    @Test
    void sameLanguageFamilyIsUsedBeforeEnglish() {
        assertEquals("Hallo", LocalizedText.resolve("{\"en_us\":\"Hello\",\"de_de\":\"Hallo\"}", "de_at"));
    }

    @Test
    void fallsBackToEnglishThenToTheFirstValue() {
        assertEquals("Hello", LocalizedText.resolve("{\"ja_jp\":\"こんにちは\",\"en_us\":\"Hello\"}", "fr_fr"));
        assertEquals("こんにちは", LocalizedText.resolve("{\"ja_jp\":\"こんにちは\",\"de_de\":\"Hallo\"}", "fr_fr"));
        assertEquals("Hello", LocalizedText.resolve("{\"en_us\":\"Hello\"}", null));
    }

    @Test
    void nonTextValuesAreIgnored() {
        assertEquals("Hello", LocalizedText.resolve("{\"ja_jp\":{\"nested\":1},\"en_us\":\"Hello\"}", "ja_jp"));
    }

    @Test
    void brokenJsonShowsTheRawTextInsteadOfNothing() {
        assertEquals("{not json", LocalizedText.resolve("{not json", "en_us"));
    }
}
