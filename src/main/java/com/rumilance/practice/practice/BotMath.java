package com.rumilance.practice.practice;

/**
 * Pure numeric kernels shared by the bot behaviours. Everything here is Bukkit-free so the
 * local ECJ test loop (tools/localtest) can pin the constants that were transcribed from the
 * Quantum PvP Practice datapack / vanilla formulas without a running server.
 */
public final class BotMath {

    private BotMath() { }

    // ---- mace smash (Quantum quantum:mace/slam: damage scales with fallen height) ----
    /** Fall distance beyond this many blocks starts amplifying the smash. */
    public static final double SMASH_FALL_BLOCKS = 0.9d;
    /** Extra multiplier per additional fallen block. */
    public static final double SMASH_PER_BLOCK = 0.35d;
    /** Hard cap so fall hits stay surmountable. */
    public static final double SMASH_MAX_SCALE = 3.0d;

    /** Smash damage multiplier for a mace hit landing after {@code fallDistance} blocks. */
    public static double maceSmashScale(double fallDistance) {
        double extra = Math.max(0.0d, fallDistance - SMASH_FALL_BLOCKS) * SMASH_PER_BLOCK;
        return Math.min(SMASH_MAX_SCALE, 1.0d + extra);
    }
}
