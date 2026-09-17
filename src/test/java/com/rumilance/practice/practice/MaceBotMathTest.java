package com.rumilance.practice.practice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure math behind the mace bot's fight (Quantum parity: {@code quantum:mace/tick},
 * {@code mace/lunge}, {@code mace/wind}) and the smooth head-turning every bot now shares.
 * None of it touches Bukkit, so it is pinned here instead of only in-game.
 */
class MaceBotMathTest {

    private static final BotDifficulty.Preset[] LADDER = {
            BotDifficulty.Preset.NPC,
            BotDifficulty.Preset.EASY,
            BotDifficulty.Preset.INTERMEDIATE,
            BotDifficulty.Preset.HARD,
            BotDifficulty.Preset.CRAZY,
            BotDifficulty.Preset.MASTER,
            BotDifficulty.Preset.SURVIVAL_MASTER
    };

    @Test
    void smashScalesWithFallDistanceButStaysCapped() {
        // No fall, or a fall below the slam threshold: no bonus.
        assertEquals(1.0d, BotMath.maceSmashScale(0.0d), 1e-9);
        assertEquals(1.0d, BotMath.maceSmashScale(-3.0d), 1e-9);
        assertEquals(1.0d, BotMath.maceSmashScale(0.9d), 1e-9);
        // A normal jump (~1.25 blocks of fall) does NOT reach the map's slam threshold
        // (the fall_distance15 predicate = 1.5): the map keeps the sword for those.
        assertEquals(1.0d, BotMath.maceSmashScale(1.25d), 1e-9);
        assertEquals(1.0d, BotMath.maceSmashScale(1.5d), 1e-9);
        // A wind-charge launch (about 5 blocks of fall) is a big one ...
        assertEquals(1.0d + 0.35d * 3.5d, BotMath.maceSmashScale(5.0d), 1e-9);
        // ... but even falling out of the sky cannot exceed the cap, so one smash can never
        // delete a full-health player.
        assertEquals(3.0d, BotMath.maceSmashScale(50.0d), 1e-9);
        assertEquals(3.0d, BotMath.maceSmashScale(1000.0d), 1e-9);
        // Monotonic: more height is never weaker.
        double previous = 0.0d;
        for (double fall = 0.0d; fall <= 20.0d; fall += 0.5d) {
            double scale = BotMath.maceSmashScale(fall);
            assertTrue(scale >= previous, "fall " + fall);
            previous = scale;
        }
    }

