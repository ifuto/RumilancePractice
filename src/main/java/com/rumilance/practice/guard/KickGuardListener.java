package com.rumilance.practice.guard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Cancels the automatic server kicks this server never wants applied: Paper's "Timed out"
 * kick (cause {@code TIMEOUT}, fired when keepalive answers stop), the vanilla flying kicks
 * ("Flying is not enabled on this server" / vehicle variant, causes {@code FLYING_PLAYER} /
 * {@code FLYING_VEHICLE}) and the idle kick (cause {@code IDLING}). Which causes are
 * suppressed is pure logic in {@link PracticeGuards#shouldSuppressAutomaticKick} so JUnit can
 * lock the matrix; command/ban/plugin kicks keep their other causes and are untouched.
 *
 * The socket-level read-timeout (no inbound packets at all) closes the channel without firing
 * any kick event, so joins additionally route through {@link TimeoutChannelGuard} to strip
 * that handler.
 */
public final class KickGuardListener implements Listener {

    private final TimeoutChannelGuard timeoutChannelGuard;

    public KickGuardListener(TimeoutChannelGuard timeoutChannelGuard) {
        this.timeoutChannelGuard = timeoutChannelGuard;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent event) {
        if (PracticeGuards.shouldSuppressAutomaticKick(event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        timeoutChannelGuard.disableReadTimeout(event.getPlayer());
    }
}
