package com.rumilance.practice.util;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuboidClampTest {

    @Test
    void clampHorizontalKeepsInsideCoordinates() {
        double[] xz = Cuboid.clampHorizontal(10.5, -4.2, 0, 20, -10, 10);
        assertArrayEquals(new double[]{10.5, -4.2}, xz, 0.0001d);
    }

    @Test
    void containsHorizontalIgnoresY() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        assertTrue(region.containsHorizontal(5, 5));
        assertTrue(region.containsHorizontal(0, 10));
        assertFalse(region.containsHorizontal(-1, 5));
        assertFalse(region.containsHorizontal(5, 11));
    }

    @Test
    void clampHorizontalStopsAtInsetEdges() {
        double[] xz = Cuboid.clampHorizontal(-50, 80, 0, 10, 0, 10);
        assertEquals(0.3d, xz[0], 0.0001d);
        assertEquals(10.7d, xz[1], 0.0001d);
    }

    @Test
    void slideHorizontalStaysOnApproachAxis() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        Location from = new org.bukkit.Location(null, 5.5, 70.0, 5.5);
        Location to = new org.bukkit.Location(null, 12.0, 70.0, 5.5);
        Location slid = region.slideHorizontal(from, to);
        assertTrue(region.containsHorizontal(slid.getBlockX(), slid.getBlockZ()));
        assertEquals(5.5, slid.getZ(), 0.001d);
        assertTrue(slid.getX() > 5.5 && slid.getX() < 12.0);
    }

    @Test
    void includingExpandsToCoverAPointOutside() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        assertEquals(region, region.including(5, 70, 5));
        Cuboid grown = region.including(12, 65, -1);
        assertEquals(0, grown.minX());
        assertEquals(12, grown.maxX());
        assertEquals(-1, grown.minZ());
        assertEquals(10, grown.maxZ());
        assertEquals(60, grown.minY());
        assertEquals(80, grown.maxY());
    }
}
