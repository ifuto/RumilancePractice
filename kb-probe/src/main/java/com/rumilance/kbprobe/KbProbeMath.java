package com.rumilance.kbprobe;

/**
 * Pure knockback-measurement math of the KB Probe (no Minecraft classes, so it can be
 * unit-tested and driven by an offline simulator with the exact same numbers the in-game
 * code path uses — see {@code kb-probe/sim/KbProbeSim.java}).
 *
 * <p>Vanilla 1.21.1 reference behaviour the expectations below mirror (verified against the
 * deobfuscated yarn sources, and shared with the server's PaperCombatCompatListener):</p>
 * <ul>
 *   <li>{@code takeKnockback(strength, dx, dz)}: horizontal impulse of {@code strength*(1-r)}
 *       on the victim, composed halving-wise per stage ({@code newV = oldV/2 + impulse});
 *       grounded, stationary victims additionally receive
 *       {@code min(0.4, oldV.y/2 + strength*(1-r))} vertical.</li>
 *   <li>Melee hit = up to two stages: {@code damage()} applies 0.4; an attack with knockback
 *       level {@code k} (sprint bonus 1.0 + attack_knockback attribute) applies {@code k*0.5}
 *       as a second stage while the victim still counts as grounded (same tick), so the
 *       vertical also reaches {@code min(0.4, (0.2+0.5k)(1-r))}.</li>
 *   <li>Hence the final horizontal of a stationary victim is
 *       {@code (k>0 ? 0.2+0.5k : 0.4) * (1-r)}.</li>
 * </ul>
 */
public final class KbProbeMath {

    /** Vanilla base knockback strength / grounded Y from the damage stage. */
    public static final double BASE = 0.4d;
    /** Pushback vectors must point at least this much along the attack direction (dot). */
    public static final double DIR_MIN_DOT = 0.2d;
    /** Raw speeds beyond this are treated as outliers (explosions, KB sticks, events). */
    public static final double OUTLIER = 2.5d;
    /** Horizontal speed below this counts as "standing still" (≈ half walking speed). */
    public static final double IDLE_HORIZONTAL = 0.06d;
    /** Vertical speed below this counts as "standing still". */
    public static final double IDLE_VERTICAL = 0.1d;
    /** Vanilla velocity-packet scale: packets carry int velocity/8000-of-a-block. */
    public static final double VELOCITY_PACKET_SCALE = 8000.0d;
    /**
     * Vertical factors below this are rejected: vanilla leaves Y untouched when the victim is
     * airborne, so a "grounded at click, airborne at damage" hit yields fV≈0 — a fake sample
     * that would drag the vertical average toward ×0 (jump-between-click-and-hit exception).
     */
    public static final double VF_MIN = 0.05d;
    /** Vertical factors above this are impossible for a plain melee hit (external impulse). */
    public static final double VF_MAX = 8.0d;

    private KbProbeMath() {
    }

    /** Real blocks-per-tick value of a raw velocity-packet component. */
    public static double unscaleVelocity(int raw) {
        return raw / VELOCITY_PACKET_SCALE;
    }

    /** Expected final horizontal speed: k = attack knockback level, r = knockback resistance. */
    public static double expectHorizontal(double k, double r) {
        double strength = k > 0.0d ? 0.2d + 0.5d * k : BASE;
        return strength * (1.0d - r);
    }

    /** Expected vertical kick of a ground hit (capped every stage at 0.4 by vanilla). */
    public static double expectVertical(double k, double r) {
        return Math.min(BASE, expectHorizontal(k, r));
    }

    /** True when the local pre-packet velocity qualifies as a stationary baseline. */
    public static boolean isIdle(double vx, double vy, double vz) {
        return Math.hypot(vx, vz) <= IDLE_HORIZONTAL && Math.abs(vy) <= IDLE_VERTICAL;
    }

    /** True when the pushback roughly matches the attack direction (reject cross/noise). */
    public static boolean directionOk(double dxh, double dzh, double dirX, double dirZ,
                                      double hRaw) {
        if (hRaw <= 1.0e-4d) {
            return true;
        }
        return (dxh * dirX + dzh * dirZ) / hRaw >= DIR_MIN_DOT;
    }

    /** True when no component jumps outside the believable knockback range. */
    public static boolean outlier(double hRaw, double dy) {
        return hRaw > OUTLIER || Math.abs(dy) > OUTLIER;
    }
    /**
     * A vertical factor is only believable inside [{@link #VF_MIN}, {@link #VF_MAX}]:
     * below the floor the victim almost certainly left the ground between the click and the
     * server-side hit (vanilla keeps Y → measured fV≈0 is NOT the server's coefficient);
     * above the ceiling an external impulse (mace smash, wind charge, plugin skill) is mixed in.
     */
    public static boolean verticalFactorPlausible(double fV) {
        return fV >= VF_MIN && fV <= VF_MAX;
    }
}
