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
        // No fall, or a fall below the smash threshold: a plain melee hit, no bonus.
        assertEquals(1.0d, BotMath.maceSmashScale(0.0d), 1e-9);
        assertEquals(1.0d, BotMath.maceSmashScale(-3.0d), 1e-9);
        assertEquals(1.0d, BotMath.maceSmashScale(0.9d), 1e-9);
        // A normal jump (about 1.25 blocks of fall) is a small smash.
        assertEquals(1.0d + 0.35d * 0.35d, BotMath.maceSmashScale(1.25d), 1e-9);
        // A wind-charge launch (about 5 blocks of fall) is a big one ...
        assertEquals(1.0d + 0.35d * 4.1d, BotMath.maceSmashScale(5.0d), 1e-9);
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
    void turnRateFollowsTheRungsAimError() {
        // aim 20 - aimSpread: precise rungs track a strafing player, sloppy ones get circled.
        assertEquals(8.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.NPC)), 1e-9);            // aim 12
        assertEquals(12.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.EASY)), 1e-9);           // aim 8
        assertEquals(15.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)), 1e-9);   // aim 5
        assertEquals(19.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)), 1e-9);         // aim 1
        assertEquals(20.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)), 1e-9); // aim 0
        // The ladder is monotonic in aim, so the turn rate is monotonic too.
        double previous = 0.0d;
        for (BotDifficulty.Preset rung : LADDER) {
            double rate = PracticeService.turnRatePerTick(BotDifficulty.of(rung));
            assertTrue(rate >= previous, rung + " turn rate");
            assertTrue(rate >= 3.0d, rung + " turn rate floor");
            assertTrue(rate <= 20.0d, rung + " turn rate ceiling");
            previous = rate;
        }
    }

    @Test
    void turnRateHasAFloorForTheWorstAimTheEditorAllows() {
        // A hand-tuned CUSTOM profile can push the aim error to the editor's 20 degree cap;
        // the bot must still be able to turn instead of freezing its head.
        BotDifficulty wild = BotDifficulty.of(BotDifficulty.Preset.CUSTOM);
        wild.setAimSpreadDegrees(20.0d);
        assertEquals(3.0d, PracticeService.turnRatePerTick(wild), 1e-9);
        // Values outside the editor's range cannot get through the setter, but the floor holds.
        BotDifficulty absurd = BotDifficulty.of(BotDifficulty.Preset.CUSTOM);
        absurd.setAimSpreadDegrees(500.0d); // clamped to 20 by the setter
        assertEquals(3.0d, PracticeService.turnRatePerTick(absurd), 1e-9);
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