    @Test
    void farPearlRollFollowsTheDifficultyRungs() {
        // quantum:miscellaneous/random keyed on .difficulty: 0..79 fail (Easy 20 %),
        // 0..59 fail (Intermediate 40 %), 0..39 (Hard 60 %), 0..19 (Crazy 80 %), Master+ always.
        assertEquals(0.0d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.NPC)), 1e-9);
        assertEquals(0.20d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.EASY)), 1e-9);
        assertEquals(0.40d, BotMath.maceFarPearlChance(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)), 1e-9);
        assertEquals(0.60d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.HARD)), 1e-9);
        assertEquals(0.80d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.CRAZY)), 1e-9);
        assertEquals(1.0d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.MASTER)), 1e-9);
        assertEquals(1.0d, BotMath.maceFarPearlChance(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)), 1e-9);
        // CUSTOM keeps the Intermediate 40 %; a null diff never pearls.
        assertEquals(0.40d, BotMath.maceFarPearlChance(BotDifficulty.of(BotDifficulty.Preset.CUSTOM)), 1e-9);
        assertEquals(0.0d, BotMath.maceFarPearlChance(null), 1e-9);
    }

    @Test
    void maceGatesAreTranscribedFromTheMap() {
        // Predicate/scoreboard values from the Quantum pack (1.21.11), pinned so the
        // transcription cannot drift (the fall_distance15 name means 1.5, not 15).
        assertEquals(1.5d, BotMath.MACE_SLAM_FALL_BLOCKS, 1e-9);
        assertEquals(4.0d, BotMath.MACE_IN_RANGE, 1e-9);
        assertEquals(3.2d, BotMath.MACE_CAN_SEE, 1e-9);
        assertEquals(3.0d, BotMath.MACE_HIT_RANGE, 1e-9);
        assertEquals(4.0d, BotMath.MACE_LUNGE_MIN_HORIZON, 1e-9);
        assertEquals(5.0d, BotMath.MACE_FAR_PEARL_MIN_RANGE, 1e-9);
        assertEquals(1.8d, BotMath.MACE_W_TAP_RANGE, 1e-9);
        // 50 ms per tick.
        assertEquals(550L, BotMath.MACE_HITCD_HIT_MS);      // combo/hit + sword/crit: 11t
        assertEquals(650L, BotMath.MACE_HITCD_LUNGE_MS);    // mace/lunge: 13t
        assertEquals(650L, BotMath.MACE_HITCD_NON_SHARP_FLOOR_MS); // cooldowns: 13t
        assertEquals(750L, BotMath.MACE_HITCD_ROUND_START_MS);    // difficulty/2: 15t
        assertEquals(550L, BotMath.MACE_REAL_HITCD_MS);     // advancestats: 11t
        assertEquals(350L, BotMath.MACE_STAP_ACTIVE_MS);    // bot_mech/distance: >= 7
        assertEquals(750L, BotMath.MACE_TARGET_HITCD_MS);   // advancestats: 15t (gear 2)
        assertEquals(1000L, BotMath.MACE_WIND_CD_MS);       // mace/wind: 20t
        assertEquals(1000L, BotMath.MACE_PEARL_CD_MS);      // quantum:pearl: 20t
        assertEquals(500L, BotMath.MACE_WIND_PEARL_CD_MS);  // wind_pearl_main: 10t
        assertEquals(250L, BotMath.MACE_STRAFE_CD_MS);      // sword/strafe: 5t
    }

    @Test
    void lookDeltaFollowsTheMapAimRungs() {
        // quantum:look snaps instantly with delta = aim rung - 1 degrees. The old per-tick
        // turn ladder was a misread of the unused slowcast scoreboard and made low rungs
        // unable to track a strafe (the 'weird bot that never swings').
        assertEquals(4.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.EASY)), 1e-9);
        assertEquals(3.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)), 1e-9);
        assertEquals(2.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.HARD)), 1e-9);
        assertEquals(1.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)), 1e-9);
        assertEquals(1.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)), 1e-9);
        assertEquals(1.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)), 1e-9);
        // CUSTOM defaults to the INTERMEDIATE-like aim 4 -> delta 3 (the editor slider moves it).
        assertEquals(3.0d, PracticeService.lookDeltaDegrees(
                BotDifficulty.of(BotDifficulty.Preset.CUSTOM)), 1e-9);
    }


    @Test
    void yawStepsTakeTheShortestWayRoundAndNeverOvershoot() {
        // Within the budget: arrive exactly.
        assertEquals(3.0d, PracticeService.stepAngle(0.0d, 3.0d, 5.0d), 1e-9);
        // Beyond the budget: move by exactly the budget.
        assertEquals(5.0d, PracticeService.stepAngle(0.0d, 90.0d, 5.0d), 1e-9);
        assertEquals(-5.0d, PracticeService.stepAngle(0.0d, -90.0d, 5.0d), 1e-9);
        // Across the 180/-180 seam the short way is +10 degrees, not -350.
        assertEquals(175.0d, PracticeService.stepAngle(170.0d, -170.0d, 5.0d), 1e-9);
        assertEquals(-175.0d, PracticeService.stepAngle(-170.0d, 170.0d, 5.0d), 1e-9);
        // Already facing the target (also true for a full turn apart): no drift.
        assertEquals(42.0d, PracticeService.stepAngle(42.0d, 42.0d, 5.0d), 1e-9);
        assertEquals(0.0d, PracticeService.stepAngle(0.0d, 360.0d, 5.0d), 1e-9);
        assertEquals(359.0d, PracticeService.stepAngle(359.0d, 719.0d, 5.0d), 1e-9);
    }

    @Test
    void pitchStepsAreClampedWithoutWrapping() {
        assertEquals(15.0d, PracticeService.stepValue(10.0d, 30.0d, 5.0d), 1e-9);
        assertEquals(12.0d, PracticeService.stepValue(10.0d, 12.0d, 5.0d), 1e-9);
        assertEquals(5.0d, PracticeService.stepValue(10.0d, -30.0d, 5.0d), 1e-9);
        assertEquals(-89.0d, PracticeService.stepValue(-89.0d, -89.0d, 5.0d), 1e-9);
    }

    @Test
    void clampStepIsSymmetric() {
        assertEquals(2.0d, PracticeService.clampStep(2.0d, 5.0d), 1e-9);
        assertEquals(5.0d, PracticeService.clampStep(9.0d, 5.0d), 1e-9);
        assertEquals(-5.0d, PracticeService.clampStep(-9.0d, 5.0d), 1e-9);
        assertEquals(0.0d, PracticeService.clampStep(0.0d, 5.0d), 1e-9);
    }
}
