package com.rumilance.practice.guard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Fixes the Paper/vanilla client-server inventory desync when a {@link BlockPlaceEvent} /
 * {@link BlockBreakEvent} is cancelled by our kit/arena rules (Paper #11012 / #9504 / #5451):
 * the client still thinks it spent the block (or placed it) and shows a ghost item / water
 * flow. Pushing an inventory refresh for the acting player after a cancelled interaction keeps
 * the hotbar correct. Runs on the main thread, one tick later, only for active participants
 * (FFA / match) so lobby building isn't touched.
 */
public final class BlockInteractionResyncListener implements Listener {

    private final Plugin plugin;
    private final Predicate<UUID> participant;

    public BlockInteractionResyncListener(Plugin plugin, Predicate<UUID> participant) {
        this.plugin = plugin;
        this.participant = participant;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        resync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        resync(event.getPlayer());
    }

    private void resync(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!participant.test(player.getUniqueId())) {
            return;
        }
        // Defer one tick so the interaction completes, then force the client inventory view
        // to match the server (the held block never left the inventory server-side).
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }
}
