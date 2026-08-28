package com.rumilance.practice.tnt;

/**
 * Pure helpers for practice TNT / creeper fuse. Numbers match G1axPracticeTNT 1.0.0
 * (20-tick TNT fuse) without depending on Bukkit.
 *
 * <p>Creeper {@code fuseTicks} is elapsed primed time (vanilla max 30). Remaining delay
 * until explode is {@code maxFuse - elapsed}. TNT {@code fuseTicks} is remaining time.</p>
 */
public final class PracticeTnt {

    /** 1 second. Vanilla TNT remaining fuse is 80; vanilla creeper max swell is 30. */
    public static final int DEFAULT_FUSE_TICKS = 20;
    public static final int MIN_FUSE_TICKS = 0;
    public static final int MAX_FUSE_TICKS = 80;
    /** Center the primed entity in the placed block, same as G1ax {@code add(0.5, 0, 0.5)}. */
    public static final double CENTER_OFFSET = 0.5d;

    private PracticeTnt() {
    }

    public static int clampFuseTicks(int ticks) {
        if (ticks < MIN_FUSE_TICKS) {
            return MIN_FUSE_TICKS;
        }
        if (ticks > MAX_FUSE_TICKS) {
            return MAX_FUSE_TICKS;
        }
        return ticks;
    }

    public static boolean isTntMaterial(String materialName) {
        return "TNT".equals(materialName);
    }

    public static boolean isSpawnerEggReason(String spawnReasonName) {
        return "SPAWNER_EGG".equals(spawnReasonName);
    }

    public static boolean isGlass(String materialName) {
        return materialName.endsWith("GLASS") || materialName.endsWith("GLASS_PANE");
    }

    /**
     * Ticks until a creeper at {@code elapsedFuse} / {@code maxFuse} must explode.
     * Always at least 1 so a spawn-tick explode() cannot run before the entity exists.
     */
    public static int creeperExplodeDelayTicks(int maxFuse, int elapsedFuse) {
        int max = Math.max(1, clampFuseTicks(maxFuse));
        int elapsed = Math.max(0, elapsedFuse);
        return Math.max(1, max - elapsed);
    }
}
