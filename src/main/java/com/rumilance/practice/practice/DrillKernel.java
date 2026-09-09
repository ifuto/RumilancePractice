package com.rumilance.practice.practice;

/**
 * Pure engine for the practice drills ported from Quantum's PvP Practice
 * (datapack {@code mech_train}). Every constant cites the .mcfunction line it was read
 * from, so the Bukkit-free kernel can be unit-tested for datapack parity in the local
 * ECJ test loop (tools/localtest) while PracticeService feeds it live Bukkit state.
 *
 * <p>All times are milliseconds; the datapack counts in ticks (20 t/s).
 */
public final class DrillKernel {

    private DrillKernel() { }

    // ---------------------------------------------------------------- cycle machine
    /** Attempt cycle: ARMED waits for nextAtMs, fires the attempt, grades, idles, re-arms. */
    public static final int STAGE_ARMED = 0;
    public static final int STAGE_ACTIVE = 1;
    /** Ledge's two-phase attempt: repositioned, pearl pending. */
    public static final int STAGE_LEDGE_PEARL = 2;

    /** Local pacing cadence (the datapack triggers loops via resetcd chains; see generic/reset_scores). */
    public static final long INTERMISSION_MS = 2200L;
    /** One attempt window before the grade defaults to "Failed!". */
    public static final long ATTEMPT_WINDOW_MS = 9000L;

    /** Stateless transition of one cycle step. */
    public record Step(int stage, long nextAtMs, int popsAtStart,
                       boolean fireAttempt, boolean gradeNow, boolean gradeSuccess) { }

    /**
     * Advance the cycle by one tick.
     *
     * @param stage        current stage ({@link #STAGE_ARMED} / {@link #STAGE_ACTIVE})
     * @param nextAtMs     epoch ms when the current stage expires
     * @param popsAtStart  pop count captured when the attempt fired
     * @param popsNow      pop count right now
     * @param now          current epoch ms
     */
    public static Step advance(int stage, long nextAtMs, int popsAtStart, int popsNow, long now) {
        if (stage == STAGE_ARMED) {
            if (now >= nextAtMs) {
                return new Step(STAGE_ACTIVE, now + ATTEMPT_WINDOW_MS, popsNow, true, false, false);
            }
            return new Step(STAGE_ARMED, nextAtMs, popsAtStart, false, false, false);
        }
        if (stage == STAGE_ACTIVE) {
            boolean success = popsNow > popsAtStart;
            if (now >= nextAtMs || success) {
                // Grade immediately on a pop, otherwise at the window close.
                return new Step(STAGE_ARMED, now + INTERMISSION_MS, popsAtStart, false, true, success);
            }
            return new Step(STAGE_ACTIVE, nextAtMs, popsAtStart, false, false, false);
        }
        // Unknown stage -> re-arm silently (defensive; mirrors reset_trigger recovery).
        return new Step(STAGE_ARMED, Math.min(nextAtMs, now), popsAtStart, false, false, false);
    }

    /** Ledge two-phase attempt step. */
    public record LedgeStep(int stage, long nextAtMs, int popsAtStart,
                            boolean fireReposition, boolean firePearl,
                            boolean gradeNow, boolean gradeSuccess) { }

    /**
     * crystal/ledge/loop: reposition bot ~30x away + player ~ ~ ~14, wait pearlcd2=15 ticks
     * (750ms), dash-pearl in, then the normal grade window closes the attempt.
     */
    public static LedgeStep advanceLedge(int stage, long nextAtMs, int popsAtStart,
                                         int popsNow, long now) {
        if (stage == STAGE_ARMED) {
            if (now >= nextAtMs) {
                return new LedgeStep(STAGE_LEDGE_PEARL, now + LEDGE_PEARL_DELAY_MS, popsNow,
                        true, false, false, false);
            }
            return new LedgeStep(STAGE_ARMED, nextAtMs, popsAtStart, false, false, false, false);
        }
        if (stage == STAGE_LEDGE_PEARL) {
            if (now >= nextAtMs) {
                return new LedgeStep(STAGE_ACTIVE, now + ATTEMPT_WINDOW_MS, popsAtStart,
                        false, true, false, false);
            }
            return new LedgeStep(STAGE_LEDGE_PEARL, nextAtMs, popsAtStart, false, false, false, false);
        }
        if (stage == STAGE_ACTIVE) {
            boolean success = popsNow > popsAtStart;
            if (now >= nextAtMs || success) {
                return new LedgeStep(STAGE_ARMED, now + INTERMISSION_MS, popsAtStart,
                        false, false, true, success);
            }
            return new LedgeStep(STAGE_ACTIVE, nextAtMs, popsAtStart, false, false, false, false);
        }
        return new LedgeStep(STAGE_ARMED, Math.min(nextAtMs, now), popsAtStart,
                false, false, false, false);
    }

