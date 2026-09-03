package com.rumilance.practice.hiddenrank;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Hidden ranks ("裏ランク"): ranks that are NEVER displayed anywhere (no nametag, no tab icon)
 * but silently unlock perks. Granted via {@code /urank} — never via {@code /rank}.
 *
 * <p>Currently the only hidden rank is {@code custom_shield}: the holder receives a shield
 * carrying an operator-assigned Custom Model Data in every match, which the resource pack maps
 * to a high-resolution custom artwork. Players holding it lose the VIP+ shield pattern editor
 * (their shield look is fixed by the artwork).</p>
 *
 * <p>Persisted to {@code hidden_ranks.yml} in the plugin data folder.</p>
 */
public final class HiddenRankService {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Boolean> customShield = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> shieldModelData = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownName = new ConcurrentHashMap<>();

    public HiddenRankService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(PluginIdentity.dataFolder(plugin), "hidden_ranks.yml");
        load();
    }

    // ------------------------------------------------------------------ custom_shield rank

    public boolean hasCustomShield(UUID playerId) {
        return customShield.getOrDefault(playerId, false);
    }

    /** Grants or removes the hidden custom_shield rank and persists immediately. */
    public void setCustomShield(UUID playerId, String name, boolean grant) {
        if (grant) {
            customShield.put(playerId, true);
            if (name != null && !name.isBlank()) {
                lastKnownName.put(playerId, name);
            }
        } else {
            customShield.remove(playerId);
            shieldModelData.remove(playerId);
        }
        save();
    }

    /** All holders of the hidden custom_shield rank (for the admin GUI). */
    public Set<UUID> customShieldHolders() {
        return new LinkedHashSet<>(customShield.keySet());
    }

    public String lastName(UUID playerId) {
        return lastKnownName.getOrDefault(playerId, "?");
    }

    // ------------------------------------------------------------------ shield model data

    /** The operator-assigned Custom Model Data, or {@code 0} when none is set. */
    public int shieldModelData(UUID playerId) {
        return shieldModelData.getOrDefault(playerId, 0);
    }

    public void setShieldModelData(UUID playerId, int cmd) {
        if (cmd <= 0) {
            shieldModelData.remove(playerId);
        } else {
            shieldModelData.put(playerId, cmd);
        }
        save();
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (yaml.getBoolean(key + ".custom-shield", false)) {
                    customShield.put(uuid, true);
                }
                int cmd = yaml.getInt(key + ".model-data", 0);
                if (cmd > 0) {
                    shieldModelData.put(uuid, cmd);
                }
                String name = yaml.getString(key + ".name", null);
                if (name != null && !name.isBlank()) {
                    lastKnownName.put(uuid, name);
                }
            } catch (IllegalArgumentException ignored) {
                // malformed uuid line — skip
            }
        }
    }

    private synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> all = new LinkedHashSet<>(customShield.keySet());
        all.addAll(shieldModelData.keySet());
        for (UUID uuid : all) {
            String key = uuid.toString();
            yaml.set(key + ".custom-shield", customShield.getOrDefault(uuid, false));
            int cmd = shieldModelData.getOrDefault(uuid, 0);
            if (cmd > 0) {
                yaml.set(key + ".model-data", cmd);
            }
            String name = lastKnownName.get(uuid);
            if (name != null) {
                yaml.set(key + ".name", name);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving hidden_ranks.yml", e);
        }
    }
}
