package com.rumilance.practice.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads, caches and reloads YAML resource files for RumilancePractice.
 */
public final class ConfigService {

    public static final String CONFIG = "config.yml";
    public static final String DATABASE = "database.yml";
    public static final String GUI = "gui.yml";
    public static final String SOUNDS = "sounds.yml";
    public static final String PROFILE = "profile.yml";
    public static final String KITS = "kits.yml";
    public static final String ARENAS = "arenas.yml";
    public static final String PRACTICES = "practices.yml";
    public static final String LOBBY = "lobby.yml";
    public static final String FFA = "ffa.yml";
    public static final String PLANS = "plans.yml";
    public static final String ARROW_EFFECTS = "arrow-effects.yml";
    public static final String EKIT_ITEMS = "ekit-items.yml";
    public static final String PRESET_ITEMS = "preset-items.yml";
    public static final String SCOREBOARD = "scoreboard.yml";

    private static final List<String> RESOURCE_FILES = List.of(
            CONFIG, DATABASE, GUI, SOUNDS, PROFILE, KITS, ARENAS, PRACTICES, LOBBY, FFA, PLANS,
            ARROW_EFFECTS, EKIT_ITEMS, PRESET_ITEMS, SCOREBOARD
    );

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new LinkedHashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public void loadAll() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder: " + dataFolder);
        }
        for (String fileName : RESOURCE_FILES) {
            configs.put(fileName, loadWithDefaults(fileName));
        }
    }

    private FileConfiguration loadWithDefaults(String fileName) {
        File target = new File(plugin.getDataFolder(), fileName);
        if (!target.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(target);
        YamlConfiguration jarDefaults = new YamlConfiguration();
        try (InputStream resource = plugin.getResource(fileName)) {
            if (resource != null) {
                try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                    jarDefaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled defaults for " + fileName, e);
        }
        YamlConfiguration merged = deepMerge(jarDefaults, onDisk);
        merged.setDefaults(jarDefaults);
        return merged;
    }

    static YamlConfiguration deepMerge(YamlConfiguration base, YamlConfiguration overlay) {
        YamlConfiguration out = new YamlConfiguration();
        if (base != null) {
            for (String key : base.getKeys(true)) {
                if (!base.isConfigurationSection(key)) {
                    out.set(key, base.get(key));
                }
            }
        }
        if (overlay != null) {
            for (String key : overlay.getKeys(true)) {
                if (overlay.isConfigurationSection(key)) {
                    continue;
                }
                Object value = overlay.get(key);
                if (value instanceof List<?> list
                        && list.isEmpty()
                        && key.startsWith("layouts.")
                        && out.get(key) instanceof List<?> baseList
                        && !baseList.isEmpty()) {
                    continue;
                }
                out.set(key, value);
            }
        }
        return out;
    }

    public void reload() {
        loadAll();
    }

    public void save(String fileName) {
        FileConfiguration configuration = configs.get(fileName);
        if (configuration == null) {
            return;
        }
        try {
            configuration.save(new File(plugin.getDataFolder(), fileName));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save " + fileName, e);
        }
    }

    public FileConfiguration get(String fileName) {
        FileConfiguration configuration = configs.get(fileName);
        if (configuration == null) {
            throw new IllegalStateException("Configuration '" + fileName + "' has not been loaded yet");
        }
        return configuration;
    }

    public FileConfiguration config() {
        return get(CONFIG);
    }

    public FileConfiguration database() {
        return get(DATABASE);
    }

    public FileConfiguration gui() {
        return get(GUI);
    }

    public FileConfiguration sounds() {
        return get(SOUNDS);
    }

    public FileConfiguration profile() {
        return get(PROFILE);
    }

    public FileConfiguration kits() {
        return get(KITS);
    }

    public FileConfiguration arenas() {
        return get(ARENAS);
    }

    public FileConfiguration practices() {
        return get(PRACTICES);
    }

    public FileConfiguration lobby() {
        return get(LOBBY);
    }

    public FileConfiguration ffa() {
        return get(FFA);
    }

    public FileConfiguration plans() {
        return get(PLANS);
    }

    public FileConfiguration arrowEffects() {
        return get(ARROW_EFFECTS);
    }

    public FileConfiguration ekitItems() {
        return get(EKIT_ITEMS);
    }

    public FileConfiguration presetItems() {
        return get(PRESET_ITEMS);
    }

    public FileConfiguration scoreboard() {
        return get(SCOREBOARD);
    }
}
