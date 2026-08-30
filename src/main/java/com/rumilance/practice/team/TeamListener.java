package com.rumilance.practice.team;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Forwards quit events to {@link TeamService} so offline owners disband and offline members
 * are removed cleanly.
 */
public final class TeamListener implements Listener {

    private final TeamService teamService;

    public TeamListener(TeamService teamService) {
        this.teamService = teamService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        teamService.handleQuit(event.getPlayer().getUniqueId());
    }
}
