package com.rumilance.practice.security.sign;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

/**
 * Cancels malicious sign edits before they are applied. Runs at {@link EventPriority#HIGH} so
 * other plugins can still veto first, and ignores already-cancelled events.
 */
public final class SignChangeGuardListener implements Listener {

    private final SignGuardService signGuardService;

    public SignChangeGuardListener(SignGuardService signGuardService) {
        this.signGuardService = signGuardService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        boolean cancel = signGuardService.inspect(event.getPlayer(), event.lines(),
                event.getBlock().getLocation());
        if (cancel) {
            event.setCancelled(true);
        }
    }
}
