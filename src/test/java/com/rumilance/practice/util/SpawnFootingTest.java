package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpawnFootingTest {

    @Test
    void standingOnFloorUsesTheBlockBelow() {
        String[] column = column(80, "AIR");
        column[64] = "STONE";
        assertEquals(64, ground(65, column));
    }

    @Test
    void buriedInFloorPopsOntoThatFloor() {
        String[] column = column(80, "AIR");
        column[64] = "STONE";
        assertEquals(64, ground(64, column));
    }

    @Test
    void doesNotPickACeilingWhenTheFloorIsValid() {
        String[] column = column(80, "AIR");
        column[64] = "STONE";
        column[70] = "STONE";
        assertEquals(64, ground(65, column));
    }

    @Test
    void buriedUnderSeveralSolidsSearchesUp() {
        String[] column = column(80, "AIR");
        column[60] = "STONE";
        column[61] = "STONE";
        column[62] = "STONE";
        column[63] = "STONE";
        assertEquals(63, ground(60, column));
    }

    @Test
    void thickFloorStillPopsOntoTheSurface() {
        String[] column = column(80, "AIR");
        for (int y = 60; y <= 67; y++) {
            column[y] = "STONE";
        }
        assertEquals(67, ground(60, column));
    }

    @Test
    void pinnedMasDoesNotDropToACaveFarBelow() {
        String[] column = column(80, "AIR");
        column[40] = "STONE";
        column[64] = "STONE";
        assertEquals(64, ground(65, column));
    }

    @Test
    void airWithOnlyACaveFarBelowIsNotUsed() {
        String[] column = column(80, "AIR");
        column[40] = "STONE";
        assertEquals(Integer.MIN_VALUE, ground(65, column));
    }

    private static int ground(int startY, String[] column) {
        return SpawnFooting.findGroundY(
                startY,
                0,
                column.length - 3,
                y -> y >= 0 && y < column.length && "STONE".equals(column[y]),
                y -> y >= 0 && y < column.length && "AIR".equals(column[y])
        );
    }

    private static String[] column(int height, String fill) {
        String[] column = new String[height];
        java.util.Arrays.fill(column, fill);
        return column;
    }
}
