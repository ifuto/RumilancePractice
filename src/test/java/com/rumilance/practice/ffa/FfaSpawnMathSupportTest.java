package com.rumilance.practice.ffa;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "floating spawn on the border ring" guard: a real floor carries neighbourhood
 * support, while the top of a 1-wide border wall sheet is air-adjacent. Pins the pure
 * sampling rule used by {@code FfaSpawnLocator.scanColumn} / {@code FfaSpawnIndex}.
 */
class FfaSpawnMathSupportTest {

    private static String[] fill(int n, String name) {
        String[] out = new String[n];
        Arrays.fill(out, name);
        return out;
    }

    @Test
    void flatFloweringMeadowPasses() {
        // plain ground level: snow above soil counts, soil below counts -> 16/16.
        assertTrue(FfaSpawnMath.hasFloorSupport(fill(8, "SNOW"), fill(8, "GRASS_BLOCK")));
        assertTrue(FfaSpawnMath.hasFloorSupport(fill(8, "AIR"), fill(8, "GRASS_BLOCK")));
    }

    @Test
    void borderWallSheetTopIsRejected() {
        // 1-wide bedrock shell (arena border): at the top level everything beside the
        // column is air; one block deeper only the along-wall neighbours are solid (2).
        String[] ground = fill(8, "AIR");
        String[] below = fill(8, "AIR");
        below[0] = "BEDROCK";
        below[4] = "BEDROCK";
        assertFalse(FfaSpawnMath.hasFloorSupport(ground, below));
    }

    @Test
    void tallPillarTopIsRejected() {
        // single-column lookout: both rings air except the column itself (not sampled).
        assertFalse(FfaSpawnMath.hasFloorSupport(fill(8, "AIR"), fill(8, "AIR")));
    }

    @Test
    void tinyIslandRimIsRejected() {
        // 3x3 floating islet corner: only 3 solid cells one level down, air at ground.
        String[] below = fill(8, "AIR");
        below[1] = "STONE";
        below[3] = "STONE";
        below[4] = "STONE";
        assertFalse(FfaSpawnMath.hasFloorSupport(fill(8, "AIR"), below));
    }

    @Test
    void blanketSnowFieldStillPasses() {
        // SNOW layers are "support" too — otherwise snowy arenas would lose all spawns.
        assertTrue(FfaSpawnMath.hasFloorSupport(fill(8, "SNOW"), fill(8, "AIR")));
    }

    @Test
    void unknownSamplesCountAsSupported() {
        // chunk-edge passthrough: null (outside the snapshot) must not flush valid spots.
        String[] ground = new String[8]; // all null = unknown
        String[] below = { "AIR", "AIR", "BEDROCK", "AIR", "AIR", "AIR", "AIR", "AIR" };
        assertTrue(FfaSpawnMath.hasFloorSupport(ground, below));
    }
}
