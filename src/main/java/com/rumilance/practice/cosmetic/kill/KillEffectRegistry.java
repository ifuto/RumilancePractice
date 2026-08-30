package com.rumilance.practice.cosmetic.kill;

import com.rumilance.practice.config.ConfigService;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and holds the configured {@link KillEffect} definitions from {@code kill-effects.yml}.
 * The free "None" option is always synthesized first; everything else is VIP+ only. Reloadable
 * at runtime via {@link #reload()}.
 */
public final class KillEffectRegistry {

    private final ConfigService configService;
    private final Logger logger;
    private volatile List<KillEffect> effects = List.of();

    public KillEffectRegistry(ConfigService configService, Logger logger) {
        this.configService = configService;
        this.logger = logger;
        reload();
    }

    public void reload() {
        List<KillEffect> loaded = new ArrayList<>();
        loaded.add(new KillEffect(KillEffect.NONE_ID, "<gray>None", Material.BARRIER,
                null, null, 0f, 0f, KillEffect.Shape.NONE, 0d, 0));

        ConfigurationSection root = configService.killEffects().getConfigurationSection("effects");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                if (KillEffect.NONE_ID.equalsIgnoreCase(id)) {
                    continue; // synthesized above
                }
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                Particle particle = parseParticle(s.getString("particle"));
                Sound sound = parseSound(s.getString("sound"));
                Material icon = parseMaterial(s.getString("icon"), Material.NETHER_STAR);
                KillEffect.Shape shape = KillEffect.Shape.parse(s.getString("shape"), KillEffect.Shape.BURST);
                String displayName = s.getString("display-name", id);
                float volume = (float) s.getDouble("volume", 1.0d);
                float pitch = (float) s.getDouble("pitch", 1.0d);
                double radius = s.getDouble("radius", 1.2d);
                int duration = Math.max(0, s.getInt("duration", 10));
                loaded.add(new KillEffect(id.toLowerCase(Locale.ROOT), displayName, icon,
                        particle, sound, volume, pitch, shape, radius, duration));
            }
        }
        this.effects = List.copyOf(loaded);
        logger.info("Loaded " + (loaded.size() - 1) + " kill effects from kill-effects.yml");
    }

    /** All selectable effects, with the free "None" option first. */
    public List<KillEffect> all() {
        return effects;
    }

    public KillEffect byId(String id) {
        if (id == null || id.isBlank() || KillEffect.NONE_ID.equalsIgnoreCase(id)) {
            return effects.isEmpty() ? null : effects.get(0);
        }
        for (KillEffect effect : effects) {
            if (effect.id().equalsIgnoreCase(id)) {
                return effect;
            }
        }
        return effects.isEmpty() ? null : effects.get(0);
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown kill-effect particle: " + name);
            return null;
        }
    }

    private Sound parseSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown kill-effect sound: " + name);
            return null;
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }
}
