package com.rumilance.practice.lobby;

import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles right-click on Interaction entities spawned by LobbyFloatingEntitiesService.
 * Delegates to registered callbacks by entity ID.
 */
public final class FloatingEntityClickListener implements Listener {

    public interface ClickAction {
        void onClick(Player player);
    }

    private volatile Map<Integer, ClickAction> actions = new ConcurrentHashMap<>();

    /** Set the shared action map (from LobbyFloatingEntitiesService). */
    public void setClickActions(Map<Integer, ClickAction> actions) {
        this.actions = actions != null ? actions : new ConcurrentHashMap<>();
    }

    /** Register an action for an interaction entity. */
    public void register(Interaction entity, ClickAction action) {
        if (entity != null && action != null) {
            actions.put(entity.getEntityId(), action);
        }
    }

    public void clear() {
        actions.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        ClickAction action = actions.get(interaction.getEntityId());
        if (action != null) {
            action.onClick(event.getPlayer());
        }
    }
}