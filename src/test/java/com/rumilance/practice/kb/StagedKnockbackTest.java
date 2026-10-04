package com.rumilance.practice.kb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The staged-knockback model (kb-probe 0.7.0) must reproduce vanilla 1.21.x knockback
 * exactly with the vanilla constants, and the event-delta decomposition must recover the
 * push direction + strength the probe fitted.
 */
class StagedKnockbackTest {

    private static final StagedKnockback V = StagedKnockback.VANILLA;

    @Test
    void vanillaStageBaseMatchesTakeKnockback() {
        // current (0.3, 0.1, 0.2), push dir (0.6, 0.8), no resistance, grounded:
        // vanilla: new = current/2 + push*0.4, y = min(0.4, currentY/2 + 0.4)
        double[] out = V.stageBase(0.3d, 0.1d, 0.2d, 0.6d, 0.8d, 1.0d, true);
        assertEquals(0.15d + 0.24d, out[0], 1.0E-9d);
        assertEquals(Math.min(0.4d, 0.05d + 0.4d), out[1], 1.0E-9d);
        assertEquals(0.1d + 0.32d, out[2], 1.0E-9d);
    }

    @Test
    void vanillaStageExtraMatchesSecondKnockbackCall() {
        double[] one = V.stageBase(0.3d, 0.1d, 0.2d, 0.6d, 0.8d, 1.0d, true);
        // sprint hit (k=1): vanilla second call re-halves and adds push*0.5 (reapply=true)
        double[] out = V.stageExtra(one[0], one[1], one[2], 0.6d, 0.8d, 1.0d, true, 0.5d, 0.5d);
        assertEquals(one[0] * 0.5d + 0.3d, out[0], 1.0E-9d);
        assertEquals(Math.min(0.4d, one[1] * 0.5d + 0.5d), out[1], 1.0E-9d);
        assertEquals(one[2] * 0.5d + 0.4d, out[2], 1.0E-9d);
    }

    @Test
    void reapplyFalseAddsFrictionlessExtra() {
        StagedKnockback noReapply = new StagedKnockback(0.4d, 0.4d, 0.4d, 0.8d, 0.3d,
                0.5d, 0.5d, 1.0d, 0.0d, 0.5d, false);
        double[] out = noReapply.stageExtra(0.2d, 0.3d, 0.4d, 1.0d, 0.0d, 1.0d, true, 0.8d, 0.3d);
        assertEquals(0.2d + 0.8d, out[0], 1.0E-9d);
        assertEquals(0.3d + 0.3d, out[1], 1.0E-9d); // grounded: + extraV, no cap, no re-halving
        assertEquals(0.4d, out[2], 1.0E-9d);
    }

    @Test
    void airborneUsesAirMultipliers() {
        StagedKnockback air = new StagedKnockback(0.4d, 0.4d, 0.4d, 0.5d, 0.5d,
                0.5d, 0.5d, 2.0d, 0.5d, 0.5d, true);
        double[] out = air.stageBase(1.0d, -0.2d, 0.0d, 0.0d, 1.0d, 1.0d, false);
        assertEquals(1.0d * 0.5d, out[0], 1.0E-9d); // dirX=0 → only friction remains
        assertEquals(-0.2d + 0.4d * 0.5d, out[1], 1.0E-9d); // airVerticalMultiplier
        assertEquals(0.0d * 0.5d + 1.0d * 0.4d * 2.0d, out[2], 1.0E-9d); // airH scales the kick
    }

    @Test
    void decompositionRecoversPushDirectionAndStrength() {
        // vanilla stage on current (0.3, 0.2) with push dir (0.6, 0.8), s=0.4:
        // delta = (0.15 - 0.24, 0.1 - 0.32) - (0.3, 0.2) = (-0.39, -0.42)
        double[] stage = StagedKnockback.decomposeStage(0.3d, 0.2d, -0.39d, -0.42d);
        assertNotNull(stage);
        assertEquals(0.6d, stage[0], 1.0E-9d);
        assertEquals(0.8d, stage[1], 1.0E-9d);
        assertEquals(0.4d, stage[2], 1.0E-9d);
        assertNull(StagedKnockback.decomposeStage(0.0d, 0.0d, 0.0d, 0.0d));
    }

    @Test
    void vanillaKRecoveredFromStrength() {
        assertEquals(1.0d, StagedKnockback.vanillaKFromStrength(0.5d, 1.0d), 1.0E-9d);
        assertEquals(2.0d, StagedKnockback.vanillaKFromStrength(0.4d, 0.4d), 1.0E-9d);
        assertEquals(0.0d, StagedKnockback.vanillaKFromStrength(0.5d, 0.0d), 1.0E-9d);
    }

    @Test
    void probeBoundsAreClamped() {
        StagedKnockback clamped = new StagedKnockback(-1.0d, 9.0d, 0.4d, 0.5d, 0.5d,
                1.7d, 0.5d, 1.0d, 0.0d, Double.NaN, true);
        assertEquals(0.0d, clamped.horizontal(), 1.0E-9d);
        assertEquals(StagedKnockback.MAG_MAX, clamped.vertical(), 1.0E-9d);
        assertEquals(StagedKnockback.FRICTION_MAX, clamped.frictionHorizontal(), 1.0E-9d);
        assertEquals(0.0d, clamped.knockbackEnchant(), 1.0E-9d); // NaN → low
    }
}
