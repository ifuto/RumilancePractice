package com.rumilance.practice.cosmetic.kill;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * A paid kill-effect cosmetic played at a victim's death position when the killer has one
 * selected. Effects are intentionally short and self-contained (a handful of particle rings /
 * bursts, an optional sound) so they never affect gameplay or linger. All kill effects are
 * VIP+ only — the free "None" entry turns the cosmetic off.
 *
 * <p>Definitions are loaded from {@code kill-effects.yml} (see {@link KillEffectRegistry});
 * this class is just the resolved value plus the lightweight animation renderer.</p>
 */
public record KillEffect(
        String id,
        String displayName,
        Material icon,
        Particle particle,
        Sound sound,
        float volume,
        float pitch,
        Shape shape,
        double radius,
        int durationTicks
) {

    public enum Shape {
        /** Sound + initial burst only, no expansion. */
        NONE,
        /** Expanding horizontal ring. */
        RING,
        /** Vertical helix twisting upward. */
        HELIX,
        /** Straight column rising from the ground. */
        COLUMN,
        /** Filled sphere shell. */
        SPHERE,
        /** Single one-shot explosion burst. */
        BURST;

        static Shape parse(String raw, Shape fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Shape.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    public static final String NONE_ID = "none";

    public boolean isNone() {
        return NONE_ID.equalsIgnoreCase(id) || particle == null;
    }

    /** Immediately render the effect at {@code origin} (victim location). */
    public void play(Location origin) {
        if (isNone() || origin.getWorld() == null) {
            return;
        }
        World world = origin.getWorld();
        Location at = origin.clone().add(0, 1.0, 0);
        if (sound != null) {
            world.playSound(at, sound, volume, pitch);
        }
        // Every effect starts with a small central burst for impact.
        world.spawnParticle(particle, at, 12, 0.3, 0.4, 0.3, 0.01);
    }

    /**
     * Drives the multi-tick expansion (called once per tick for {@link #durationTicks()} ticks).
     * {@code tick} is 0..duration. Kept light so several simultaneous FFA finishes stay cheap.
     */
    public void playTick(Location origin, int tick) {
        if (isNone() || origin.getWorld() == null || shape == Shape.NONE || shape == Shape.BURST) {
            return;
        }
        World world = origin.getWorld();
        double progress = durationTicks <= 0 ? 1.0 : Math.min(1.0, (double) tick / durationTicks);
        double r = shape == Shape.RING || shape == Shape.SPHERE ? radius * progress : radius;
        Location base = origin.clone().add(0, 0.2, 0);
        switch (shape) {
            case RING -> ring(world, base, r, tick);
            case HELIX -> helix(world, base, progress, tick);
            case COLUMN -> column(world, base, progress);
            case SPHERE -> sphere(world, base, r);
            default -> { }
        }
    }

    private void ring(World world, Location base, double r, int tick) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i + tick * 12.0);
            world.spawnParticle(particle,
                    base.clone().add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r),
                    1, 0, 0, 0, 0);
        }
    }

    private void helix(World world, Location base, double progress, int tick) {
        int points = 12;
        double height = 1.8 * progress;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians((360.0 / points) * i + tick * 24.0);
            double rr = radius * (1.0 - progress * 0.6);
            world.spawnParticle(particle,
                    base.clone().add(Math.cos(angle) * rr, height, Math.sin(angle) * rr),
                    1, 0, 0, 0, 0);
        }
    }

    private void column(World world, Location base, double progress) {
        double height = 2.0 * progress;
        for (double y = 0; y < height; y += 0.25) {
            world.spawnParticle(particle, base.clone().add(0, y, 0), 1, 0.12, 0, 0.12, 0);
        }
    }

    private void sphere(World world, Location base, double r) {
        for (int i = 0; i < 20; i++) {
            Vector v = new Vector(
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2,
                    (Math.random() - 0.5) * 2).normalize().multiply(r);
            world.spawnParticle(particle, base.clone().add(v), 1, 0, 0, 0, 0);
        }
    }
}
