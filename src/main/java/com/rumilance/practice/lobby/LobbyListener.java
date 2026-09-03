package com.rumilance.practice.lobby;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
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

/**
 * Lobby protections. Skipped for fighting / FFA / editing / preparing match players.
 */
public final class LobbyListener implements Listener {

    private final LobbyService lobbyService;
    private final PlayerStateManager stateManager;
    private final GuiSessionRegistry guiSessions;
    private final FfaService ffaService;

    public LobbyListener(LobbyService lobbyService, PlayerStateManager stateManager,
                         GuiSessionRegistry guiSessions, FfaService ffaService) {
        this.lobbyService = lobbyService;
        this.stateManager = stateManager;
        this.guiSessions = guiSessions;
        this.ffaService = ffaService;
    }

    private boolean shouldProtect(Player player) {
        if (ffaService != null && ffaService.isInFfa(player.getUniqueId())) {
            return false;
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.FFA) {
            return false;
        }
        // Match lifecycle states own their damage rules (MatchListener cancels pre/post-fight
        // hits and allows ACTIVE combat). They must NEVER fall under lobby protection: a GUI
        // session that is open — or lingers — during a fight would otherwise make the player
        // invulnerable ("cannot hit the enemy in Party Fight").
        switch (state) {
            case PREPARING_MATCH, COUNTDOWN, FIGHTING, ENDING -> {
                return false;
            }
            default -> {
            }
        }
        if (guiSessions.get(player.getUniqueId()).isPresent()) {
            return true;
        }
        return switch (state) {
            case LOBBY, OPENING_GUI, QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, IDLE,
                 EDITING_KIT -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !shouldProtect(player)) {
            return;
        }
        // Full invulnerability in lobby / queue / kit-edit / GUI — including Match Found punch-through.
        event.setCancelled(true);
        event.setDamage(0);
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

}
