package com.rumilance.practice.practice;

/**
 * Pure numeric kernels shared by the bot behaviours. Everything here is Bukkit-free so the
 * local ECJ test loop (tools/localtest) can pin the constants that were transcribed from the
 * Quantum PvP Practice datapack / vanilla formulas without a running server.
 */
public final class BotMath {

    private BotMath() { }

    // ---- mace smash (Quantum quantum:mace/slam: damage scales with fallen height) ----

    /**
     * Fall distance in blocks from which a falling hit commits as a mace SLAM. This is the
     * map's {@code quantum:fall_distance15} predicate — the name means 1.5, not 15 — below it
     * the map never picks up the mace and only the grounded sword hit fires. A plain jump
     * (~1.25 blocks of fall) therefore does NOT slam; wind/lunge launches do.
     */
    public static final double MACE_SLAM_FALL_BLOCKS = 1.5d;
    /** Extra multiplier per additional fallen block past the slam threshold. */
    public static final double MACE_SMASH_PER_BLOCK = 0.35d;
    /** Hard cap so fall hits stay surmountable. */
    public static final double MACE_SMASH_MAX_SCALE = 3.0d;

    /** Smash damage multiplier for a mace hit landing after {@code fallDistance} blocks. */
    public static double maceSmashScale(double fallDistance) {
        double extra = Math.max(0.0d, fallDistance - MACE_SLAM_FALL_BLOCKS) * MACE_SMASH_PER_BLOCK;
        return Math.min(MACE_SMASH_MAX_SCALE, 1.0d + extra);
    }

    // ---- mace swing gates (Quantum mace pipeline, transcribed as it runs on Paper) ----

    /**
     * {@code allstats/newstats}: {@code in_range} — the target sits inside 4 blocks (plus the
     * binomial reach test on the hitbox distance). Wind, lunge and the re-approach rhythm all
     * key off {@code in_range = 0}.
     */
    public static final double MACE_IN_RANGE = 4.0d;
    /** {@code allstats/newstats}: {@code can_see_target} — the target selector radius for the
     *  eye raycast (or an instant pass when the target is inside a 2 block cube). */
    public static final double MACE_CAN_SEE = 3.2d;
    /** {@code decisions/player_hit}: the hit selector radius, measured from the bot's eyes. */
    public static final double MACE_HIT_RANGE = 3.0d;
    /** {@code decisions/spear}: the spear lunge fires only when NO target is within this
     *  horizontal distance (plus {@code in_range = 0} and the bot being airborne). */
    public static final double MACE_LUNGE_MIN_HORIZON = 4.0d;
    /** {@code mace_new/far_pearl}: the pearl is thrown only at a target at least this far. */
    public static final double MACE_FAR_PEARL_MIN_RANGE = 5.0d;
    /** {@code bot_mech/distance} W-tap: the bot may stop for one tick at this target distance
     *  (1 % roll per tick at the map's default tap chance). */
    public static final double MACE_W_TAP_RANGE = 1.8d;

    /** 50 ms per tick. */
    private static final long TICK_MS = 50L;

    /** {@code quantum:sword/combo/hit} + {@code quantum:sword/crit}: hitcd after a landed swing. */
    public static final long MACE_HITCD_HIT_MS = 11L * TICK_MS;
    /** {@code quantum:mace/lunge}: the spear lunge costs a longer 13. */
    public static final long MACE_HITCD_LUNGE_MS = 13L * TICK_MS;
    /** {@code quantum:cooldowns} line 1: while the main hand is NOT a sharp weapon (mace,
     *  spear, wind charge) the hitcd is pinned at 13 instead of decaying. */
    public static final long MACE_HITCD_NON_SHARP_FLOOR_MS = 13L * TICK_MS;
    /** {@code quantum:difficulty/2} at round start: {@code @a[xlib_bot] hitcd 15}. */
    public static final long MACE_HITCD_ROUND_START_MS = 15L * TICK_MS;
    /** {@code allstats/advancestats}: when the bot lands damage, its {@code real_hitcd} is set
     *  to 11 and decays one per tick — the S-tap window. */
    public static final long MACE_REAL_HITCD_MS = 11L * TICK_MS;
    /** {@code bot_mech/distance}: in mace mode the S-tap ({@code move backward}, which cancels
     *  the {@code move forward} of the same tick) is active while {@code real_hitcd >= 7}. */
    public static final long MACE_STAP_ACTIVE_MS = 7L * TICK_MS;
    /** {@code allstats/advancestats}: hitting the target sets ITS {@code hitcd} to 15 (gear 2)
     *  — the pcrit gate that keeps the crit variant from trading swings with its own hits. */
    public static final long MACE_TARGET_HITCD_MS = 15L * TICK_MS;
    /** {@code quantum:mace/wind}: {@code windcd = 20} after a wind launch. */
    public static final long MACE_WIND_CD_MS = 20L * TICK_MS;
    /** {@code quantum:pearl}: {@code pearlcd = 20} after every pearl throw. */
    public static final long MACE_PEARL_CD_MS = 20L * TICK_MS;
    /** {@code quantum:mace/wind_pearl_main}: {@code wind_pearl_cd = 10}. */
    public static final long MACE_WIND_PEARL_CD_MS = 10L * TICK_MS;
    /** {@code quantum:sword/strafe}: {@code strafecd = 5} — the strafe side re-rolls every 5
     *  ticks while the bot is grounded (combo variant only). */
    public static final long MACE_STRAFE_CD_MS = 5L * TICK_MS;

    /**
     * The mace's far-pearl / wind-pearl roll ({@code quantum:miscellaneous/random}, keyed on
     * the difficulty): Easy 20 %, Intermediate 40 %, Hard 60 %, Crazy 80 %, Master and above
     * always. NPC never fights and CUSTOM keeps the Intermediate 40 %.
     */
    public static double maceFarPearlChance(BotDifficulty diff) {
        if (diff == null || diff.preset() == BotDifficulty.Preset.NPC) {
            return 0.0d;
        }
        return switch (diff.preset()) {
            case EASY -> 0.20d;
            case INTERMEDIATE, CUSTOM -> 0.40d;
            case HARD -> 0.60d;
            case CRAZY -> 0.80d;
            case MASTER, SURVIVAL_MASTER -> 1.0d;
        };
    }
}
