package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationUtilWorldBorderTest {

    @Test
    void clampsOutsideCoordinatesTowardCenterWithMargin() {
        double[] clamped = LocationUtil.clampXZ(80, 0, 0, 0, 50, 2.0d, false);
        assertEquals(48.0d, clamped[0], 0.001d);
        assertEquals(0.0d, clamped[1], 0.001d);
    }

    @Test
    void pullsNearEdgeInwardEvenWhenMarkedInside() {
        double[] clamped = LocationUtil.clampXZ(49.5, 0, 0, 0, 50, 2.0d, true);
        assertEquals(48.5d, clamped[0], 0.001d);
        assertEquals(0.0d, clamped[1], 0.001d);
    }

    @Test
    void leavesComfortablyInsideCoordinatesUntouched() {
        double[] clamped = LocationUtil.clampXZ(10, -10, 0, 0, 50, 2.0d, true);
        assertArrayEquals(new double[]{10.0d, -10.0d}, clamped, 0.001d);
    }
}
