package com.rumilance.practice.team;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-team battle configuration, edited by the party owner in the team list screen and
 * applied to every member of that team when the party fight starts:
 * <ul>
 *   <li>{@code maxHealth} — team max HP (default 20), applied via the MAX_HEALTH attribute
 *       and reset to 20 by {@code PlayerVitals.resetMaxHealth} when the fight ends.</li>
 *   <li>{@code scale} — body size multiplier (default 1.0), applied via the SCALE attribute
 *       and reset the same way.</li>
 *   <li>{@code effects} — potion effects always active for the team (type &rarr; amplifier,
 *       0-based like vanilla /effect). Applied with an "infinite" duration at fight start;
 *       the standard combat-state cleanup strips them at the end.</li>
 *   <li>{@code customKitId} — optional per-team loadout kit. Map rules (block place/break,
 *       pearls...) always follow the match-wide kit.</li>
 * </ul>
 */
public record TeamConfig(
        double maxHealth,
        double scale,
        Map<PotionEffectType, Integer> effects,
        String customKitId
) {

    public static final double DEFAULT_MAX_HEALTH = 20.0d;
    public static final double DEFAULT_SCALE = 1.0d;
    /** Bounds for owner-editable values (anti foot-gun, keeps fights sane). */
    public static final double MIN_MAX_HEALTH = 2.0d;
    public static final double MAX_MAX_HEALTH = 100.0d;
    public static final double MIN_SCALE = 0.25d;
    public static final double MAX_SCALE = 4.0d;

    public TeamConfig {
        maxHealth = clamp(maxHealth, MIN_MAX_HEALTH, MAX_MAX_HEALTH);
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);
        effects = effects == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(effects));
        customKitId = customKitId == null || customKitId.isBlank() ? null : customKitId;
    }

    /** The untouched default configuration (20 HP, scale 1, no effects, shared kit). */
    public static TeamConfig defaults() {
        return new TeamConfig(DEFAULT_MAX_HEALTH, DEFAULT_SCALE, Map.of(), null);
    }

    public boolean isDefault() {
        return maxHealth == DEFAULT_MAX_HEALTH && scale == DEFAULT_SCALE
                && effects.isEmpty() && customKitId == null;
    }

    public TeamConfig withMaxHealth(double value) {
        return new TeamConfig(value, scale, effects, customKitId);
    }

    public TeamConfig withScale(double value) {
        return new TeamConfig(maxHealth, value, effects, customKitId);
    }

    public TeamConfig withEffects(Map<PotionEffectType, Integer> newEffects) {
        return new TeamConfig(maxHealth, scale, newEffects, customKitId);
    }

    public TeamConfig withCustomKitId(String kitId) {
        return new TeamConfig(maxHealth, scale, effects, kitId);
    }

    /** Applies max health, body scale and the always-on effects to a team member. */
    public void apply(Player player) {
        Objects.requireNonNull(player, "player");
        try {
            AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(maxHealth);
            }
            // Applied at fight start right after the kit heal: fill to the team's max.
            player.setHealth(maxHealth);
        } catch (RuntimeException ignored) {
            // Attribute edge cases must never break fight start.
        }
        try {
            AttributeInstance scaleAttr = player.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }
        } catch (RuntimeException | NoSuchFieldError | NoClassDefFoundError ignored) {
            // Pre-1.20.5 servers have no SCALE attribute: size stays vanilla.
        }
        for (Map.Entry<PotionEffectType, Integer> entry : effects.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            int amplifier = Math.max(0, Math.min(255, entry.getValue() == null ? 0 : entry.getValue()));
            player.addPotionEffect(new PotionEffect(
                    entry.getKey(), PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
        }
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