    // ---------------------------------------------------------------- grade strings
    // Exact actionbar texts from the mode loop.mcfunction files.
    /** mace/elytra/loop:1-2 */
    public static final String G_MACE_PASS = "Slam!";
    public static final String G_MACE_FAIL = "Failed!";
    /** mace/stun_slam/loop:1 */
    public static final String G_STUN_PASS = "Stun Slam!";
    /** mace/far_pearl/loop:1,4 / crystal/ledge/loop:3 / crystal/dtap/loop:3 / crystal/hit_anchor/loop:3 */
    public static final String G_SAD_FAIL = "\uD83E\uDD7A Failed!";
    /** crystal/dtap/loop:1 */
    public static final String G_DTAP_PASS = "ez Dtap";
    /** crystal/hit_anchor/loop:1 */
    public static final String G_ANCHOR_PASS = "Digging isn't meta!";
    /** mace/divebomb/loop:7 (shield_cd variants omitted: shield_cd is a stun branch) */
    public static final String G_DIVE_PASS = "Stun Slam Dive Bomb?!?!?!?";
    /** crystal/ledge/loop:1 */
    public static final String G_LEDGE_PASS = "Ledge Dash!";

    /** The actionbar a finished attempt shows for {@code mode}, success-dependent. */
    public static String gradeText(PracticeMode mode, boolean success) {
        return switch (mode) {
            case MACE_ELYTRA -> success ? G_MACE_PASS : G_MACE_FAIL;
            case MACE_FAR_PEARL -> success ? G_MACE_PASS : G_SAD_FAIL; // far_pearl/loop:4
            case MACE_STUN_SLAM -> success ? G_STUN_PASS : G_MACE_FAIL;
            case MACE_DIVEBOMB -> success ? G_DIVE_PASS : G_MACE_FAIL;
            case CRYSTAL_DTAP -> success ? G_DTAP_PASS : G_SAD_FAIL;
            case CRYSTAL_LEDGE -> success ? G_LEDGE_PASS : G_SAD_FAIL;
            case CRYSTAL_HIT_ANCHOR -> success ? G_ANCHOR_PASS : G_SAD_FAIL;
            default -> success ? G_MACE_PASS : G_MACE_FAIL;
        };
    }

    // ---------------------------------------------------------------- mace far pearl
    // mace/far_pearl/loop:7 `spreadplayers ~ ~ 15 15 false quantumbot` at marker ~ ~10 ~:
    // vanilla spreads each target uniformly inside the square of half-side maxRange (15).
    /** Inclusive |dx|/|dz| cap for the scatter square. */
    public static final int FAR_PEARL_SCATTER_RADIUS = 15;
    /** Height offset above the anchor where the bot materialises. */
    public static final int FAR_PEARL_HEIGHT = 10;
    /** Single-target uniform square sample, vanilla spreadplayers-style. */
    public static int farPearlScatter(java.util.Random rng, int axis) {
        return rng.nextInt(FAR_PEARL_SCATTER_RADIUS * 2 + 1) - FAR_PEARL_SCATTER_RADIUS;
    }
    // mace/far_pearl/init:8-9 — the drill bot is a glass cannon.
    public static final double FAR_PEARL_BOT_MAX_HEALTH = 2.0d;

    // ---------------------------------------------------------------- mace elytra
    // mace/elytra/loop:4 `tp @a[tag=xlib_target] ~ ~30 ~10` at the marker.
    public static final int ELYTRA_PLAYER_Y = 30;
    public static final int ELYTRA_PLAYER_Z = 10;

    // ---------------------------------------------------------------- mace divebomb
    // mace/divebomb/loop:13 `tp @a ~ ~30 ~15 facing entity bot`.
    public static final int DIVEBOMB_PLAYER_Y = 30;
    public static final int DIVEBOMB_PLAYER_Z = 15;
    // mace/divebomb/loop:1-3 — every loop: clear elytra, give netherite_chestplate,
    // then item-replace armor.chest with elytra (restore happens next loop / at teardown).
    // mace/divebomb/loop:23 wind stand sits `^ ^ ^-2` behind the player facing the bot.

