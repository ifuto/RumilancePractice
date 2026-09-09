package com.rumilance.practice.practice;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Datapack parity for the drill kernel: every number asserted here is read straight out of
 * the Quantum {@code mech_train} mcfunction lines cited in {@link DrillKernel}, so the drills
 * stay identical to the map by construction — and the same kernel feeds PracticeService.
 */
class DrillKernelTest {

    // ----------------------------------------------------------- cycle sequencing

    @Test
    void armedFiresAttemptExactlyAtDeadline() {
        DrillKernel.Step s = DrillKernel.advance(DrillKernel.STAGE_ARMED, 10_000L, 0, 0, 9_999L);
        assertFalse(s.fireAttempt());
        assertEquals(DrillKernel.STAGE_ARMED, s.stage());

        s = DrillKernel.advance(DrillKernel.STAGE_ARMED, 10_000L, 0, 0, 10_000L);
        assertTrue(s.fireAttempt());
        assertEquals(DrillKernel.STAGE_ACTIVE, s.stage());
        assertEquals(10_000L + DrillKernel.ATTEMPT_WINDOW_MS, s.nextAtMs());
        // pops baseline is captured at fire time (reset_scores right before the loop tp)
        assertEquals(0, s.popsAtStart());
    }

    @Test
    void popDuringWindowGradesImmediatelyAsSuccess() {
        DrillKernel.Step s = DrillKernel.advance(
                DrillKernel.STAGE_ACTIVE, 20_000L, 0, 1, 15_000L);
        assertTrue(s.gradeNow());
        assertTrue(s.gradeSuccess());
        assertFalse(s.fireAttempt());
        assertEquals(DrillKernel.STAGE_ARMED, s.stage());
        assertEquals(15_000L + DrillKernel.INTERMISSION_MS, s.nextAtMs());
    }

    @Test
    void windowCloseWithoutPopGradesFailed() {
        DrillKernel.Step s = DrillKernel.advance(
                DrillKernel.STAGE_ACTIVE, 20_000L, 0, 0, 20_000L);
        assertTrue(s.gradeNow());
        assertFalse(s.gradeSuccess());
        assertEquals(DrillKernel.STAGE_ARMED, s.stage());
    }

    @Test
    void activeWaitsQuietlyInsideTheWindow() {
        DrillKernel.Step s = DrillKernel.advance(
                DrillKernel.STAGE_ACTIVE, 20_000L, 0, 0, 19_000L);
        assertFalse(s.gradeNow());
        assertFalse(s.fireAttempt());
        assertEquals(DrillKernel.STAGE_ACTIVE, s.stage());
    }

    @Test
    void loopRunsArmedAttemptGradeIdleArmed() {
        int stage = DrillKernel.STAGE_ARMED;
        long nextAt = 1_000L;
        int popsAt = 0;
        // t=999: idle
        DrillKernel.Step s = DrillKernel.advance(stage, nextAt, popsAt, 0, 999L);
        assertFalse(s.fireAttempt());
        // t=1000: attempt fires, window [1000, 1000+9000]
        s = DrillKernel.advance(s.stage(), s.nextAtMs(), s.popsAtStart(), 0, 1_000L);
        assertTrue(s.fireAttempt());
        stage = s.stage();
        nextAt = s.nextAtMs();
        popsAt = s.popsAtStart();
        // t=nextAt: no pop -> Failed at window edge
        s = DrillKernel.advance(stage, nextAt, popsAt, 0, 12_000L);
        assertTrue(s.gradeNow());
        assertFalse(s.gradeSuccess());
        // intermission: stay armed until nextAt fires again
        s = DrillKernel.advance(s.stage(), s.nextAtMs(), s.popsAtStart(), 0, 13_000L);
        assertFalse(s.fireAttempt());
        s = DrillKernel.advance(s.stage(), 14_200L, s.popsAtStart(), 0, 14_200L);
        assertTrue(s.fireAttempt());
    }

    // ----------------------------------------------------------- grade strings (exact)

