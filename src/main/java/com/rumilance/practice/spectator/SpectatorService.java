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
        if (matchOpt.isEmpty() || matchOpt.get().state() != MatchState.ACTIVE) {
            spectator.sendMessage(Component.text("That player is not in an active match.", NamedTextColor.RED));
            return false;
        }
        MatchSession match = matchOpt.get();
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
        spectatorToMatch.put(spectator.getUniqueId(), match.id());
        matchSpectators.computeIfAbsent(match.id(), id -> ConcurrentHashMap.newKeySet()).add(spectator.getUniqueId());
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(settings.spectatorAllowFlight());
        spectator.teleport(LocationUtil.safeTeleportLocation(target.getLocation(), spectator));
        // Confine the spectator's view to this arena (per-player border + view distance);
        // SpectatorBoundsListener is the hard server-side backstop against flying out.
        if (viewControl != null) {
            viewControl.applyForMatch(spectator, match);
        }
        if (settings.spectatorHideFromPlayers()) {
            hideInWorld(spectator);
        }
        spectator.sendMessage(Component.text("Spectating " + target.getName(), NamedTextColor.AQUA));
        return true;
    }

    public void leave(Player spectator) {
        UUID matchId = spectatorToMatch.remove(spectator.getUniqueId());
        if (matchId != null) {
            Set<UUID> set = matchSpectators.get(matchId);
            if (set != null) {
                set.remove(spectator.getUniqueId());
            }
        }
        revealInWorld(spectator);
        stateManager.resetToLobby(spectator.getUniqueId());
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
        return spectatorToMatch.containsKey(uuid);
    }

    public Optional<UUID> matchOf(UUID spectator) {
        return Optional.ofNullable(spectatorToMatch.get(spectator));
    }

    /** FFA-arena spectate is not wired yet; reserved for scoreboard layouts. */
    public Optional<String> ffaArenaOf(UUID spectator) {
        return Optional.empty();
    }

    /** Snapshot of spectators currently watching {@code matchId}. */
    public Set<UUID> spectatorsWatching(UUID matchId) {
        Set<UUID> set = matchSpectators.get(matchId);
        return set == null ? Set.of() : Set.copyOf(set);
    }
}
