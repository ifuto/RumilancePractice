package com.rumilance.practice.party;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Forwards player quit events to {@link PartyService} so offline leaders disband their party
 * and offline members are removed cleanly.
 */
public final class PartyListener implements Listener {

    private final PartyService partyService;

    public PartyListener(PartyService partyService) {
        this.partyService = partyService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        partyService.handleQuit(event.getPlayer().getUniqueId());
    }
}
