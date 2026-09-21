package com.rumilance.practice.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Decides where an ender pearl actually puts the player — <em>before</em> the teleport happens.
 *
 * <p><b>The one-block wall case.</b> A pearl dies just outside the block it hit, but the player
 * is 0.6 blocks wide and 1.8 tall: thrown at a one-block-thick glass wall the hitbox still
 * overlaps the glass, and vanilla drops the player inside it. The landing is therefore resolved
 * to the nearest free column, searched with a bias back towards the thrower
 * ({@link LandingSearch}), so the player lands on their own side of the wall with the Y and
 * momentum the pearl had — never inside it and never through it.</p>
 *
 * <p><b>Order of preference.</b>
 * <ol>
 *   <li>the pearl's own position, untouched, whenever the hitbox fits there (vanilla play,</li>
 *   <li>the same column snapped onto a surface at the same height,</li>
 *   <li>the nearest free column at the same height (the wall case),</li>
 *   <li>the same column lifted onto the surface above (ledge / pillar pearls),</li>
 *   <li>otherwise {@code null}: the teleport is cancelled rather than burying the player.</li>
 * </ol>
 */
public final class PearlLanding {

    /** How far the landing may be moved sideways before giving up (blocks). */
    private static final int SEARCH_RADIUS = 3;

    private PearlLanding() {
    }

    /**
     * Resolves a pearl destination inside {@code bounds}. Slides along the pearl path at the
     * landing Y, then puts the player down on a free spot (no WorldBorder clamp, limited upward
     * scan). Returns {@code null} when the pearl would pass through / land on the glass border.
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
        Location landed = resolve(dest, from, maxLiftBlocks);
        if (landed == null) {
            return null;
        }
        if (bounds != null && (!bounds.containsHorizontal(landed) || isBorderGlass(landed.getBlock()))) {
            return null;
        }
        return landed;
    }

    /**
     * The preventive decision: never return a point whose hitbox overlaps a block. Returns
     * {@code null} when no free spot is reachable nearby, so the caller can cancel instead of
     * burying the player.
     */
    private static Location resolve(Location dest, Location from, int maxLiftBlocks) {
        if (dest == null || dest.getWorld() == null) {
            return dest;
        }
        // 1) Vanilla: the pearl's own position is legal -> leave it completely untouched.
        if (SpawnFooting.fits(dest)) {
            return dest;
        }
        // 2) Same height, own column (a slab under the landing, the pearl one block into the
        //    floor, ...): the smallest possible correction.
        Location ownColumn = SpawnFooting.standClearPearl(dest, maxLiftBlocks);
        if (keepsHeight(dest, ownColumn)) {
            return ownColumn;
        }
        // 3) Same height, neighbouring column - the one-block wall case. Biased back towards
        //    the thrower, so the player lands on their own side of the wall.
        double[] bias = from == null || from.getWorld() == null
                ? new double[]{0.0d, 0.0d}
                : LandingSearch.towards(from.getX(), from.getZ(), dest.getX(), dest.getZ());
        Location nearby = SpawnFooting.standNearby(dest, SEARCH_RADIUS, bias[0], bias[1], false);
        if (keepsHeight(dest, nearby)) {
            return nearby;
        }
        // 4) A real lift is now allowed (ledge / pillar pearls): own column, then neighbours.
        if (ownColumn != null) {
            return ownColumn;
        }
        if (nearby != null) {
            return nearby;
        }
        Location floored = SpawnFooting.standNearby(dest, SEARCH_RADIUS, bias[0], bias[1], true);
        if (floored != null) {
            return floored;
        }
        // Nothing free anywhere near: the caller cancels the teleport instead of burying.
        return null;
    }

    /** True when the candidate sits at (about) the pearl's own height. */
    private static boolean keepsHeight(Location dest, Location candidate) {
        return candidate != null && Math.abs(candidate.getY() - dest.getY()) < 1.0d;
    }

    private static boolean isBorderGlass(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        return name.endsWith("GLASS") || name.endsWith("GLASS_PANE");
    }
}
