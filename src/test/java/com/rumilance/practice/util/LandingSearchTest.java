package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The landing search is what keeps a pearl from burying the player in a one-block wall: the
 * first column it tries is the one the pearl came from, so the player lands on their own side of
 * the wall instead of inside it or through it.
 */
class LandingSearchTest {

    @Test
    void wallCaseLooksBackTowardsTheThrower() {
        // Pearl thrown east from (0, 0) to (10, 0) hits a one-block glass wall in between.
        double[] bias = LandingSearch.towards(0.0d, 0.0d, 10.0d, 0.0d);
        assertEquals(-10.0d, bias[0], 1.0e-9);
        assertEquals(0.0d, bias[1], 1.0e-9);
        int[] first = LandingSearch.nearbyOffsets(3, bias[0], bias[1]).get(0);
        assertEquals(-1, first[0], "the thrower's side is tried first");
        assertEquals(0, first[1]);
    }

    @Test
    void ringsGrowOutwardsSoTheClosestColumnWins() {
        List<int[]> offsets = LandingSearch.nearbyOffsets(3, -1.0d, 0.0d);
        assertEquals(48, offsets.size(), "8 + 16 + 24 columns for radius 3");
        int firstRingTwo = -1;
        for (int i = 0; i < offsets.size(); i++) {
            int[] offset = offsets.get(i);
            if (Math.max(Math.abs(offset[0]), Math.abs(offset[1])) == 2) {
                firstRingTwo = i;
                break;
            }
        }
        assertTrue(firstRingTwo >= 8, "all 8 ring-1 columns come before ring 2");
        Set<String> seen = new HashSet<>();
        for (int[] offset : offsets) {
            assertTrue(offset[0] != 0 || offset[1] != 0, "the landing itself is never returned");
            assertTrue(Math.max(Math.abs(offset[0]), Math.abs(offset[1])) <= 3);
            assertTrue(seen.add(offset[0] + ":" + offset[1]), "no duplicate column");
        }
    }

    @Test
    void diagonalBiasPrefersTheDiagonalColumn() {
        List<int[]> offsets = LandingSearch.nearbyOffsets(2, 1.0d, 1.0d);
        int[] first = offsets.get(0);
        assertEquals(1, first[0]);
        assertEquals(1, first[1]);
        // A pearl thrown north-west resolves towards the south-east column it came from.
        double[] bias = LandingSearch.towards(5.0d, 5.0d, 0.0d, 0.0d);
        int[] back = LandingSearch.nearbyOffsets(1, bias[0], bias[1]).get(0);
        assertEquals(1, back[0]);
        assertEquals(1, back[1]);
    }

    @Test
    void ownColumnComesFirstSoTheSmallestCorrectionWins() {
        List<int[]> columns = LandingSearch.columnCandidates(1, -1.0d, 0.0d);
        assertEquals(9, columns.size(), "own column + 8 neighbours");
        assertEquals(0, columns.get(0)[0], "the landing's own block is tried first");
        assertEquals(0, columns.get(0)[1]);
        assertEquals(-1, columns.get(1)[0], "then the thrower's side");
    }

    @Test
    void onlyASmallDownwardStepCountsAsAFloorSnap() {
        // Pearl died a quarter block inside the floor it came down on -> snap onto the surface.
        assertTrue(LandingSearch.floorSnap(65.0d, 64.75d));
        // A side hit one block below the wall's top must never be lifted onto the wall.
        assertFalse(LandingSearch.floorSnap(66.0d, 65.3d));
        assertFalse(LandingSearch.floorSnap(66.0d, 65.0d));
        // Dropping onto a surface just below the feet is fine.
        assertTrue(LandingSearch.floorSnap(64.0d, 64.5d));
        // ... but not falling a whole block or more.
        assertFalse(LandingSearch.floorSnap(64.0d, 65.5d));
    }

    @Test
    void verticalThrowsFallBackToADeterministicOrder() {
        double[] bias = LandingSearch.towards(3.0d, 4.0d, 3.0d, 4.0d);
        assertEquals(0.0d, bias[0], 1.0e-9);
        assertEquals(0.0d, bias[1], 1.0e-9);
        List<int[]> first = LandingSearch.nearbyOffsets(1, bias[0], bias[1]);
        List<int[]> second = LandingSearch.nearbyOffsets(1, bias[0], bias[1]);
        assertEquals(8, first.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i)[0], second.get(i)[0], "same order on every call");
            assertEquals(first.get(i)[1], second.get(i)[1], "same order on every call");
        }
        List<String> order = new ArrayList<>();
        for (int[] offset : first) {
            order.add(offset[0] + "," + offset[1]);
        }
        assertEquals(List.of("-1,0", "0,-1", "0,1", "1,0",
                "-1,-1", "-1,1", "1,-1", "1,1"), order,
                "nearest first, then geometric");
    }
}
