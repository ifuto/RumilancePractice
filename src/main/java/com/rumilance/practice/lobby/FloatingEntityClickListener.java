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

    private volatile Map<Integer, java.util.function.Consumer<Player>> actions =
            new ConcurrentHashMap<>();

    /** Set the shared action map (from LobbyFloatingEntitiesService). */
    public void setClickActions(Map<Integer, java.util.function.Consumer<Player>> actions) {
        this.actions = actions != null ? actions : new ConcurrentHashMap<>();
    }

    /** Register an action for an interaction entity. */
    public void register(Interaction entity, java.util.function.Consumer<Player> action) {
        if (entity != null && action != null) {
            actions.put(entity.getEntityId(), action);
        }
    }

    public void clear() {
        actions.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }
        java.util.function.Consumer<Player> action = actions.get(interaction.getEntityId());
        if (action != null) {
            // Swallow the interaction so a right-click never also swings/places.
            event.setCancelled(true);
            action.accept(event.getPlayer());
        }
    }
}