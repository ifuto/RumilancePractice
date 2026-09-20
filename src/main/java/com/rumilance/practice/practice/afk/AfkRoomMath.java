package com.rumilance.practice.practice.afk;

/**
 * Pure geometry of one private AFK crystal room. Bukkit-free on purpose so JUnit can
 * exercise the exact numbers the listeners and the packet filter use (see
 * {@code AfkRoomMathTest}); every caller passes plain doubles/ints.
 *
 * <p>Two independent boxes are described here:</p>
 * <ul>
 *   <li><b>build box</b> — the floor footprint in X/Z plus a build-height cap in Y. The
 *       height cap is a <em>placement</em> rule only ("高度制限はブロックの設置高さだけ"):
 *       the player themselves is never restricted by it.</li>
 *   <li><b>visible footprint</b> — the same X/Z box, used by the ProtocolLib filter to drop
 *       every block/entity packet that belongs to somebody else's room. No Y limit is
 *       applied there: rooms only ever neighbour each other horizontally (132 blocks apart
 *       on the +60k line), so a horizontal test is the whole isolation rule.</li>
 * </ul>
 */
public final class AfkRoomMath {

    private AfkRoomMath() {
    }

    /** Smallest block X (inclusive) covered by the floor of a room centred on {@code centerX}. */
    public static double footprintMin(double center, int floorRadius) {
        return center - floorRadius - 0.5d;
    }

    /** Largest block X (inclusive) covered by the floor of a room centred on {@code centerX}. */
    public static double footprintMax(double center, int floorRadius) {
        return center + floorRadius - 0.5d;
    }

    /**
     * True when the block cell {@code (bx, bz)} sits on the room's floor. Half-open test on the
     * cell centre so a floor built as {@code center ± floorRadius} matches exactly: with a
     * 0.5-offset centre the covered cells are {@code center-50.5 .. center+49.5}.
     */
    public static boolean onFootprint(double centerX, double centerZ, int floorRadius, int bx, int bz) {
        double dx = (bx + 0.5d) - centerX;
        double dz = (bz + 0.5d) - centerZ;
        return dx >= -floorRadius && dx < floorRadius
                && dz >= -floorRadius && dz < floorRadius;
    }

    /** Y of the netherite floor row for a room whose centre is at {@code centerY}. */
    public static int floorBlockY(double centerY) {
        return (int) Math.floor(centerY);
    }

    /**
     * True when {@code blockY} is inside the build-height cap: the {@code buildHeight} cells
     * directly above the floor row. The floor row itself is protected, not buildable.
     */
    public static boolean withinBuildHeight(double centerY, int buildHeight, int blockY) {
        int floorY = floorBlockY(centerY);
        return blockY > floorY && blockY <= floorY + buildHeight;
    }

    /** Placement rule: on the floor footprint AND inside the build-height cap. */
    public static boolean canPlaceAt(double centerX, double centerZ, double centerY,
                                     int floorRadius, int buildHeight,
                                     int bx, int by, int bz) {
        return onFootprint(centerX, centerZ, floorRadius, bx, bz)
                && withinBuildHeight(centerY, buildHeight, by);
    }

    /**
     * Continuous (entity/packet) version of {@link #onFootprint}: both edges inclusive so an
     * entity standing on the room's outer border still counts as inside.
     */
    public static boolean positionInside(double centerX, double centerZ, int floorRadius,
                                         double x, double z) {
        return x >= footprintMin(centerX, floorRadius)
                && x <= footprintMax(centerX, floorRadius)
                && z >= footprintMin(centerZ, floorRadius)
                && z <= footprintMax(centerZ, floorRadius);
    }

    /**
     * True when the 16x16 chunk {@code (chunkX, chunkZ)} overlaps the room footprint, i.e. the
     * chunk may be sent to that room's owner. Chunks that only touch the border are allowed —
     * cancelling a partially-visible chunk would punch holes in the player's own floor.
     */
    public static boolean chunkVisible(double centerX, double centerZ, int floorRadius,
                                       int chunkX, int chunkZ) {
        double minX = chunkX * 16.0d;
        double minZ = chunkZ * 16.0d;
        return minX < footprintMax(centerX, floorRadius)
                && minX + 16.0d > footprintMin(centerX, floorRadius)
                && minZ < footprintMax(centerZ, floorRadius)
                && minZ + 16.0d > footprintMin(centerZ, floorRadius);
    }

    /**
     * Section-position helper for {@code section_blocks_update}: ProtocolLib hands the section
     * back either as block coordinates or as raw section coordinates depending on how the
     * server class stores it, so a section is treated as visible when either reading lands
     * inside the footprint. Guessing wrong in the "outside" direction only leaks a neighbour's
     * explosion; guessing wrong in the "inside" direction would desync the player's own room.
     */
    public static boolean sectionVisible(double centerX, double centerZ, int floorRadius,
                                         int sectionX, int sectionZ) {
        return positionInside(centerX, centerZ, floorRadius, sectionX, sectionZ)
                || positionInside(centerX, centerZ, floorRadius, sectionX * 16.0d, sectionZ * 16.0d);
    }
}
