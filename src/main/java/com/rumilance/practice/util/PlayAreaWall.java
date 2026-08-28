package com.rumilance.practice.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Server-side wall: stop at the cuboid edge along the attempted move (barrier-like slide).
 * Never teleports toward arena center or spawn.
 */
public final class PlayAreaWall {

    private PlayAreaWall() {
    }

    /**
     * @return true when the event was rewritten
     */
    public static boolean constrain(PlayerMoveEvent event, Cuboid region, Player player) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || region == null || region.containsHorizontal(to)) {
            return false;
        }
        Location slid = region.slideHorizontal(from, to);
        slid.setYaw(to.getYaw());
        slid.setPitch(to.getPitch());
        event.setTo(slid);
        Vector velocity = player.getVelocity();
        boolean hitX = Math.abs(slid.getX() - to.getX()) > 1.0e-4d;
        boolean hitZ = Math.abs(slid.getZ() - to.getZ()) > 1.0e-4d;
        if (hitX) {
            velocity.setX(0.0d);
        }
        if (hitZ) {
            velocity.setZ(0.0d);
        }
        if (hitX || hitZ) {
            player.setVelocity(velocity);
        }
        return true;
    }
}
