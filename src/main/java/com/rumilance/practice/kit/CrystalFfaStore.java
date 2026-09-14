package com.rumilance.practice.kit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Crystal FFA per-player state: which KIT slot (1..9) the player selected for the declared
 * crystal FFA kit. FFA applies that variant's layout on spawn; the picker GUI writes it.
 *
 * <p>Storage is a tiny {@code crystal-ffa.yml} ({@code players.<uuid>: <slot>}) — one int per
 * player, written synchronously on the rare click that changes it.</p>
 */
public final class CrystalFfaStore {

    /** KIT slots per player (KIT1..KIT9 — one 9-slot row in the picker). */
    public static final int SLOTS = 9;

    private final File file;
    private final YamlConfiguration yaml;

    public CrystalFfaStore(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "crystal-ffa.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Layout storage key for one variant of a kit ({@code <kit>#v<n>}). */
    public static String variantKey(String kitId, int variant) {
        return kitId + "#v" + variant;
    }

    /** The player's selected KIT slot, always clamped to {@code 1..SLOTS} (default KIT1). */
    public int selectedVariant(UUID playerId) {
        int raw = yaml.getInt("players." + playerId, 1);
        return Math.min(SLOTS, Math.max(1, raw));
    }

    /** Selects the KIT slot the player will spawn with in crystal FFA (persisted). */
    public void selectVariant(UUID playerId, int variant) {
        yaml.set("players." + playerId.toString(), Math.min(SLOTS, Math.max(1, variant)));
        try {
            yaml.save(file);
        } catch (IOException e) {
            pluginlessWarning(e);
        }
    }

    private void pluginlessWarning(IOException e) {
        org.bukkit.Bukkit.getLogger().log(Level.WARNING,
                "[N Arena][CrystalFfa] could not save crystal-ffa.yml: " + e.getMessage());
    }
}
