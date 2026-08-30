package com.rumilance.practice.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Potion effect applied at match {@code beginFight} (after countdown), with splash-potion durations.
 *
 * @param potionEffectKey Minecraft effect id (e.g. {@code speed}, {@code jump_boost}); case-insensitive
 * @param amplifier       0-based amplifier (level 1 → 0, level 2 → 1)
 */
public record KitStartEffect(String potionEffectKey, int amplifier) {

    public KitStartEffect {
        Objects.requireNonNull(potionEffectKey, "potionEffectKey");
        potionEffectKey = potionEffectKey.trim().toLowerCase(Locale.ROOT);
        if (potionEffectKey.isEmpty()) {
            throw new IllegalArgumentException("potionEffectKey blank");
        }
        amplifier = Math.max(0, amplifier);
    }
}
