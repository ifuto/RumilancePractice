package com.rumilance.practice.util;

import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Default drinkable-potion effect durations (ticks) for kit start effects.
 * Java Edition 1.21+ splash potions use the same durations on a direct hit; kit start effects
 * are applied directly to the player, so drinkable values are used here.
 */
public final class SplashPotionDurations {

    private SplashPotionDurations() {
    }

    /** Normalizes kit/YAML keys ({@code JUMP}, {@code heal}, …) to Minecraft effect ids. */
    public static String normalizeKey(String potionEffectKey) {
        if (potionEffectKey == null || potionEffectKey.isBlank()) {
            return "";
        }
        String key = potionEffectKey.trim().toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace("minecraft:", "");
        return switch (key) {
            case "swiftness" -> "speed";
            case "jump", "leaping" -> "jump_boost";
            case "heal", "healing", "instant_heal" -> "instant_health";
            case "harm", "harming", "instant_harm" -> "instant_damage";
            default -> key;
        };
    }

    /**
     * @param type      effect type (null → fallback)
     * @param amplifier 0-based amplifier
     * @return duration in ticks
     */
    public static int ticks(PotionEffectType type, int amplifier) {
        if (type == null) {
            return 45 * 20;
        }
        int amp = Math.max(0, amplifier);
        return ticks(type.getKey().getKey(), amp);
    }

    /** Same as {@link #ticks(PotionEffectType, int)} using a Minecraft effect id string. */
    public static int ticks(String potionEffectKey, int amplifier) {
        int amp = Math.max(0, amplifier);
        String key = normalizeKey(potionEffectKey);
        if (key.isEmpty()) {
            return 45 * 20;
        }

        return switch (key) {
            case "speed", "strength", "jump_boost", "fire_resistance",
                 "night_vision", "water_breathing", "invisibility" -> amp >= 1 ? 90 * 20 : 180 * 20;
            case "regeneration" -> amp >= 1 ? 22 * 20 : 45 * 20;
            case "poison" -> amp >= 1 ? 21 * 20 : 45 * 20;
            case "weakness", "slow_falling" -> 90 * 20;
            case "slowness" -> amp >= 1 ? 20 * 20 : 90 * 20;
            case "instant_health", "instant_damage" -> 1;
            case "turtle_master" -> 20 * 20;
            default -> 45 * 20;
        };
    }
}
