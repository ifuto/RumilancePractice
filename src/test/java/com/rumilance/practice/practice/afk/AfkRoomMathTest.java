package com.rumilance.practice.practice.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AFK crystal room's boxes, verified against a real room: centre {@code (60000.5, -52.0,
 * 60000.5)} (the +60k line, 12 above a -64 world floor), a 100x100 netherite floor and the
 * 30-block build cap. The height cap must be a <em>placement</em> rule only, and the packet
 * filter must hide the neighbouring room 132 blocks away while never hiding the player's own
 * floor chunks.
 */
class AfkRoomMathTest {

    private static final double CENTER_X = 60000.5d;
    private static final double CENTER_Z = 60000.5d;
    private static final double CENTER_Y = -52.0d;
    private static final int BASE_X = 60000;
    private static final int BASE_Z = 60000;
    private static final int RADIUS = 50;
    private static final int BUILD_HEIGHT = 30;
    private static final int FLOOR_Y = -52;

    // ---------------------------------------------------------- build height (placement only)

    @Test
    void floorRowIsNotBuildable() {
        assertFalse(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X, FLOOR_Y, BASE_Z), "the netherite floor row itself is protected");
    }

    @Test
    void buildHeightCapIsExactlyThirtyBlocksAboveTheFloor() {
        assertTrue(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X, FLOOR_Y + 1, BASE_Z), "first cell above the floor");
        assertTrue(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X, FLOOR_Y + 30, BASE_Z), "the 30th cell is still buildable");
        assertFalse(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X, FLOOR_Y + 31, BASE_Z), "cell 31 is over the cap");
    }

    @Test
    void heightCapNeverDependsOnHorizontalPosition() {
        // Corner of the footprint and the centre behave the same: only Y decides the cap.
        for (int dx : new int[]{-50, 0, 49}) {
            for (int dz : new int[]{-50, 0, 49}) {
                assertTrue(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                        BASE_X + dx, FLOOR_Y + 30, BASE_Z + dz), "cap ok at " + dx + "/" + dz);
                assertFalse(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                        BASE_X + dx, FLOOR_Y + 31, BASE_Z + dz), "cap blocks at " + dx + "/" + dz);
            }
        }
    }

    // ---------------------------------------------------------- footprint (placement bounds)

    @Test
    void footprintCoversExactlyTheBuiltFloor() {
        assertTrue(AfkRoomMath.onFootprint(CENTER_X, CENTER_Z, RADIUS, BASE_X - 50, BASE_Z - 50));
        assertTrue(AfkRoomMath.onFootprint(CENTER_X, CENTER_Z, RADIUS, BASE_X + 49, BASE_Z + 49));
        assertFalse(AfkRoomMath.onFootprint(CENTER_X, CENTER_Z, RADIUS, BASE_X + 50, BASE_Z),
                "one past the last floor block");
        assertFalse(AfkRoomMath.onFootprint(CENTER_X, CENTER_Z, RADIUS, BASE_X, BASE_Z - 51));
    }

    @Test
    void placementOutsideTheFootprintIsRejectedAtAnyHeight() {
        assertFalse(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X + 50, FLOOR_Y + 1, BASE_Z));
        assertFalse(AfkRoomMath.canPlaceAt(CENTER_X, CENTER_Z, CENTER_Y, RADIUS, BUILD_HEIGHT,
                BASE_X - 51, FLOOR_Y + 20, BASE_Z));
    }

    // ---------------------------------------------------------- packet filter (own area only)

    @Test
    void ownAreaPositionsStayVisible() {
        assertTrue(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS, CENTER_X, CENTER_Z));
        assertTrue(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS,
                AfkRoomMath.footprintMin(CENTER_X, RADIUS), AfkRoomMath.footprintMin(CENTER_Z, RADIUS)));
        assertTrue(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS,
                AfkRoomMath.footprintMax(CENTER_X, RADIUS), AfkRoomMath.footprintMax(CENTER_Z, RADIUS)));
        // The bot's home, 8 blocks north of the centre.
        assertTrue(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS, CENTER_X, CENTER_Z - 8.0d));
    }

    @Test
    void neighbouringRoomPositionsAreBlocked() {
        double nextRoomX = CENTER_X + 132.0d; // 132-block pitch on the +60k line
        assertFalse(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS, nextRoomX, CENTER_Z));
        assertFalse(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS, nextRoomX, CENTER_Z - 8.0d));
        assertFalse(AfkRoomMath.positionInside(CENTER_X, CENTER_Z, RADIUS, 0.0d, 0.0d),
                "the lobby far away is blocked too");
    }

    @Test
    void chunksOverlappingTheFloorAreKeptAndForeignChunksDropped() {
        int ownChunk = BASE_X >> 4;
        assertTrue(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, ownChunk, ownChunk));
        // Border chunk: covers 60048..60063, so 2 blocks of it belong to the floor.
        assertTrue(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, (BASE_X + 48) >> 4, ownChunk));
        // First chunk fully outside the footprint (60064..60079, past the 60050.0 edge).
        assertFalse(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, (BASE_X + 64) >> 4, ownChunk));
        // The neighbouring room's chunk.
        assertFalse(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, (BASE_X + 132) >> 4, ownChunk));
        // Same checks on the Z axis.
        assertTrue(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, ownChunk, (BASE_Z - 50) >> 4));
        assertFalse(AfkRoomMath.chunkVisible(CENTER_X, CENTER_Z, RADIUS, ownChunk, (BASE_Z + 132) >> 4));
    }

    @Test
    void sectionUpdatesAreVisibleUnderBothCoordinateReadings() {
        int ownSection = BASE_X >> 4;
        // ProtocolLib may hand back block coordinates or raw section coordinates.
        assertTrue(AfkRoomMath.sectionVisible(CENTER_X, CENTER_Z, RADIUS, BASE_X, BASE_Z),
                "block-coordinate reading");
        assertTrue(AfkRoomMath.sectionVisible(CENTER_X, CENTER_Z, RADIUS, ownSection, ownSection),
                "section-coordinate reading");
        assertFalse(AfkRoomMath.sectionVisible(CENTER_X, CENTER_Z, RADIUS, BASE_X + 132, BASE_Z + 132));
        assertFalse(AfkRoomMath.sectionVisible(CENTER_X, CENTER_Z, RADIUS,
                (BASE_X + 132) >> 4, (BASE_Z + 132) >> 4));
    }

    @Test
    void floorRowIsDerivedFromTheCentre() {
        assertEquals(-52, AfkRoomMath.floorBlockY(CENTER_Y));
        assertEquals(59950.0d, AfkRoomMath.footprintMin(CENTER_X, RADIUS), 1e-9);
        assertEquals(60050.0d, AfkRoomMath.footprintMax(CENTER_X, RADIUS), 1e-9);
    }
}
