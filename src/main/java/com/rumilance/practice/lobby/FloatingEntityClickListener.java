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
    /** Per-kit floating queue items carry a plain Runnable (they know their own target). */
    private volatile Map<Integer, Runnable> floatActions = new ConcurrentHashMap<>();

    /** Set the shared action map (from LobbyFloatingEntitiesService). */
    public void setClickActions(Map<Integer, java.util.function.Consumer<Player>> actions) {
        this.actions = actions != null ? actions : new ConcurrentHashMap<>();
    }

    /** Set the shared action map (from FloatingQueueService). */
    public void setFloatActions(Map<Integer, Runnable> floatActions) {
        this.floatActions = floatActions != null ? floatActions : new ConcurrentHashMap<>();
    }

    public void clear() {
        actions.clear();
        floatActions.clear();
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) {
            return;
        }
        int id = interaction.getEntityId();
        java.util.function.Consumer<Player> action = actions.get(id);
        if (action == null) {
            Runnable plain = floatActions.get(id);
            if (plain != null) {
                // Swallow the interaction so a right-click never also swings/places.
                event.setCancelled(true);
                plain.run();
            }
            return;
        }
        event.setCancelled(true);
        action.accept(event.getPlayer());
    }
}