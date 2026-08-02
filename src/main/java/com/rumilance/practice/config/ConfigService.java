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
 * Loads, caches and reloads all of RumilancePractice's YAML resource files, copying bundled
 * defaults from the plugin jar into the data folder on first run and layering any file already
 * present in the data folder on top of the jar's own defaults (so partial user overrides never
 * lose access to newly introduced keys).
 */
public final class ConfigService {

    public static final String CONFIG = "config.yml";
    public static final String DATABASE = "database.yml";
    public static final String GUI = "gui.yml";
    public static final String SOUNDS = "sounds.yml";
    public static final String PROFILE = "profile.yml";
    public static final String KITS = "kits.yml";
    public static final String ARENAS = "arenas.yml";
    public static final String LOBBY = "lobby.yml";
    public static final String FFA = "ffa.yml";
    public static final String PLANS = "plans.yml";
    public static final String ARROW_EFFECTS = "arrow-effects.yml";
    public static final String EKIT_ITEMS = "ekit-items.yml";

    private static final List<String> RESOURCE_FILES = List.of(
            CONFIG, DATABASE, GUI, SOUNDS, PROFILE, KITS, ARENAS, LOBBY, FFA, PLANS, ARROW_EFFECTS, EKIT_ITEMS
    );

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new LinkedHashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) every managed YAML file. Safe to call multiple times.
     */
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
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(target);
        try (InputStream resource = plugin.getResource(fileName)) {
            if (resource != null) {
                try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                    configuration.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bundled defaults for " + fileName, e);
        }
        return configuration;
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
}
