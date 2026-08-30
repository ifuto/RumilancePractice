package com.rumilance.practice.sight;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * {@code config.yml ↁEsight}  Eworld-border toggles and pearl landing limits.
 */
public record SightSettings(
        boolean enabled,
        boolean worldBorderCombatants,
        boolean worldBorderSpectators,
        int pearlMaxLiftBlocks
) {

    public static SightSettings defaults() {
        return new SightSettings(true, true, false, 2);
    }

    public static SightSettings from(FileConfiguration config) {
        if (config == null) {
            return defaults();
        }
        ConfigurationSection section = config.getConfigurationSection("sight");
        if (section == null) {
            return defaults();
        }
        return new SightSettings(
                section.getBoolean("enabled", true),
                section.getBoolean("world-border-combatants", true),
                section.getBoolean("world-border-spectators", false),
                Math.max(0, Math.min(8, section.getInt("pearl-max-lift-blocks", 2)))
        );
    }
}
