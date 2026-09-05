package com.rumilance.practice.locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads every bundled {@code lang/<locale>.yml} translation file (flattening nested YAML
 * sections into dotted keys, e.g. {@code general.no-permission}) and resolves message lookups
 * with a three-tier fallback: requested locale &rarr; configured default locale &rarr; raw key.
 *
 * <p>Locale files placed under a plugin's data folder (e.g. {@code plugins/RumilancePractice/lang/})
 * take precedence over the versions bundled in the plugin jar, allowing server owners to
 * customize translations without rebuilding the plugin.</p>
 *
 * <p>This class only depends on Bukkit's standalone configuration API ({@code YamlConfiguration})
 * and the classpath, so it can be exercised directly by plain JUnit tests.</p>
 */
public final class LocaleService {

    /**
     * Locales bundled with the plugin. Japanese, English (US/UK) and Korean ship full
     * translations; Simplified Chinese, Spanish and French ship a core subset (anything
     * missing falls back to {@code en_us}, see {@link #rawMessage(String, String)}). Server
     * owners may still add their own {@code lang/<locale>.yml} files in the plugin data
     * folder to override or extend these.
     */
    public static final List<String> BUILT_IN_LOCALES = List.of(
            "en_us", "ja_jp", "en_gb", "ko_kr", "zh_cn", "es_es", "fr_fr"
    );

    /** Human-facing option list for the language picker, in display order. */
    public static final List<String> PICKER_LOCALES = List.of(
            "en_us", "en_gb", "ja_jp", "ko_kr", "zh_cn", "es_es", "fr_fr"
    );

    private final String defaultLocale;
    private final File externalLangFolder;
    private final Map<String, Map<String, String>> catalog = new ConcurrentHashMap<>();

    public LocaleService(String defaultLocale) {
        this(defaultLocale, null);
    }

    public LocaleService(String defaultLocale, File externalLangFolder) {
        this.defaultLocale = normalize(defaultLocale);
        this.externalLangFolder = externalLangFolder;
        reload();
    }

    /**
     * (Re)loads every built-in locale from the classpath, layering any override found in the
     * external lang folder on top.
     */
    public void reload() {
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        for (String locale : BUILT_IN_LOCALES) {
            Map<String, String> flattened = loadLocale(locale);
            if (!flattened.isEmpty()) {
                loaded.put(locale, flattened);
            }
        }
        catalog.clear();
        catalog.putAll(loaded);
        if (!catalog.containsKey(defaultLocale)) {
            throw new IllegalStateException("Default locale '" + defaultLocale + "' has no bundled translation file");
        }
    }

    private Map<String, String> loadLocale(String locale) {
        Map<String, String> result = new LinkedHashMap<>();
        String resourcePath = "lang/" + locale + ".yml";
        try (InputStream in = LocaleService.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    flattenInto(YamlConfiguration.loadConfiguration(reader), result);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bundled locale resource: " + resourcePath, e);
        }

        if (externalLangFolder != null) {
            File override = new File(externalLangFolder, locale + ".yml");
            if (override.isFile()) {
                flattenInto(YamlConfiguration.loadConfiguration(override), result);
            }
        }
        return result;
    }

    private void flattenInto(ConfigurationSection section, Map<String, String> target) {
        for (String key : section.getKeys(true)) {
            if (section.isString(key)) {
                target.put(key, section.getString(key));
            }
        }
    }

    /**
     * Resolves the MiniMessage string for {@code key} under {@code locale}, falling back to the
     * configured default locale, and finally to a visibly-broken {@code !key!} marker if the key
     * does not exist anywhere. Never returns {@code null}.
     */
    public String rawMessage(String locale, String key) {
        Objects.requireNonNull(key, "key");
        String normalized = normalize(locale);

        Map<String, String> localeMap = catalog.get(normalized);
        if (localeMap != null) {
            String value = localeMap.get(key);
            if (value != null) {
                return value;
            }
        }

        if (!normalized.equals(defaultLocale)) {
            Map<String, String> fallbackMap = catalog.get(defaultLocale);
            if (fallbackMap != null) {
                String value = fallbackMap.get(key);
                if (value != null) {
                    return value;
                }
            }
        }

        return "!" + key + "!";
    }

    public boolean isSupported(String locale) {
        return catalog.containsKey(normalize(locale));
    }

    public String defaultLocale() {
        return defaultLocale;
    }

    public Set<String> supportedLocales() {
        return Collections.unmodifiableSet(catalog.keySet());
    }

    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en_us";
        }
        return locale.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
