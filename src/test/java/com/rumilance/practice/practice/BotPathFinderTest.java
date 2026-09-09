package com.rumilance.practice.practice;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic-grids tests for the A* steering engine (no Bukkit needed). */
class BotPathFinderTest {

    private static BotPathFinder.Passable flat(Set<String> solidExtra) {
        return (x, y, z) -> {
            // floor everywhere at y<0; extra solid blocks listed as "x,z,y"
            if (y <= 0) {
                return false; // inside the floor
            }
            if (solidExtra.contains(x + "," + z + "," + (y - 1))) {
                return false; // floating headroom over obstacle column isn't standable
            }
            // standable when: floor solid (y-1<0 default OR listed) and feet/head free
            boolean floorSolid = (y - 1) <= 0 || solidExtra.contains(x + "," + z + "," + (y - 1));
            boolean feetFree = !solidExtra.contains(x + "," + z + "," + y);
            boolean headFree = !solidExtra.contains(x + "," + z + "," + (y + 1));
            return floorSolid && feetFree && headFree;
        };
    }

    @Test
    void straightLineOnFlat() {
        Set<String> solid = new HashSet<>();
        List<BotPathFinder.Node> path = BotPathFinder.find(flat(solid),
                new BotPathFinder.Node(0, 1, 0), new BotPathFinder.Node(4, 1, 0), 500);
        assertFalse(path.isEmpty());
        BotPathFinder.Node last = path.get(path.size() - 1);
        assertEquals(4, last.x());
        assertEquals(1, last.y());
    }

    @Test
    void jumpsOneBlockWall() {
        Set<String> solid = new HashSet<>();
        // 1-block high wall at x=2 spanning z=-5..5 — long enough that hopping beats detouring
        for (int z = -5; z <= 5; z++) {
            solid.add("2," + z + ",1");
        }
        List<BotPathFinder.Node> path = BotPathFinder.find(flat(solid),
                new BotPathFinder.Node(0, 1, 0), new BotPathFinder.Node(4, 1, 0), 600);
        assertFalse(path.isEmpty());
        assertTrue(path.stream().anyMatch(n -> n.y() == 2), "path should hop onto the wall");
        BotPathFinder.Node last = path.get(path.size() - 1);
        assertEquals(4, last.x());
    }

    @Test
    void detoursAroundFenceWithGap() {
        Set<String> solid = new HashSet<>();
        // wall at x=2 spanning z=-2..2 except a gap at z=2
        for (int z = -2; z <= 1; z++) {
            solid.add("2," + z + ",1");
            solid.add("2," + z + ",2");
        }
        List<BotPathFinder.Node> path = BotPathFinder.find(flat(solid),
                new BotPathFinder.Node(0, 1, 0), new BotPathFinder.Node(4, 1, 0), 900);
        assertFalse(path.isEmpty());
        assertTrue(path.stream().allMatch(n -> n.y() == 1),
                "2-high wall is unclimbable: path must stay on the floor");
        BotPathFinder.Node last = path.get(path.size() - 1);
        assertEquals(4, last.x());
    }

    @Test
    void noPathWhenSealedRoom() {
        Set<String> solid = new HashSet<>();
        // 3-high box around the goal at (6,1,0)
        for (int x = 4; x <= 8; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 1; y <= 4; y++) {
                    if (x == 4 || x == 8 || z == -2 || z == 2 || y == 4) {
                        solid.add(x + "," + z + "," + y);
                    }
                }
            }
        }
        List<BotPathFinder.Node> path = BotPathFinder.find(flat(solid),
                new BotPathFinder.Node(0, 1, 0), new BotPathFinder.Node(6, 1, 0), 1200);
        assertTrue(path.isEmpty(), "sealed room means no path within budget");
    }
}
