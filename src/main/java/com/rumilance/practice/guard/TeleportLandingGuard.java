package com.rumilance.practice.guard;

import com.rumilance.practice.util.SafeTeleport;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Arms the anti-bury landing watch for <em>every</em> teleport, including ones this plugin does
 * not start (GSit seats, staff {@code /tp}, map/arena tools, other plugins' warps).
 *
 * <p>The watch only acts on a genuine burial — solid material inside both halves of the
 * player's hitbox — so a teleport that merely lands the player next to a wall is untouched.</p>
 */
public final class TeleportLandingGuard implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer() == null || event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        SafeTeleport.watchLanding(event.getPlayer());
    }
}