    // ---------------------------------------------------------------- mace stun slam
    // mace/stun_slam/loop:9 summons the quantum:large_wind_burst marker AT the player;
    // our port launches both participants upward in place of the burst.
    public static final double STUN_SLAM_PLAYER_LAUNCH = 0.95d;
    public static final double STUN_SLAM_BOT_LAUNCH = 0.9d;

    // ---------------------------------------------------------------- crystal dtap
    // crystal/dtap/tick:2 `if entity @a[distance=..3] run scoreboard players set @s hitcd 7`.
    public static final double DTAP_HIT_RANGE = 3.0d;
    public static final int DTAP_HITCD_TICKS = 7;
    public static final long DTAP_HITCD_MS = DTAP_HITCD_TICKS * 50L;
    /** dtap/loop resets the pair 10 blocks apart at the home line. */
    public static final double DTAP_RESET_DISTANCE = 10.0d;

    // ---------------------------------------------------------------- crystal ledge
    // crystal/ledge/loop:5-9 — bot (marker) shifts ~30 ~ ~; player `~ ~ ~14 facing bot`;
    // pearlcd2 set 15 ticks -> the dash pearl flies 750ms after reposition; slow_falling
    // infinite on the player.
    public static final int LEDGE_BOT_SHIFT = 30;
    public static final int LEDGE_PLAYER_Z = 14;
    public static final long LEDGE_PEARL_DELAY_MS = 15L * 50L;

    // ---------------------------------------------------------------- crystal hit anchor
    // crystal/hit_anchor init: anchor_timer 22 / resetcd 4; loop detonates a fresh anchor
    // per attempt — our port lowers the anchor mixup gate to this cadence.
    public static final long HIT_ANCHOR_CADENCE_MIN_MS = 1500L;

    // ---------------------------------------------------------------- pot drills
    // Local-making-pragmatic drills (datapack routes real logic through quantum: hotbar
    // top-up cadence; repot heals the bot from its own splash stock).
    public static final double REPOT_HEALTH_FRACTION = 0.65d;
    public static final double REPOT_HEAL = 6.0d;
    public static final long REPOT_COOLDOWN_MS = 2500L;
    public static final long REFILL_COOLDOWN_MS = 4000L;
    public static final int REFILL_QUANTITY = 16;
    /** pot/refill loop lines: hotbar slot 1; refill_inventory also stock-piles slot 12. */
    public static final int REFILL_HOTBAR_SLOT = 1;
    public static final int REFILL_INV_SLOT = 12;

    // ---------------------------------------------------------------- cart power tiers
    // Local power ladder (datapack ships fixed modes; these scale the envelope, tier -3..+3).
    public static final int CART_BASE_FUSE_TICKS = 26;
    public static final int CART_FUSE_PER_TIER = 2;
    public static final int CART_MIN_FUSE_TICKS = 12;
    public static final double CART_BASE_YIELD = 4.0d;
    public static final double CART_YIELD_PER_TIER = 0.6d;
    /** T3 cadence multiplier floor (TNT every comboCd*(1-0.12*tier) ms, min 700ms). */
    public static final double CART_CADENCE_PER_TIER = 0.12d;
    public static final long CART_MIN_CADENCE_MS = 700L;
    public static final double CART_BOW_BASE_FACTOR = 0.7d;
    public static final double CART_BOW_PER_TIER = 0.18d;

    public static int cartFuseTicks(int tier) {
        return Math.max(CART_MIN_FUSE_TICKS, CART_BASE_FUSE_TICKS - CART_FUSE_PER_TIER * tier);
    }

    public static float cartYield(int tier) {
        return (float) (CART_BASE_YIELD + CART_YIELD_PER_TIER * tier);
    }

    public static long cartCooldownMs(long baseCooldownMs, int tier) {
        return Math.max(CART_MIN_CADENCE_MS,
                (long) Math.round(baseCooldownMs * (1.0d - CART_CADENCE_PER_TIER * tier)));
    }

    public static double cartBowFactor(int tier) {
        return CART_BOW_BASE_FACTOR + CART_BOW_PER_TIER * tier;
    }
}
