package com.rumilance.practice.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Keeps ender-pearl landings from burying the player inside a wall / floor without flinging
 * them onto distant glass ceilings above arena borders.
 */
public final class PearlLanding {

    private PearlLanding() {
    }

    /**
     * Resolves a pearl destination inside {@code bounds}. Slides along the pearl path at the
     * landing Y, then applies a small footing fix (no WorldBorder clamp, limited upward scan).
     * Returns {@code null} when the pearl would pass through / land on the glass border.
     */
    public static Location safePearlLanding(Location from, Location to, Cuboid bounds, int maxLiftBlocks) {
        if (to == null || to.getWorld() == null) {
            return to;
        }
        Location dest = to.clone();
        if (bounds != null) {
            if (!bounds.containsHorizontal(to) || isBorderGlass(to.getBlock())) {
                // Collide with the play-area wall: keep the thrower inside, do not tunnel past glass.
                if (from != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
                    dest = bounds.slideHorizontal(from, to);
                } else {
                    dest = bounds.clampHorizontal(to);
                    dest.setY(to.getY());
                    dest.setYaw(to.getYaw());
                    dest.setPitch(to.getPitch());
                }
                if (!bounds.containsHorizontal(dest) || isBorderGlass(dest.getBlock())) {
                    return null;
                }
            }
        }
        Location footed = resolveFooting(dest, maxLiftBlocks);
        if (bounds != null && footed != null
                && (!bounds.containsHorizontal(footed) || isBorderGlass(footed.getBlock()))) {
            return null;
        }
        return footed;
    }

    private static boolean isBorderGlass(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        return name.endsWith("GLASS") || name.endsWith("GLASS_PANE");
    }

    /**
     * @deprecated use {@link #safePearlLanding(Location, Location, Cuboid, int)}
     */
    @Deprecated
    public static Location safeDestination(Location to) {
        return resolveFooting(to, 2);
    }

    private static Location resolveFooting(Location dest, int maxLiftBlocks) {
        if (!SpawnFooting.isBuried(dest)) {
            Location clear = SpawnFooting.standClearPearl(dest, maxLiftBlocks);
            return clear != null ? clear : dest;
        }
        Block block = dest.getBlock();
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN
        }) {
            Block neighbour = block.getRelative(face);
            if (neighbour.getType().isAir() || !neighbour.getType().isSolid()) {
                Location candidate = neighbour.getLocation().add(0.5d, 0.0d, 0.5d);
                candidate.setYaw(dest.getYaw());
                candidate.setPitch(dest.getPitch());
                Location stand = SpawnFooting.standClearPearl(candidate, maxLiftBlocks);
                if (stand != null && !SpawnFooting.isBuried(stand) && !isBorderGlass(stand.getBlock())) {
                    return stand;
                }
            }
        }
        Location lifted = SpawnFooting.standClearPearl(dest, maxLiftBlocks);
        return lifted != null ? lifted : dest;
    }
}
