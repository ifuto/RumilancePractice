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
 * <p><b>Order of preference — nothing ever climbs the block that was hit.</b>
 * <ol>
 *   <li>the pearl's own position, untouched, whenever the hitbox fits there — a pearl that
 *       really does land on top of a wall stays on top of it;</li>
 *   <li><b>stop in front at the pearl's own height</b>: own block centre first, then the nearest
 *       free column, always biased back towards the thrower, so a wall throw lands on the
 *       player's own side with the Y and momentum the pearl had;</li>
 *   <li>only when nothing is free at that height (the pearl came down into a floor or slab):
 *       put the player on top of that surface, and at most half a block up — a side hit can
 *       never be lifted onto the block it touched;</li>
 *   <li>a one-block step in the same column family, same half-block rule;</li>
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
     * The preventive decision: never return a point whose hitbox overlaps a block, and never
     * climb the block that was hit. Returns {@code null} when no free spot is reachable nearby,
     * so the caller can cancel instead of burying the player.
     */
    private static Location resolve(Location dest, Location from, int maxLiftBlocks) {
        if (dest == null || dest.getWorld() == null) {
            return dest;
        }
        // 1) Vanilla: the pearl's own position is legal -> leave it completely untouched. A
        //    pearl that genuinely lands on a ledge / on top of a wall stays there.
        if (SpawnFooting.fits(dest)) {
            return dest;
        }
        double[] bias = from == null || from.getWorld() == null
                ? new double[]{0.0d, 0.0d}
                : LandingSearch.towards(from.getX(), from.getZ(), dest.getX(), dest.getZ());
        // 2) Stop in front of what was hit, at the pearl's own height: own column first, then
        //    the nearest free column, always biased back towards the thrower. This is the
        //    one-block wall answer — the player lands on their side of the wall, never on top
        //    of it and never through it.
        Location front = SpawnFooting.standNearby(dest, SEARCH_RADIUS, bias[0], bias[1], false, -1);
        if (front != null) {
            return front;
        }
        // 3) Nothing free at that height: the pearl died inside the surface it came down onto
        //    (a floor, a slab) — put the player on top of it, but only for a small step so a
        //    side hit can never climb the block it touched.
        Location sameColumn = SpawnFooting.standClearPearl(dest, 1);
        if (sameColumn != null && SpawnFooting.fits(sameColumn)
                && LandingSearch.floorSnap(sameColumn.getY(), dest.getY())) {
            return sameColumn;
        }
        // 4) Last resort before cancelling: a one-block step in the same column family.
        Location stepped = SpawnFooting.standNearby(dest, SEARCH_RADIUS, bias[0], bias[1], false,
                Math.max(1, Math.min(maxLiftBlocks, 2)));
        if (stepped != null && LandingSearch.floorSnap(stepped.getY(), dest.getY())) {
            return stepped;
        }
        // Nothing free anywhere near: the caller cancels the teleport instead of burying.
        return null;
    }

    private static boolean isBorderGlass(Block block) {
        if (block == null) {
            return false;
        }
        String name = block.getType().name();
        return name.endsWith("GLASS") || name.endsWith("GLASS_PANE");
    }
}
