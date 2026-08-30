package com.rumilance.practice.ffa;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfaSpawnMathTest {

    private final RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(1L);

    @Test
    void pickIndexReturnsMinusOneWhenEmpty() {
        assertEquals(-1, FfaSpawnMath.pickIndex(0, new int[0], new int[0], new int[0], new int[0], 64, rng));
    }

    @Test
    void prefersCandidateFarFromOccupants() {
        int[] cx = {0, 20};
        int[] cz = {0, 0};
        int[] ox = {0};
        int[] oz = {0};
        int pick = FfaSpawnMath.pickIndex(2, cx, cz, ox, oz, 8 * 8, rng);
        assertEquals(1, pick);
    }

    @Test
    void fallsBackToFarthestWhenAllAreClose() {
        int[] cx = {1, 3};
        int[] cz = {0, 0};
        int[] ox = {0};
        int[] oz = {0};
        int pick = FfaSpawnMath.pickIndex(2, cx, cz, ox, oz, 100, rng);
        assertEquals(1, pick);
    }

    @Test
    void findGrassFeetYStandsOnGrassUnderAir() {
        String[] column = new String[8];
        java.util.Arrays.fill(column, "AIR");
        column[3] = "GRASS_BLOCK";
        int feet = FfaSpawnMath.findGrassFeetY(0, 7, y -> y >= 0 && y < column.length ? column[y] : "AIR");
        assertEquals(4, feet);
    }

    @Test
    void findGrassFeetYSkipsLavaFeet() {
        String[] column = new String[8];
        java.util.Arrays.fill(column, "AIR");
        column[3] = "GRASS_BLOCK";
        column[4] = "LAVA";
        int feet = FfaSpawnMath.findGrassFeetY(0, 7, y -> y >= 0 && y < column.length ? column[y] : "AIR");
        assertEquals(Integer.MIN_VALUE, feet);
    }

    @Test
    void grassFeetArePassableAndFireIsUnsafe() {
        assertTrue(FfaSpawnMath.isPassableSpawnFeet("SHORT_GRASS"));
        assertTrue(FfaSpawnMath.isPassableSpawnFeet("AIR"));
        assertFalse(FfaSpawnMath.isPassableSpawnFeet("STONE"));
        assertTrue(FfaSpawnMath.isUnsafeFeet("LAVA"));
        assertFalse(FfaSpawnMath.isUnsafeFeet("AIR"));
    }

    @Test
    void chunkKeyKeepsNegativeZ() {
        long key = FfaSpawnIndex.chunkKey(-3, -5);
        assertEquals(-3, (int) (key >> 32));
        assertEquals(-5, (int) key);
    }
}