    @Test
    void gradeTextsMatchModeLoopLines() {
        // mace/elytra/loop:1-2
        assertEquals("Slam!", DrillKernel.gradeText(PracticeMode.MACE_ELYTRA, true));
        assertEquals("Failed!", DrillKernel.gradeText(PracticeMode.MACE_ELYTRA, false));
        // mace/stun_slam/loop:1
        assertEquals("Stun Slam!", DrillKernel.gradeText(PracticeMode.MACE_STUN_SLAM, true));
        assertEquals("Failed!", DrillKernel.gradeText(PracticeMode.MACE_STUN_SLAM, false));
        // mace/far_pearl/loop:4 — the sad-face failed line
        assertEquals("\uD83E\uDD7A Failed!", DrillKernel.gradeText(PracticeMode.MACE_FAR_PEARL, false));
        assertEquals("Slam!", DrillKernel.gradeText(PracticeMode.MACE_FAR_PEARL, true));
        // mace/divebomb/loop:7
        assertEquals("Stun Slam Dive Bomb?!?!?!?", DrillKernel.gradeText(PracticeMode.MACE_DIVEBOMB, true));
        // crystal/dtap/loop:1,3
        assertEquals("ez Dtap", DrillKernel.gradeText(PracticeMode.CRYSTAL_DTAP, true));
        assertEquals("\uD83E\uDD7A Failed!", DrillKernel.gradeText(PracticeMode.CRYSTAL_DTAP, false));
        // crystal/hit_anchor/loop:1
        assertEquals("Digging isn't meta!", DrillKernel.gradeText(PracticeMode.CRYSTAL_HIT_ANCHOR, true));
        // crystal/ledge/loop:1
        assertEquals("Ledge Dash!", DrillKernel.gradeText(PracticeMode.CRYSTAL_LEDGE, true));
        assertEquals("\uD83E\uDD7A Failed!", DrillKernel.gradeText(PracticeMode.CRYSTAL_LEDGE, false));
    }

    // ----------------------------------------------------------- mace drill facts

    @Test
    void farPearlScatterMatchesSpreadplayers() {
        // far_pearl/loop:7 spreadplayers min=15 max=15 at ~ ~10 ~ -> uniform square 15.
        assertEquals(15, DrillKernel.FAR_PEARL_SCATTER_RADIUS);
        assertEquals(10, DrillKernel.FAR_PEARL_HEIGHT);
        Random rng = new Random(42L); // deterministic suite
        boolean seenMinusFifteen = false;
        boolean seenPlusFifteen = false;
        for (int i = 0; i < 50_000; i++) {
            int v = DrillKernel.farPearlScatter(rng, 0);
            assertTrue(v >= -15 && v <= 15, "scatter out of vanilla square: " + v);
            seenMinusFifteen |= v == -15;
            seenPlusFifteen |= v == 15;
        }
        assertTrue(seenMinusFifteen, "inclusive bound -15 must be reachable");
        assertTrue(seenPlusFifteen, "inclusive bound +15 must be reachable");
    }

    @Test
    void farPearlBotIsGlassCannon() {
        // far_pearl/init:9 attribute quantumbot max_health base set 2
        assertEquals(2.0d, DrillKernel.FAR_PEARL_BOT_MAX_HEALTH, 1e-9);
    }

    @Test
    void elytraAndDivebombOffsets() {
        // elytra/loop:4 tp player ~ ~30 ~10
        assertEquals(30, DrillKernel.ELYTRA_PLAYER_Y);
        assertEquals(10, DrillKernel.ELYTRA_PLAYER_Z);
        // divebomb/loop:tp player ~ ~30 ~15 facing bot
        assertEquals(30, DrillKernel.DIVEBOMB_PLAYER_Y);
        assertEquals(15, DrillKernel.DIVEBOMB_PLAYER_Z);
    }

    // ----------------------------------------------------------- crystal drill facts

