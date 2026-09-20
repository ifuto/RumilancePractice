package com.rumilance.practice.testarena;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothTerrainGeneratorTest {

    @Test
    void generatedSurfaceStaysWithinFiveBlocksAcrossThe100By100Map() {
        SmoothTerrainGenerator.HeightMap map =
                SmoothTerrainGenerator.HeightMap.create(SmoothTerrainGenerator.WIDTH, 0x4e4152454e41L);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = 0; x < SmoothTerrainGenerator.WIDTH; x++) {
            for (int z = 0; z < SmoothTerrainGenerator.WIDTH; z++) {
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
                    SmoothTerrainGenerator.HeightMap.create(SmoothTerrainGenerator.WIDTH, seed);
            for (int x = 0; x < SmoothTerrainGenerator.WIDTH; x++) {
                for (int z = 0; z < SmoothTerrainGenerator.WIDTH; z++) {
                    int here = map.heightAt(x, z);
                    if (x + 1 < SmoothTerrainGenerator.WIDTH) {
                        assertTrue(Math.abs(here - map.heightAt(x + 1, z)) <= 1,
                                "x jump at " + x + "," + z + " seed=" + seed);
                    }
                    if (z + 1 < SmoothTerrainGenerator.WIDTH) {
                        assertTrue(Math.abs(here - map.heightAt(x, z + 1)) <= 1,
                                "z jump at " + x + "," + z + " seed=" + seed);
                    }
                }
            }
        }
    }

    @Test
    void materialLayersMatchTheTwoMapDefinitions() {
        SmoothTerrainGenerator.TerrainMap grass = SmoothTerrainGenerator.TerrainMap.GRASS_STONE;
        assertEquals(Material.GRASS_BLOCK, grass.materialAtLayer(1));
        assertEquals(Material.DIRT, grass.materialAtLayer(2));
        assertEquals(Material.DIRT, grass.materialAtLayer(3));
        assertEquals(Material.STONE, grass.materialAtLayer(4));
        assertEquals(Material.STONE, grass.materialAtLayer(50));

        SmoothTerrainGenerator.TerrainMap sand = SmoothTerrainGenerator.TerrainMap.SAND_SANDSTONE;
        assertEquals(Material.SAND, sand.materialAtLayer(1));
        assertEquals(Material.SAND, sand.materialAtLayer(4));
        assertEquals(Material.SANDSTONE, sand.materialAtLayer(5));
        assertEquals(Material.SANDSTONE, sand.materialAtLayer(50));
    }
}
