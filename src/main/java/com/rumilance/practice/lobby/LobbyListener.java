package com.rumilance.practice.lobby;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Lobby protections. Skipped for fighting / FFA / editing / preparing match players.
 */
public final class LobbyListener implements Listener {

    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;

    public LobbyListener(LobbyService lobbyService, PlayerStateManager stateManager) {
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
    }

    private boolean shouldProtect(Player player) {
        PlayerState state = stateManager.getState(player.getUniqueId());
        return switch (state) {
            case LOBBY, OPENING_GUI, QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, IDLE -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !shouldProtect(player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && shouldProtect(player)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (shouldProtect(event.getPlayer()) && !event.getPlayer().hasPermission("rumilance.lobby.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (shouldProtect(event.getPlayer()) && !event.getPlayer().hasPermission("rumilance.lobby.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (shouldProtect(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && shouldProtect(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldProtect(player) || player.hasPermission("rumilance.lobby.bypass")) {
            return;
        }
        Cuboid region = lobbyService.region();
        if (region == null) {
            return;
        }
        if (event.getTo().getY() < lobbyService.fallReturnY()) {
            lobbyService.sendToLobby(player);
            return;
        }
        if (!region.contains(event.getTo())) {
            event.setTo(LocationUtil.safeTeleportLocation(lobbyService.spawn(), player));
        }
    }
}
