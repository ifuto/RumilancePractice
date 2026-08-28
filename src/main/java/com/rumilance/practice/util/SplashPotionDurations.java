package com.rumilance.practice.util;

import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Default splash-potion effect durations (ticks), matching vanilla splash lengths.
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
                 "night_vision", "water_breathing", "invisibility" -> amp >= 1 ? 67 * 20 : 135 * 20;
            case "regeneration", "poison" -> amp >= 1 ? 16 * 20 : 33 * 20;
            case "weakness", "slow_falling" -> 90 * 20;
            case "slowness" -> amp >= 1 ? 20 * 20 : 90 * 20;
            case "instant_health", "instant_damage" -> 1;
            case "turtle_master" -> amp >= 1 ? 7 * 20 : 15 * 20;
            default -> 45 * 20;
        };
    }
}