    @Test
    void dtapWindowIsSevenTicksInsideThreeBlocks() {
        // crystal/dtap/tick:2 distance=..3 -> hitcd 7
        assertEquals(3.0d, DrillKernel.DTAP_HIT_RANGE, 1e-9);
        assertEquals(7, DrillKernel.DTAP_HITCD_TICKS);
        assertEquals(350L, DrillKernel.DTAP_HITCD_MS);
        assertEquals(10.0d, DrillKernel.DTAP_RESET_DISTANCE, 1e-9);
    }

    @Test
    void ledgeTwoPhaseSequencing() {
        // ARMED -> (deadline) reposition now, pearl in 750ms -> ACTIVE window -> grade.
        DrillKernel.LedgeStep s = DrillKernel.advanceLedge(DrillKernel.STAGE_ARMED, 2_000L, 0, 0, 2_000L);
        assertTrue(s.fireReposition());
        assertEquals(DrillKernel.STAGE_LEDGE_PEARL, s.stage());
        assertEquals(2_750L, s.nextAtMs());
        s = DrillKernel.advanceLedge(s.stage(), s.nextAtMs(), s.popsAtStart(), 0, 2_400L);
        assertFalse(s.firePearl());
        s = DrillKernel.advanceLedge(s.stage(), s.nextAtMs(), s.popsAtStart(), 0, 2_750L);
        assertTrue(s.firePearl());
        assertEquals(DrillKernel.STAGE_ACTIVE, s.stage());
        assertEquals(2_750L + DrillKernel.ATTEMPT_WINDOW_MS, s.nextAtMs());
        s = DrillKernel.advanceLedge(s.stage(), s.nextAtMs(), s.popsAtStart(), 1, 3_000L);
        assertTrue(s.gradeNow());
        assertTrue(s.gradeSuccess());
    }

    @Test
    void ledgeDashesFifteenTicksAfterReposition() {
        // crystal/ledge/loop: pearlcd2 15 -> pearl at +750ms; bot ~30 ~ ~; player ~ ~ ~14
        assertEquals(30, DrillKernel.LEDGE_BOT_SHIFT);
        assertEquals(14, DrillKernel.LEDGE_PLAYER_Z);
        assertEquals(750L, DrillKernel.LEDGE_PEARL_DELAY_MS);
    }

    // ----------------------------------------------------------- pot drills

    @Test
    void repotAndRefillNumbers() {
        assertEquals(0.65d, DrillKernel.REPOT_HEALTH_FRACTION, 1e-9);
        assertEquals(6.0d, DrillKernel.REPOT_HEAL, 1e-9);
        assertEquals(2500L, DrillKernel.REPOT_COOLDOWN_MS);
        assertEquals(4000L, DrillKernel.REFILL_COOLDOWN_MS);
        assertEquals(16, DrillKernel.REFILL_QUANTITY);
        assertEquals(1, DrillKernel.REFILL_HOTBAR_SLOT);
        assertEquals(12, DrillKernel.REFILL_INV_SLOT);
    }

    // ----------------------------------------------------------- cart power tiers

    @Test
    void cartTierFormulasCoverTheWholeLadder() {
        // M3 weakest, P3 strongest; fuse floor keeps P3 detonable.
        assertEquals(32, DrillKernel.cartFuseTicks(-3));
        assertEquals(26, DrillKernel.cartFuseTicks(0));
        assertEquals(20, DrillKernel.cartFuseTicks(3));
        assertEquals(2.2f, DrillKernel.cartYield(-3), 1e-4f);
        assertEquals(4.0f, DrillKernel.cartYield(0), 1e-4f);
        assertEquals(5.8f, DrillKernel.cartYield(3), 1e-4f);
        assertEquals(1360L, DrillKernel.cartCooldownMs(1_000L, -3));
        assertEquals(1_000L, DrillKernel.cartCooldownMs(1_000L, 0));
        assertEquals(700L, DrillKernel.cartCooldownMs(1_000L, 5)); // min cadence guard
        assertEquals(0.16d, DrillKernel.cartBowFactor(-3), 1e-9);
        assertEquals(0.7d, DrillKernel.cartBowFactor(0), 1e-9);
        assertEquals(1.24d, DrillKernel.cartBowFactor(3), 1e-9);
    }
}
