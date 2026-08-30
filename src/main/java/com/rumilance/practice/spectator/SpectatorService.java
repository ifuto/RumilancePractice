package com.rumilance.practice.spectator;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spectate ongoing matches when both fighters allow it.
 */
public final class SpectatorService {

    private final Plugin plugin;
    private final MatchRegistry matchRegistry;
    private final PlayerStateManager stateManager;
    private final SettingsService settingsService;
    private final LobbyService lobbyService;
    private final PluginSettings settings;
    private final Map<UUID, UUID> spectatorToMatch = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> matchSpectators = new ConcurrentHashMap<>();
    /** Spectators currently inside an FFA arena: spectator -> FFA arena id. */
    private final Map<UUID, String> spectatorToFfa = new ConcurrentHashMap<>();
    /** Optional per-player border/view-distance control (null = feature off). */
    private volatile com.rumilance.practice.sight.ViewControlService viewControl;
    private volatile com.rumilance.practice.ffa.FfaService ffaService;
    private volatile com.rumilance.practice.match.TeamColoredArmorService teamColoredArmorService;

    public void setViewControl(com.rumilance.practice.sight.ViewControlService viewControl) {
        this.viewControl = viewControl;
    }

    public void setFfaService(com.rumilance.practice.ffa.FfaService ffaService) {
        this.ffaService = ffaService;
    }

    public com.rumilance.practice.ffa.FfaService ffaService() {
        return ffaService;
    }

    public void setTeamColoredArmorService(
            com.rumilance.practice.match.TeamColoredArmorService teamColoredArmorService) {
        this.teamColoredArmorService = teamColoredArmorService;
    }

    /** @return the match this player is currently spectating, if any. */
    public java.util.Optional<UUID> spectatedMatch(UUID spectator) {
        return java.util.Optional.ofNullable(spectatorToMatch.get(spectator));
    }

    public SpectatorService(
            Plugin plugin,
            MatchRegistry matchRegistry,
            PlayerStateManager stateManager,
            SettingsService settingsService,
            LobbyService lobbyService,
            PluginSettings settings
    ) {
        this.plugin = plugin;
        this.matchRegistry = matchRegistry;
        this.stateManager = stateManager;
        this.settingsService = settingsService;
        this.lobbyService = lobbyService;
        this.settings = settings;
    }

    public boolean trySpectate(Player spectator, Player target) {
        PlayerState state = stateManager.getState(spectator.getUniqueId());
        if (state != PlayerState.LOBBY && state != PlayerState.OPENING_GUI) {
            spectator.sendMessage(Component.text("You cannot spectate right now.", NamedTextColor.RED));
            return false;
        }
        Optional<MatchSession> matchOpt = matchRegistry.byPlayer(target.getUniqueId());
        boolean inMatch = matchOpt.isPresent() && matchOpt.get().state() == MatchState.ACTIVE;

        if (inMatch) {
            return spectateMatch(spectator, target, matchOpt.get());
        }
        // Not a duel/team match: spectate an FFA fighter instead (FFA players are tracked
        // by FfaService, not the MatchRegistry).
        if (ffaService != null && ffaService.isInFfa(target.getUniqueId())) {
            return spectateFfa(spectator, target);
        }
        spectator.sendMessage(Component.text("That player is not in an active fight.", NamedTextColor.RED));
        return false;
    }

    private boolean spectateMatch(Player spectator, Player target, MatchSession match) {
        for (UUID participant : match.participants()) {
            if (!settingsService.get(participant).spectateVisible()) {
                spectator.sendMessage(Component.text("Spectating is disabled by a participant.", NamedTextColor.RED));
                return false;
            }
        }
        try {
            stateManager.transition(spectator.getUniqueId(), PlayerState.SPECTATING);
        } catch (Exception e) {
            spectator.sendMessage(Component.text("Cannot enter spectate state.", NamedTextColor.RED));
            return false;
        }
        spectatorToFfa.remove(spectator.getUniqueId());
        spectatorToMatch.put(spectator.getUniqueId(), match.id());
        matchSpectators.computeIfAbsent(match.id(), id -> ConcurrentHashMap.newKeySet()).add(spectator.getUniqueId());
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(settings.spectatorAllowFlight());
        moveSpectatorTo(spectator, target.getLocation());
        // Confine the spectator's view to this arena (per-player border + view distance);
        // SpectatorBoundsListener is the hard server-side backstop against flying out.
        if (viewControl != null) {
            viewControl.applyForMatch(spectator, match);
        }
        hideInWorld(spectator);
        spectator.sendMessage(Component.text("Spectating " + target.getName(), NamedTextColor.AQUA));
        return true;
    }

