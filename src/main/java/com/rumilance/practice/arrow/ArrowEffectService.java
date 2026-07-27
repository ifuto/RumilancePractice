package com.rumilance.practice.arrow;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.settings.SettingsService;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-density arrow trails with distance filtering. Central tracker, not per-arrow tick storms.
 */
public final class ArrowEffectService {

    public record EffectDef(String id, String displayName, Particle particle, int particlesPerTick) {
    }

    private final Plugin plugin;
    private final ConfigService configService;
    private final SettingsService settingsService;
    private final Map<String, EffectDef> effects = new ConcurrentHashMap<>();
    private final Map<UUID, AbstractArrow> tracked = new ConcurrentHashMap<>();
    private BukkitTask task;
    private int particleLimit = 40;

    public ArrowEffectService(Plugin plugin, ConfigService configService, SettingsService settingsService) {
        this.plugin = plugin;
        this.configService = configService;
        this.settingsService = settingsService;
        reload();
    }

    public void reload() {
        effects.clear();
        ConfigurationSection root = configService.arrowEffects().getConfigurationSection("effects");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String particleName = section.getString("particle");
                Particle particle = null;
                if (particleName != null && !particleName.isBlank()) {
                    try {
                        particle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        particle = null;
                    }
                }
                effects.put(id.toLowerCase(Locale.ROOT), new EffectDef(
                        id.toLowerCase(Locale.ROOT),
                        section.getString("display-name", id),
                        particle,
                        Math.max(1, section.getInt("particles-per-tick", 1))
                ));
            }
        }
        particleLimit = configService.config().getInt("performance.particle-limit-per-player", 40);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        tracked.clear();
    }

    public Set<String> effectIds() {
        return Set.copyOf(effects.keySet());
    }

    public EffectDef get(String id) {
        return effects.get(id.toLowerCase(Locale.ROOT));
    }

    public void track(AbstractArrow arrow, Player shooter) {
        if (!shooter.hasPermission("rumilance.user.mem")
                && !shooter.hasPermission("rumilance.user.vip")
                && !shooter.hasPermission("rumilance.user.vip_plus")) {
            return;
        }
        String effectId = settingsService.get(shooter).arrowEffect();
        EffectDef def = get(effectId);
        if (def == null || def.particle() == null) {
            return;
        }
        tracked.put(arrow.getUniqueId(), arrow);
    }

    private void tick() {
        tracked.entrySet().removeIf(entry -> {
            AbstractArrow arrow = entry.getValue();
            if (arrow.isDead() || !arrow.isValid() || arrow.isInBlock()) {
                return true;
            }
            if (!(arrow.getShooter() instanceof Player shooter)) {
                return true;
            }
            EffectDef def = get(settingsService.get(shooter).arrowEffect());
            if (def == null || def.particle() == null) {
                return true;
            }
            int sent = 0;
            for (Player viewer : arrow.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(arrow.getLocation()) > 48 * 48) {
                    continue;
                }
                if (sent >= particleLimit) {
                    break;
                }
                viewer.spawnParticle(def.particle(), arrow.getLocation(), def.particlesPerTick(), 0.01, 0.01, 0.01, 0);
                sent += def.particlesPerTick();
            }
            return false;
        });
    }
}
