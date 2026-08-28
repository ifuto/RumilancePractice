package com.rumilance.practice.util;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsNudgeTest {

    @Test
    void nudgeFromEastPushesOneBlockWest() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        Location outside = new Location(null, 14.0, 70.0, 5.5);
        Location nudged = BoundsNudge.nudgeInward(region, outside);
        assertTrue(region.containsHorizontal(nudged.getBlockX(), nudged.getBlockZ()));
        assertEquals(5.5, nudged.getZ(), 0.001d);
        assertEquals(70.0, nudged.getY(), 0.001d);
        // Edge is maxX+0.7=10.7; one block inward ≈ 9.7
        assertEquals(9.7d, nudged.getX(), 0.05d);
    }

    @Test
    void nudgeFromWestPushesOneBlockEast() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        Location outside = new Location(null, -3.0, 70.0, 5.5);
        Location nudged = BoundsNudge.nudgeInward(region, outside);
        assertTrue(region.containsHorizontal(nudged.getBlockX(), nudged.getBlockZ()));
        assertEquals(1.3d, nudged.getX(), 0.05d);
    }

    @Test
    void alreadyInsideIsUnchanged() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 10, 80, 10);
        Location inside = new Location(null, 5.5, 70.0, 5.5, 90f, 0f);
        Location nudged = BoundsNudge.nudgeInward(region, inside);
        assertEquals(5.5, nudged.getX(), 0.001d);
        assertEquals(5.5, nudged.getZ(), 0.001d);
        assertEquals(90f, nudged.getYaw(), 0.001f);
    }

    @Test
    void neverSnapsToCenter() {
        Cuboid region = Cuboid.of("world", 0, 60, 0, 40, 80, 40);
        Location outside = new Location(null, 50.0, 70.0, 20.0);
        Location nudged = BoundsNudge.nudgeInward(region, outside);
        double centerX = (region.minX() + region.maxX() + 1) / 2.0;
        assertTrue(Math.abs(nudged.getX() - centerX) > 5.0d);
        assertTrue(region.containsHorizontal(nudged.getBlockX(), nudged.getBlockZ()));
    }
}
