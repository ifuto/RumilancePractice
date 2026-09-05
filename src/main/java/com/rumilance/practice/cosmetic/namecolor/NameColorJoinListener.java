package com.rumilance.practice.cosmetic.namecolor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Applies a VIP+ player's saved name color shortly after join and drops the cache on quit. */
public final class NameColorJoinListener implements Listener {

    private final Plugin plugin;
    private final NameColorService nameColorService;

    public NameColorJoinListener(Plugin plugin, NameColorService nameColorService) {
        this.plugin = plugin;
        this.nameColorService = nameColorService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Slight delay: lets the rank cache warm up and avoids join-tick DB reads.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                nameColorService.applyToPlayer(player);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nameColorService.unload(event.getPlayer().getUniqueId());
    }
}
