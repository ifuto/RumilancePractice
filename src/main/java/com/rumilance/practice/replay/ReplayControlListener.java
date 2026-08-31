package com.rumilance.practice.replay;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Drives the replay viewer transport buttons: the operator holds dye/barrier items in creative
 * and right-clicks one to restart / rewind / pause / fast-forward / change speed / stop.
 * Runs at LOWEST and cancels the interaction so control items never place a block or trigger
 * vanilla behaviour.
 */
public final class ReplayControlListener implements Listener {

    private final ReplayService replayService;

    public ReplayControlListener(ReplayService replayService) {
        this.replayService = replayService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        if (!replayService.isReplaying(event.getPlayer().getUniqueId())) {
            return;
        }
        if (replayService.handleControlClick(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }
}
