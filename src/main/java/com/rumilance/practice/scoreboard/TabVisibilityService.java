package com.rumilance.practice.scoreboard;

import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.UUID;

/**
 * Hides lobby players from fighters and spectators so TAB stays fight-focused.
 */
public final class TabVisibilityService {

    private final Plugin plugin;
    private final PlayerStateManager stateManager;
    private final MatchRegistry matchRegistry;
    private volatile SpectatorService spectatorService;

    public TabVisibilityService(
            Plugin plugin,
            PlayerStateManager stateManager,
            MatchRegistry matchRegistry
    ) {
        this.plugin = plugin;
        this.stateManager = stateManager;
        this.matchRegistry = matchRegistry;
    }

    public void setSpectatorService(SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    public void refresh(Collection<? extends Player> online) {
        for (Player viewer : online) {
            boolean isolate = isMatchOrSpectate(viewer.getUniqueId());
            for (Player other : online) {
                if (other.equals(viewer)) {
                    continue;
                }
                if (isolate && isLobbyVisible(other.getUniqueId())) {
                    if (viewer.canSee(other)) {
                        viewer.hidePlayer(plugin, other);
                    }
                } else if (!viewer.canSee(other)) {
                    viewer.showPlayer(plugin, other);
                }
            }
        }
    }

    public void showAll(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && !player.canSee(other)) {
                player.showPlayer(plugin, other);
            }
        }
    }

    private boolean isMatchOrSpectate(UUID playerId) {
        MatchSession session = matchRegistry.byPlayer(playerId).orElse(null);
        if (session != null) {
            return true;
        }
        if (spectatorService != null && spectatorService.matchOf(playerId).isPresent()) {
            return true;
        }
        return false;
    }

    private boolean isLobbyVisible(UUID playerId) {
        PlayerState state = stateManager.getState(playerId);
        return switch (state) {
            case LOBBY, OPENING_GUI, QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, IDLE,
                 EDITING_KIT -> true;
            default -> false;
        };
    }
}
