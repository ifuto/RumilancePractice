package com.rumilance.practice.match;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.rumilance.practice.settings.SettingsService;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps packet-only team leather looks in sync when players track each other or change armor.
 */
public final class TeamColoredArmorListener implements Listener {

    private final TeamColoredArmorService service;
    private final SettingsService settingsService;

    public TeamColoredArmorListener(TeamColoredArmorService service, SettingsService settingsService) {
        this.service = service;
        this.settingsService = settingsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        Player viewer = event.getPlayer();
        if (!settingsService.get(viewer).teamColoredArmor()) {
            return;
        }
        service.refreshViewer(viewer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        service.scheduleRefreshTarget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        service.scheduleRefreshTarget(target);
        for (Player viewer : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (!settingsService.get(viewer).teamColoredArmor()) {
                continue;
            }
            service.refreshViewer(viewer);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearForPlayer(event.getPlayer());
    }
}