    private boolean spectateFfa(Player spectator, Player target) {
        String arenaId = ffaService.arenaOf(target.getUniqueId()).orElse(null);
        com.rumilance.practice.ffa.FfaService.FfaArena arena =
                arenaId == null ? null : ffaService.get(arenaId).orElse(null);
        if (arena == null || arena.region() == null) {
            spectator.sendMessage(Component.text("That player is not in an active fight.", NamedTextColor.RED));
            return false;
        }
        try {
            stateManager.transition(spectator.getUniqueId(), PlayerState.SPECTATING);
        } catch (Exception e) {
            spectator.sendMessage(Component.text("Cannot enter spectate state.", NamedTextColor.RED));
            return false;
        }
        spectatorToMatch.remove(spectator.getUniqueId());
        spectatorToFfa.put(spectator.getUniqueId(), arenaId);
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(settings.spectatorAllowFlight());
        moveSpectatorTo(spectator, target.getLocation());
        // Confine the spectator's camera to the FFA arena region.
        if (viewControl != null) {
            viewControl.applyRegion(spectator, arena.region());
        }
        hideInWorld(spectator);
        spectator.sendMessage(Component.text("Spectating FFA: " + target.getName(), NamedTextColor.AQUA));
        return true;
    }

    /**
     * Moves a spectator to the target spot via SafeTeleport (chunk-load + anti-bury) instead
     * of a raw {@code teleport}, so spectating never drops the camera into blocks or a
     * stale-border corner.
     */
    private void moveSpectatorTo(Player spectator, org.bukkit.Location at) {
        com.rumilance.practice.util.SafeTeleport
                .teleport(spectator, LocationUtil.safeTeleportLocation(at));
    }

    public void leave(Player spectator) {
        leave(spectator, true);
    }

    /**
     * @param returnToLobby when false (used from PlayerQuitEvent) only drop the bookkeeping and
     *                      visibility state; teleporting / re-applying a lobby inventory to a player
     *                      who is disconnecting is wasted work and schedules needless chunk loads.
     */
    public void leave(Player spectator, boolean returnToLobby) {
        UUID matchId = spectatorToMatch.remove(spectator.getUniqueId());
        spectatorToFfa.remove(spectator.getUniqueId());
        if (matchId != null) {
            Set<UUID> set = matchSpectators.get(matchId);
            if (set != null) {
                set.remove(spectator.getUniqueId());
            }
        }
        stateManager.resetToLobby(spectator.getUniqueId());
        if (!returnToLobby || !spectator.isOnline()) {
            return;
        }
        revealInWorld(spectator);
        spectator.setAllowFlight(false);
        spectator.setFlying(false);
        // sendToLobby already routes the return through SafeTeleport + post-teleport sight.
        lobbyService.sendToLobby(spectator);
    }

    /** World-only hide so TAB still lists spectators / dead fighters. */
    public void hideInWorld(Player hidden) {
        if (hidden == null) {
            return;
        }
        // Spectators are ALWAYS fully invisible to everyone, including other spectators:
        // a spectator must never see another camera flying around.
        boolean hiddenIsSpectator = hidden.getGameMode() == GameMode.SPECTATOR
                || isSpectating(hidden.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(hidden)) {
                continue;
            }
            if (hiddenIsSpectator) {
                online.hideEntity(plugin, hidden);
                hidden.hideEntity(plugin, online);
                continue;
            }
            if (settings.spectatorHideFromPlayers()) {
                online.hideEntity(plugin, hidden);
            }
            if (online.getGameMode() == GameMode.SPECTATOR) {
                hidden.hideEntity(plugin, online);
            }
        }
    }

    public void revealInWorld(Player player) {
        if (player == null) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            online.showEntity(plugin, player);
            player.showEntity(plugin, online);
        }
    }

    public void clearMatch(UUID matchId) {
        Set<UUID> specs = matchSpectators.remove(matchId);
        if (specs == null) {
            return;
        }
        for (UUID id : specs) {
            spectatorToMatch.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                revealInWorld(player);
                stateManager.resetToLobby(id);
                lobbyService.sendToLobby(player);
            }
        }
    }

    public boolean isSpectating(UUID uuid) {
        return spectatorToMatch.containsKey(uuid) || spectatorToFfa.containsKey(uuid);
    }

    public Optional<UUID> matchOf(UUID spectator) {
        return Optional.ofNullable(spectatorToMatch.get(spectator));
    }

    /** @return the FFA arena id this spectator is watching, if any. */
    public Optional<String> ffaArenaOf(UUID spectator) {
        return Optional.ofNullable(spectatorToFfa.get(spectator));
    }

    /** Clears all spectators watching an FFA arena (called when the arena resets). */
    public void clearFfaArena(String arenaId) {
        if (arenaId == null) {
            return;
        }
        spectatorToFfa.entrySet().removeIf(e -> {
            if (!arenaId.equals(e.getValue())) {
                return false;
            }
            Player player = Bukkit.getPlayer(e.getKey());
            if (player != null) {
                revealInWorld(player);
                player.setAllowFlight(false);
                player.setFlying(false);
                stateManager.resetToLobby(e.getKey());
                lobbyService.sendToLobby(player);
            }
            return true;
        });
    }

    /** Snapshot of spectators currently watching {@code matchId}. */
    public Set<UUID> spectatorsWatching(UUID matchId) {
        Set<UUID> set = matchSpectators.get(matchId);
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
