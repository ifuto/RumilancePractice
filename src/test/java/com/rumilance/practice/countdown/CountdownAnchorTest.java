package com.rumilance.practice.countdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Ready/Leave pair is derived from the teleport destination (spawn position + yaw), not from
 * the player's live position: five blocks ahead of the spawn, emerald one to the right, redstone
 * one to the left, both at eye height above the spawn's feet.
 *
 * <p>Minecraft yaw: 0 = south (+Z), 90 = west (−X), 180 = north (−Z), 270 = east (+X).</p>
 */
class CountdownAnchorTest {

    private static final double EPS = 1.0e-6;

    @Test
    void facingSouthPutsReadyToTheWest() {
        // Facing +Z (south), the player's right hand points west (-X).
        CountdownAnchor.Pair pair = CountdownAnchor.pair(0.0, 64.0, 0.0, 0f);
        assertEquals(-CountdownAnchor.SIDE, pair.readyX(), EPS);
        assertEquals(CountdownAnchor.SIDE, pair.leaveX(), EPS);
        assertEquals(CountdownAnchor.FORWARD, pair.readyZ(), EPS);
        assertEquals(CountdownAnchor.FORWARD, pair.leaveZ(), EPS);
        assertEquals(64.0 + CountdownAnchor.EYE, pair.readyY(), EPS);
        assertEquals(pair.readyY(), pair.leaveY(), EPS);
    }

    @Test
    void facingNorthMirrorsThePair() {
        CountdownAnchor.Pair pair = CountdownAnchor.pair(0.0, 64.0, 0.0, 180f);
        assertEquals(CountdownAnchor.SIDE, pair.readyX(), EPS, "right of north is east");
        assertEquals(-CountdownAnchor.SIDE, pair.leaveX(), EPS);
        assertEquals(-CountdownAnchor.FORWARD, pair.readyZ(), EPS);
    }

    @Test
    void diagonalSpawnsKeepThePairPerpendicularToTheSpawnYaw() {
        // Yaw 45 = south-west: forward is (-0.707, +0.707), so the pair's right is
        // north-west (-0.707, -0.707) — diagonal spawns are the reason the maths is yaw-based.
        CountdownAnchor.Pair pair = CountdownAnchor.pair(10.0, 70.0, -4.0, 45f);
        double root = Math.sqrt(0.5d);
        double centreX = 10.0 - CountdownAnchor.FORWARD * root;
        double centreZ = -4.0 + CountdownAnchor.FORWARD * root;
        assertEquals(centreX - CountdownAnchor.SIDE * root, pair.readyX(), EPS);
        assertEquals(centreZ - CountdownAnchor.SIDE * root, pair.readyZ(), EPS);
        assertEquals(centreX + CountdownAnchor.SIDE * root, pair.leaveX(), EPS);
        assertEquals(centreZ + CountdownAnchor.SIDE * root, pair.leaveZ(), EPS);
    }

    @Test
    void bothBlocksSitOnTheSameCircleAroundTheSpawn() {
        CountdownAnchor.Pair pair = CountdownAnchor.pair(100.5, 63.0, -250.5, 123.0f);
        double dx = pair.readyX() - 100.5;
        double dz = pair.readyZ() + 250.5;
        double expected = Math.hypot(CountdownAnchor.FORWARD, CountdownAnchor.SIDE);
        assertEquals(expected, Math.hypot(dx, dz), 1.0e-9);
        // The pair is mirrored around the forward axis, so the two are 4 blocks apart.
        assertEquals(2 * CountdownAnchor.SIDE,
                Math.hypot(pair.readyX() - pair.leaveX(), pair.readyZ() - pair.leaveZ()), EPS);
    }

    @Test
    void facingWestMovesThePairStraightAheadWithNoSidewaysShift() {
        // 90 = west (-X): both blocks share one X, split on Z (right of west is north, -Z).
        CountdownAnchor.Pair pair = CountdownAnchor.pair(0.0, 64.0, 0.0, 90f);
        assertEquals(-CountdownAnchor.FORWARD, pair.readyX(), EPS);
        assertEquals(-CountdownAnchor.FORWARD, pair.leaveX(), EPS);
        assertEquals(-CountdownAnchor.SIDE, pair.readyZ(), EPS, "right of west is north");
        assertEquals(CountdownAnchor.SIDE, pair.leaveZ(), EPS);
        assertTrue(Math.abs(pair.readyX() - pair.leaveX()) < EPS);
    }
}
