package com.rumilance.practice.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Server-side wall with BLOCK-identical collision at the cuboid's outer faces: the wall planes
 * sit exactly on the region's continuous bounds ({@code [minX, maxX+1) × [minZ, maxZ+1)}) — the
 * same planes a wall of blocks would occupy — and a move that crosses one is clamped to the
 * face with per-axis sliding, exactly like walking into a block:
 *
 * <ul>
 *   <li>the wall-normal velocity component is dropped, the tangent component survives (you
 *       slide along the wall, never stick),</li>
 *   <li>the clamp is analytic and double-precision — no binary-search and no block-granularity
 *       "contains" test, both of which let a move penetrate up to a block past the face before
 *       snapping back (the old pull-back / rubber-band feel),</li>
 *   <li>never teleports toward arena center or spawn.</li>
 * </ul>
 *
 * <p>This wall rarely fires on its own: the per-player client border (ViewControlService) now
 * sits on exactly the same planes, so the CLIENT blocks walking out natively — the same way it
 * blocks at a block face — and this is the exact backstop for the shorter side of rectangular
 * arenas (a vanilla border is square), modified clients, and knockback that lands outside.</p>
 */
public final class PlayAreaWall {

    /** Skin thickness: clamped positions sit this far inside the face (never re-cross it). */
    private static final double EPSILON = 1.0e-3;

    private PlayAreaWall() {
    }

    /**
     * @return true when the event was rewritten (the move crossed a wall plane)
     */
    public static boolean constrain(PlayerMoveEvent event, Cuboid region, Player player) {
        Location to = event.getTo();
        if (to == null || region == null) {
            return false;
        }
        double minX = region.minX() + EPSILON;
        double maxX = region.maxX() + 1.0d - EPSILON;
        double minZ = region.minZ() + EPSILON;
        double maxZ = region.maxZ() + 1.0d - EPSILON;
        double x = to.getX();
        double z = to.getZ();
        double clampedX = Math.max(minX, Math.min(maxX, x));
        double clampedZ = Math.max(minZ, Math.min(maxZ, z));
        if (clampedX == x && clampedZ == z) {
            return false;
        }
        Location slid = to.clone();
        slid.setX(clampedX);
        slid.setZ(clampedZ);
        event.setTo(slid);
        Vector velocity = player.getVelocity();
        boolean hit = false;
        if (clampedX != x) {
            velocity.setX(0.0d);
            hit = true;
        }
        if (clampedZ != z) {
            velocity.setZ(0.0d);
            hit = true;
        }
        if (hit) {
            player.setVelocity(velocity);
        }
        return true;
    }
}
