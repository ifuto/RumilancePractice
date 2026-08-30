/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.Configuration
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.rumilance.practice.config;

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
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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
    public static final String PRESET_ITEMS = "preset-items.yml";
    public static final String SCOREBOARD = "scoreboard.yml";
    private static final List<String> RESOURCE_FILES = List.of("config.yml", "database.yml", "gui.yml", "sounds.yml", "profile.yml", "kits.yml", "arenas.yml", "lobby.yml", "ffa.yml", "plans.yml", "arrow-effects.yml", "ekit-items.yml", "preset-items.yml", "scoreboard.yml");
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new LinkedHashMap<String, FileConfiguration>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public JavaPlugin plugin() {
        return this.plugin;
    }

    public void loadAll() {
        File dataFolder = this.plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            this.plugin.getLogger().warning("Failed to create plugin data folder: " + String.valueOf(dataFolder));
        }
        for (String fileName : RESOURCE_FILES) {
            this.configs.put(fileName, this.loadWithDefaults(fileName));
        }
    }

    private FileConfiguration loadWithDefaults(String fileName) {
        YamlConfiguration jarDefaults;
        YamlConfiguration onDisk;
        block14: {
            File target = new File(this.plugin.getDataFolder(), fileName);
            if (!target.exists()) {
                this.plugin.saveResource(fileName, false);
            }
            onDisk = YamlConfiguration.loadConfiguration((File)target);
            jarDefaults = new YamlConfiguration();
            try (InputStream resource = this.plugin.getResource(fileName);){
                if (resource == null) break block14;
                try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8);){
                    jarDefaults = YamlConfiguration.loadConfiguration((Reader)reader);
                }
            }
            catch (IOException e) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to load bundled defaults for " + fileName, e);
            }
        }
        YamlConfiguration merged = ConfigService.deepMerge(jarDefaults, onDisk);
        merged.setDefaults((Configuration)jarDefaults);
        return merged;
    }

    static YamlConfiguration deepMerge(YamlConfiguration base, YamlConfiguration overlay) {
        YamlConfiguration out = new YamlConfiguration();
        if (base != null) {
            for (String key : base.getKeys(true)) {
                if (base.isConfigurationSection(key)) continue;
                out.set(key, base.get(key));
            }
        }
        if (overlay != null) {
            for (String key : overlay.getKeys(true)) {
                if (overlay.isConfigurationSection(key)) continue;
                out.set(key, overlay.get(key));
            }
        }
        return out;
    }

    public void reload() {
        this.loadAll();
    }

    public void save(String fileName) {
        FileConfiguration configuration = this.configs.get(fileName);
        if (configuration == null) {
            return;
        }
        try {
            configuration.save(new File(this.plugin.getDataFolder(), fileName));
        }
        catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to save " + fileName, e);
        }
    }

    public FileConfiguration get(String fileName) {
        FileConfiguration configuration = this.configs.get(fileName);
        if (configuration == null) {
            throw new IllegalStateException("Configuration '" + fileName + "' has not been loaded yet");
        }
        return configuration;
    }

    public FileConfiguration config() {
        return this.get(CONFIG);
    }

    public FileConfiguration database() {
        return this.get(DATABASE);
    }

    public FileConfiguration gui() {
        return this.get(GUI);
    }

    public FileConfiguration sounds() {
        return this.get(SOUNDS);
    }

    public FileConfiguration profile() {
        return this.get(PROFILE);
    }

    public FileConfiguration kits() {
        return this.get(KITS);
    }

    public FileConfiguration arenas() {
        return this.get(ARENAS);
    }

    public FileConfiguration lobby() {
        return this.get(LOBBY);
    }

    public FileConfiguration ffa() {
        return this.get(FFA);
    }

    public FileConfiguration plans() {
        return this.get(PLANS);
    }

    public FileConfiguration arrowEffects() {
        return this.get(ARROW_EFFECTS);
    }

    public FileConfiguration ekitItems() {
        return this.get(EKIT_ITEMS);
    }

    public FileConfiguration presetItems() {
        return this.get(PRESET_ITEMS);
    }

    public FileConfiguration scoreboard() {
        return this.get(SCOREBOARD);
    }
}
