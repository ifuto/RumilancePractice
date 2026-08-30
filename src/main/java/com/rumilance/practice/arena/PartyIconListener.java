package com.rumilance.practice.arena;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-shot BlockPlace listener used after {@code /arena set party} to capture the map icon.
 */
public final class PartyIconListener implements Listener {

    private final Plugin plugin;
    private final ArenaTemplateStore arenaStore;
    private final ArenaService arenaService;
    private final Map<UUID, String> awaiting = new ConcurrentHashMap<>();

    public PartyIconListener(Plugin plugin, ArenaTemplateStore arenaStore, ArenaService arenaService) {
        this.plugin = plugin;
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
    }

    public void await(Player player, String arenaName) {
        awaiting.put(player.getUniqueId(), arenaName);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String arena = awaiting.remove(event.getPlayer().getUniqueId());
        if (arena == null) {
            return;
        }
        event.setCancelled(true);
        String material = event.getBlockPlaced().getType().name();
        arenaStore.setIconMaterial(arena, material);
        arenaService.setTemplates(arenaStore.templates());
        // Remove the placed block on next tick so the cancel leave-message / ghost block is clean.
        plugin.getServer().getScheduler().runTask(plugin, () ->
                event.getBlockPlaced().setType(org.bukkit.Material.AIR));
        event.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                "Party icon set: " + material + " (for " + arena + ")",
                net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }
}
