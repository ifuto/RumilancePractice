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
    void turnRateFollowsTheMapRotationLadder() {
        // The map's slowcast.step.max_rotation_per_tick ladder (quantum:difficulty/1..6).
        assertEquals(8.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.NPC)), 1e-9);
        assertEquals(1.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.EASY)), 1e-9);
        assertEquals(4.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)), 1e-9);
        assertEquals(10.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.HARD)), 1e-9);
        assertEquals(14.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)), 1e-9);
        assertEquals(20.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)), 1e-9);
        assertEquals(20.0d, PracticeService.turnRatePerTick(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)), 1e-9);
        // The map's rotation ladder is monotonic across the FIGHTING rungs only: the NPC rung
        // never swings and keeps the neutral wander rate, so it sits outside the 1..20 ladder.
        double previous = -1.0d;
        for (BotDifficulty.Preset rung : LADDER) {
            double rate = PracticeService.turnRatePerTick(BotDifficulty.of(rung));
            if (rung == BotDifficulty.Preset.NPC) {
                assertEquals(8.0d, rate, 1e-9);
                continue;
            }
            assertTrue(rate >= previous, rung + " turn rate");
            assertTrue(rate >= 1.0d, rung + " turn rate floor");
            assertTrue(rate <= 20.0d, rung + " turn rate ceiling");
            previous = rate;
        }
    }

    @Test
    void crystalTimersFollowTheMapCrystalLadder() {
        // crystal_cd rungs in ticks (quantum:difficulty/1..6 = 6/6/6/3/2/3), 50ms per tick.
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(300L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(150L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(100L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(150L, PracticeService.crystalPlaceIntervalMs(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
        // anchor_cd rungs (5/4/4/3/1/1): place -> charge wait.
        assertEquals(250L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(200L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(200L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(150L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(50L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(50L, PracticeService.anchorPlaceCdMs(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
        // charge_cd / explosion_cd rungs (5/4/3/2/2/2): charge and re-cycle waits.
        assertEquals(250L, PracticeService.anchorChargeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(200L, PracticeService.anchorChargeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(150L, PracticeService.anchorChargeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(100L, PracticeService.anchorChargeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(100L, PracticeService.anchorExplodeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(2000L, PracticeService.anchorExplodeCdMs(
                BotDifficulty.of(BotDifficulty.Preset.CUSTOM)));
        // Hand-tuned CUSTOM keeps its own combo cadence (the map has no custom rung).
        assertEquals(BotDifficulty.of(BotDifficulty.Preset.CUSTOM).comboCooldownMs(),
                PracticeService.crystalPlaceIntervalMs(BotDifficulty.of(BotDifficulty.Preset.CUSTOM)));
        // totem_cd rungs (40/31/21/10/0/1 ticks): the pause after the bot's own pop.
        assertEquals(2000L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.EASY)));
        assertEquals(1550L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE)));
        assertEquals(1050L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.HARD)));
        assertEquals(500L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.CRAZY)));
        assertEquals(0L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.MASTER)));
        assertEquals(50L, PracticeService.crystalTotemPauseMs(
                BotDifficulty.of(BotDifficulty.Preset.SURVIVAL_MASTER)));
        // g1gc/hit: the crystal bot's melee cadence is a fixed 7 ticks on every rung.
        assertEquals(350L, PracticeService.CRYSTAL_MELEE_INTERVAL_MS);
        assertEquals(3.0d, PracticeService.CRYSTAL_MELEE_REACH, 1e-9);
    }

    @Test
    void customKeepsTheNeutralTurnRateRegardlessOfAim() {
        // Hand-tuned CUSTOM profiles keep the neutral 8 deg/tick turn rate — the map ties
        // rotation to the preset rung, not to the aim slider (the NPC wanders at 8 too).
        BotDifficulty wild = BotDifficulty.of(BotDifficulty.Preset.CUSTOM);
        wild.setAimSpreadDegrees(20.0d);
        assertEquals(8.0d, PracticeService.turnRatePerTick(wild), 1e-9);
        // Values outside the editor's range cannot get through the setter; either way the
        // turn rate does not move off the neutral value.
        BotDifficulty absurd = BotDifficulty.of(BotDifficulty.Preset.CUSTOM);
        absurd.setAimSpreadDegrees(500.0d); // clamped to 20 by the setter
        assertEquals(8.0d, PracticeService.turnRatePerTick(absurd), 1e-9);
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
