package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatPhysicsTest {

    @Test
    void compensatedPingSubtractsOffsetAndClampsFloor() {
        assertEquals(1.0d, CombatPhysics.compensatedPingMs(20, 20), 0.001d);
        assertEquals(75.0d, CombatPhysics.compensatedPingMs(100, 100), 0.001d);
    }

    @Test
    void spikeUsesPreviousPingInsteadOfRewardingLagSwitch() {
        // 40 → 120 is a 80ms spike (> 20). Must keep 40-25 = 15, not 120-25.
        assertEquals(15.0d, CombatPhysics.compensatedPingMs(120, 40), 0.001d);
        assertTrue(CombatPhysics.isSpike(120, 40));
        assertFalse(CombatPhysics.isSpike(45, 40));
    }

    @Test
    void compensationNeverExceedsAbuseCap() {
        assertEquals(CombatPhysics.MAX_COMPENSATION_MS,
                CombatPhysics.compensatedPingMs(2000, 2000), 0.001d);
    }

    @Test
    void ticksFromMsRoundsUpAndCaps() {
        assertEquals(0, CombatPhysics.ticksFromMs(0));
        assertEquals(1, CombatPhysics.ticksFromMs(25));
        assertEquals(2, CombatPhysics.ticksFromMs(51));
        assertEquals(CombatPhysics.MAX_SIM_TICKS, CombatPhysics.ticksFromMs(10_000));
    }

    @Test
    void gravityDecaysTowardTerminal() {
        double afterOne = CombatPhysics.applyGravity(0.4d, 1);
        assertEquals((0.4d - 0.08d) * 0.98d, afterOne, 0.0001d);
        double afterMany = CombatPhysics.applyGravity(0.4d, 20);
        assertTrue(afterMany < afterOne);
        assertTrue(afterMany < 0.0d);
    }

    @Test
    void offGroundStripsOnGroundBoost() {
        assertEquals(0.05d, CombatPhysics.toOffGroundVertical(0.4d, 0.05d), 0.0001d);
        assertEquals(0.12d, CombatPhysics.toOffGroundVertical(0.12d, 0.05d), 0.0001d);
    }

    @Test
    void idleGapDetectsFrozenClient() {
        assertTrue(CombatPhysics.isIdleGap(400_000_000L, 50, 20));
        assertFalse(CombatPhysics.isIdleGap(20_000_000L, 50, 20));
    }

    @Test
    void idleGapIgnoresSlowServerTicks() {
        assertFalse(CombatPhysics.isIdleGap(400_000_000L, 50, 20, 80_000_000L));
        assertTrue(CombatPhysics.isIdleGap(400_000_000L, 50, 20, 50_000_000L));
    }

    @Test
    void historyIndexRewindsWithoutGoingNegative() {
        assertEquals(7, CombatPhysics.historyIndex(0, 8, 16));
        assertEquals(15, CombatPhysics.historyIndex(0, 0, 16));
        assertEquals(0, CombatPhysics.historyIndex(1, 0, 16));
        assertEquals(14, CombatPhysics.historyIndex(0, 1, 16));
    }

    @Test
    void clientCritRequiresFallingNotSprinting() {
        assertTrue(CombatPhysics.isClientCritical(
                false, 0.4f, false, false, false, false, false, 1.0f));
        assertFalse(CombatPhysics.isClientCritical(
                false, 0.4f, true, false, false, false, false, 1.0f));
        assertFalse(CombatPhysics.isClientCritical(
                true, 0.0f, false, false, false, false, false, 1.0f));
        assertFalse(CombatPhysics.isClientCritical(
                false, 0.4f, false, false, false, false, false, 0.2f));
    }

    @Test
    void burstDisplacementCapGrowsWithPingButStaysFinite() {
        double low = CombatPhysics.maxHorizontalDisplacement(25, false);
        double burst = CombatPhysics.maxHorizontalDisplacement(200, true);
        assertTrue(burst > low);
        assertTrue(burst < 8.0d);
    }

    @Test
    void horizontalCompensationAppliesAirDragPerTick() {
        assertEquals(0.5d, CombatPhysics.compensatedHorizontal(0.5d, 0), 0.0001d);
        assertEquals(0.5d * 0.91d, CombatPhysics.compensatedHorizontal(0.5d, 1), 0.0001d);
        double two = CombatPhysics.compensatedHorizontal(1.0d, 2);
        assertEquals(0.91d * 0.91d, two, 0.0001d);
        assertTrue(CombatPhysics.compensatedHorizontal(1.0d, 5) < two);
    }
}
