package com.rumilance.practice.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Suppresses Paper's automatic keep-alive timeout and floating kicks for all online players.
 * This does not grant flight or interfere with bans, manual kicks, or other safety checks.
 * Actual connection loss (including Netty/client/proxy timeouts) cannot be cancelled here.
 */
public final class PaperKickProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        // Use Paper's typed cause, never the reason text: a plugin/admin kick may use the
        // same wording, and the automatic kick's message may be translated or customized.
        switch (event.getCause()) {
            case TIMEOUT, FLYING_PLAYER, FLYING_VEHICLE -> event.setCancelled(true);
            default -> { }
        }
    }
}
