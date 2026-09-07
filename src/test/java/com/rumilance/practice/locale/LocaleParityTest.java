package com.rumilance.practice.locale;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the multi-language contract: every locale file must carry exactly the same key set.
 * The plugin ships 7 locales (en_us/en_gb/es_es/fr_fr/ja_jp/ko_kr/zh_cn) and previously new
 * keys only landed in two of them, which silently degraded the GUI to key names for the
 * rest. Parsed with a tiny indentation walker so the test needs no YAML dependency.
 */
class LocaleParityTest {

    private static final String[] LOCALES = {
            "en_gb", "en_us", "es_es", "fr_fr", "ja_jp", "ko_kr", "zh_cn"
    };

    @Test
    void everyLocaleShipsTheSameKeySet() {
        Set<String> reference = readKeys(LOCALES[1]); // en_us is the authoring locale
        assertTrue(reference.size() > 1000, "sanity: author locale should carry 1000+ keys");
        for (String locale : LOCALES) {
            Set<String> keys = readKeys(locale);
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(reference);
            assertEquals(new HashSet<>(), missing, locale + " is missing keys: " + missing);
            assertEquals(new HashSet<>(), extra, locale + " has unknown keys: " + extra);
        }
    }

    @Test
    void sampleKeysExistEverywhere() {
        for (String key : List.of("gui.party-quick-invite", "party.right-kick",
                "gui.team-settings-entry", "menu.close")) {
            for (String locale : LOCALES) {
                assertTrue(readKeys(locale).contains(key),
                        key + " missing from " + locale);
            }
        }
    }

    /** Two-level YAML walker: collects dotted key paths of every "section: key:" pair. */
    private Set<String> readKeys(String locale) {
        String path = "/lang/" + locale + ".yml";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, path + " not on the test classpath");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> keys = new HashSet<>();
            Deque<int[]> indentStack = new ArrayDeque<>();
            Deque<String> nameStack = new ArrayDeque<>();
            for (String rawLine : text.split("\n", -1)) {
                if (rawLine.isBlank() || rawLine.trim().startsWith("#")) {
                    continue;
                }
                int indent = rawLine.length() - rawLine.stripLeading().length();
                String line = rawLine.trim();
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String name = line.substring(0, colon);
                if (!name.matches("[A-Za-z0-9_-]+")) {
                    continue;
                }
                while (!indentStack.isEmpty() && indentStack.peekLast()[0] >= indent) {
                    indentStack.pollLast();
                    nameStack.pollLast();
                }
                indentStack.addLast(new int[] {indent});
                nameStack.addLast(name);
                List<String> parts = new ArrayList<>(nameStack);
                keys.add(String.join(".", parts));
            }
            return keys;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
