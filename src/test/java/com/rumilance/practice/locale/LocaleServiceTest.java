package com.rumilance.practice.locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the classpath-resource-backed locale loading and the requested-locale &rarr;
 * default-locale &rarr; raw-key fallback chain, using the real bundled {@code lang/*.yml} files
 * (no Bukkit server required, since {@code YamlConfiguration} works standalone).
 */
class LocaleServiceTest {

    private LocaleService localeService;

    @BeforeEach
    void setUp() {
        localeService = new LocaleService("en_us");
    }

    @Test
    void bundlesEveryDocumentedLocale() {
        // en_us, ja_jp, en_gb, ko_kr + the zh_cn/es_es/fr_FR subsets added with the
        // first-join language picker.
        assertEquals(7, LocaleService.BUILT_IN_LOCALES.size());
        for (String locale : LocaleService.BUILT_IN_LOCALES) {
            assertTrue(localeService.isSupported(locale), "expected " + locale + " to be loaded");
        }
    }

    @Test
    void resolvesMessageInRequestedLocale() {
        String japanese = localeService.rawMessage("ja_jp", "general.no-permission");
        assertTrue(japanese.contains("権限"), "expected Japanese translation, got: " + japanese);
    }

    @Test
    void fallsBackToDefaultLocaleWhenKeyMissingFromRequestedLocale() {
        // en_gb.yml intentionally only defines a small subset of keys; a key that only exists
        // in en_us (e.g. a deep admin/audit key) must fall back to the en_us translation.
        String fallback = localeService.rawMessage("en_gb", "admin.migrate-complete");
        String enUs = localeService.rawMessage("en_us", "admin.migrate-complete");

        assertEquals(enUs, fallback);
        assertFalse(fallback.startsWith("!"), "should not fall through to the raw-key marker");
    }

    @Test
    void fallsBackToDefaultLocaleWhenRequestedLocaleIsUnsupported() {
        String unsupported = localeService.rawMessage("xx_yz", "general.no-permission");
        String enUs = localeService.rawMessage("en_us", "general.no-permission");

        assertEquals(enUs, unsupported);
    }

    @Test
    void returnsRawKeyMarkerWhenKeyMissingEverywhere() {
        String result = localeService.rawMessage("en_us", "this.key.does.not.exist");
        assertEquals("!this.key.does.not.exist!", result);
    }

    @Test
    void normalizesLocaleCasingAndSeparators() {
        String hyphenated = localeService.rawMessage("JA-JP", "general.no-permission");
        String canonical = localeService.rawMessage("ja_jp", "general.no-permission");
        assertEquals(canonical, hyphenated);
    }

    @Test
    void defaultLocaleAccessorReflectsConstructorArgument() {
        assertEquals("en_us", localeService.defaultLocale());
    }
}
