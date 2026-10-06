package com.rumilance.practice.match;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flight for the fighter who was just killed.
 *
 * <p>Without it the loser of a duel is left standing in the arena for the whole ENDING window —
 * or, when they died in the void, keeps falling, because post-match damage is cancelled and
 * nothing stops them. On a lethal hit they are therefore given fly permission and are put into
 * the air on the spot. This is presentation only: it grants no advantage, the match is already
 * decided, and the survivor never sees the victim anyway.</p>
 *
 * <p>The service remembers <b>whom it granted flight to</b>, so it only ever takes back what it
 * gave; a player who could already fly (creative/spectator) is never touched and never recorded.
 * The record is dropped again on join, on quit and on every revoke, so a stale entry can never
 * strip flight that something else handed out afterwards — the lobby reset
 * ({@code LobbyService#ensureHubReturn}), spectator mode and replay all manage their own.</p>
 */
public final class MatchFlightService implements Listener {

    private final Set<UUID> granted = ConcurrentHashMap.newKeySet();

    /**
     * Called on a lethal hit: allow flight and lift the player into the air.
     *
     * <p>Creative and spectator fly by definition and are owned by whoever put them in that mode,
     * so they are left alone entirely.</p>
     */
    public void grantOnDeath(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        granted.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    /** Takes back what {@link #grantOnDeath} gave. A no-op for everybody else. */
    public void revoke(Player player) {
        if (player == null || !granted.remove(player.getUniqueId())) {
            return;
        }
        clearFlight(player);
    }

    /** Same as {@link #revoke(Player)}, for players who may already be gone. */
    public void revoke(UUID playerId) {
        if (playerId == null || !granted.remove(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            clearFlight(player);
        }
    }

    /** Bulk {@link #revoke(UUID)} — used at match start and match end. */
    public void revokeAll(Collection<UUID> playerIds) {
        if (playerIds == null) {
            return;
        }
        for (UUID id : playerIds) {
            revoke(id);
        }
    }

    /** True when this service is currently the reason the player can fly. */
    public boolean isGranted(UUID playerId) {
        return playerId != null && granted.contains(playerId);
    }

    private static void clearFlight(Player player) {
        // Creative/spectator own their own flight — never take it away.
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /**
     * A rejoin starts from a clean slate: the lobby reset that follows already clears flight, so
     * holding on to the record would only let a later revoke touch a state we no longer own.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        granted.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        granted.remove(event.getPlayer().getUniqueId());
    }
}
