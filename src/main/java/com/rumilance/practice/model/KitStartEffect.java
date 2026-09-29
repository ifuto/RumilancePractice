package com.rumilance.practice.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Potion effect applied at match {@code beginFight} (after countdown).
 *
 * @param potionEffectKey Minecraft effect id (e.g. {@code speed}, {@code jump_boost}); case-insensitive
 * @param amplifier       0-based amplifier (level 1 → 0, level 2 → 1)
 * @param durationTicks   duration in ticks, or {@code -1} to fall back to the splash-potion
 *                        duration table (all legacy entries created before the potion-deposit
 *                        editor carry -1 — their behaviour is unchanged)
 */
public record KitStartEffect(String potionEffectKey, int amplifier, int durationTicks) {

    /** Legacy entries (and the old palette UI saved by hand) keep the splash-potion durations. */
    public static final int DURATION_FROM_POTION_TABLE = -1;

    public KitStartEffect(String potionEffectKey, int amplifier) {
        this(potionEffectKey, amplifier, DURATION_FROM_POTION_TABLE);
    }

    public KitStartEffect {
        Objects.requireNonNull(potionEffectKey, "potionEffectKey");
        potionEffectKey = potionEffectKey.trim().toLowerCase(Locale.ROOT);
        if (potionEffectKey.isEmpty()) {
            throw new IllegalArgumentException("potionEffectKey blank");
        }
        amplifier = Math.max(0, amplifier);
        durationTicks = Math.max(durationTicks, DURATION_FROM_POTION_TABLE);
    }

    /** Human-friendly form of the configured duration ("8:00" / "0:03" / "splash default"). */
    public String durationLabel() {
        if (durationTicks < 0) {
            return "splash default";
        }
        int seconds = durationTicks / 20;
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }
}
