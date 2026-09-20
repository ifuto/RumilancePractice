package com.rumilance.practice.testarena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothTerrainGeneratorTest {

    @Test
    void generatedSurfaceStaysWithinFiveBlocks() {
        SmoothTerrainGenerator.HeightMap map =
                SmoothTerrainGenerator.HeightMap.create(24, 0x4e4152454e41L);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < 49; x++) {
            for (int z = 0; z < 49; z++) {
                int height = map.heightAt(x, z);
                min = Math.min(min, height);
                max = Math.max(max, height);
            }
        }
        assertEquals(0, min);
        assertEquals(5, max);
        assertTrue(max - min <= SmoothTerrainGenerator.MAX_HEIGHT_DELTA);
    }

    @Test
    void neighboringColumnsNeverJumpMoreThanOneBlock() {
        for (long seed = 0; seed < 32; seed++) {
            SmoothTerrainGenerator.HeightMap map =
                    SmoothTerrainGenerator.HeightMap.create(24, seed);
            for (int x = 0; x < 49; x++) {
                for (int z = 0; z < 49; z++) {
                    int here = map.heightAt(x, z);
                    if (x + 1 < 49) {
                        assertTrue(Math.abs(here - map.heightAt(x + 1, z)) <= 1,
                                "x jump at " + x + "," + z + " seed=" + seed);
                    }
                    if (z + 1 < 49) {
                        assertTrue(Math.abs(here - map.heightAt(x, z + 1)) <= 1,
                                "z jump at " + x + "," + z + " seed=" + seed);
                    }
                }
            }
        }
    }
}
