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
    /**
     * Rematch hand-off: spectators of a match that just went into a rematch, keyed by any of
     * the (carried-over) participant UUIDs. Consumed by {@link #attachCarried(MatchSession)}
     * the moment the new match's countdown begins; a timeout bails them to the lobby if the
     * new match never starts (e.g. a fighter went offline during the rematch window).
     */
    private final Map<UUID, Set<UUID>> pendingCarry = new ConcurrentHashMap<>();
    private static final long CARRY_TIMEOUT_TICKS = 30L * 20L;
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
        // Spectatable from "match found" (arena reservation) through the live fight, so the
        // spectator camera can already move to the arena during the countdown.
        boolean inMatch = matchOpt.isPresent() && isSpectatable(matchOpt.get().state());

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
        spectator.sendMessage(spectatingLine(match));
        return true;
    }

    /** {@code Spectating <red> vs <blue>} — colours, not a single player's name. */
    private Component spectatingLine(MatchSession match) {
        java.util.List<UUID> red = match.team(com.rumilance.practice.state.TeamColor.RED);
        java.util.List<UUID> blue = match.team(com.rumilance.practice.state.TeamColor.BLUE);
        Component redPart = sideText(red, NamedTextColor.RED);
        Component bluePart = sideText(blue, NamedTextColor.AQUA);
        return Component.text("Spectating ", NamedTextColor.GRAY)
                .append(redPart)
                .append(Component.text(" vs ", NamedTextColor.DARK_GRAY))
                .append(bluePart);
    }

    /** A side's label: one name in a duel, {@code NxN} member count in a team battle. */
    private Component sideText(java.util.List<UUID> members, NamedTextColor color) {
        if (members.size() == 1) {
            Player p = Bukkit.getPlayer(members.get(0));
            String name = p != null ? p.getName()
                    : com.rumilance.practice.stats.StatsService.nameOf(members.get(0));
            return Component.text(name, color);
        }
        return Component.text(members.size() + " players", color);
    }

    /** Matches are spectatable from arena reservation through the live fight. */
    private boolean isSpectatable(MatchState state) {
        return state == MatchState.RESERVING_ARENA
                || state == MatchState.PASTING_ARENA
                || state == MatchState.COUNTDOWN
                || state == MatchState.ACTIVE;
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

    /**
     * Drops the match bookkeeping for a rematch WITHOUT sending spectators back to the lobby
     * (their camera stays in the arena, which the rematch reuses). The bookkeeping is
     * re-established by {@link #attachCarried(MatchSession)} once the new countdown starts.
     */
    public void detachForRematch(UUID matchId) {
        Set<UUID> specs = matchSpectators.remove(matchId);
        if (specs != null) {
            for (UUID id : specs) {
                spectatorToMatch.remove(id);
            }
        }
    }

    /**
     * Registers the spectators of a match that is rematching, so they follow the new match.
     * Keyed by every (carried-over) participant UUID: whichever match starts with one of them
     * consumes the set. A safety timeout bails leftovers to the lobby.
     */
    public void scheduleCarry(java.util.Collection<UUID> newMatchParticipants, Set<UUID> spectators) {
        if (spectators == null || spectators.isEmpty()) {
            return;
        }
        Set<UUID> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(spectators);
        for (UUID participant : newMatchParticipants) {
            pendingCarry.put(participant, copy);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID participant : newMatchParticipants) {
                Set<UUID> leftover = pendingCarry.remove(participant);
                if (leftover == null) {
                    continue;
                }
                for (UUID id : leftover) {
                    // Only bail spectators still stuck in limbo (already attached / left = skip).
                    if (!spectatorToMatch.containsKey(id) && stateManager.getState(id) == PlayerState.SPECTATING) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null) {
                            leave(player);
                        } else {
                            stateManager.resetToLobby(id);
                        }
                    }
                }
            }
        }, CARRY_TIMEOUT_TICKS);
    }

    /**
     * Settles spectators the moment a match's countdown begins (fighters are already standing
     * on their spawns): carried-over rematch spectators get re-bound to the new match id, and
     * every spectator bound to this match — including players who started spectating during
     * the reservation/countdown window while the fighters were still in the lobby — is moved
     * into the arena.
     */
    public void attachCarried(MatchSession session) {
        Set<UUID> carried = null;
        for (UUID participant : session.participants()) {
            carried = pendingCarry.remove(participant);
            if (carried != null) {
                break;
            }
        }
        for (UUID participant : session.participants()) {
            pendingCarry.remove(participant);
        }
        if (carried != null) {
            for (UUID id : carried) {
                Player spectator = Bukkit.getPlayer(id);
                if (spectator == null || !spectator.isOnline()
                        || stateManager.getState(id) != PlayerState.SPECTATING) {
                    continue;
                }
                spectatorToMatch.put(id, session.id());
                matchSpectators.computeIfAbsent(session.id(), matchId -> ConcurrentHashMap.newKeySet())
                        .add(id);
            }
        }
        Set<UUID> bound = matchSpectators.get(session.id());
        if (bound == null || bound.isEmpty()) {
            return;
        }
        UUID first = session.participants().isEmpty() ? null : session.participants().get(0);
        Player anchor = first == null ? null : Bukkit.getPlayer(first);
        for (UUID id : Set.copyOf(bound)) {
            Player spectator = Bukkit.getPlayer(id);
            if (spectator == null || !spectator.isOnline()
                    || stateManager.getState(id) != PlayerState.SPECTATING) {
                continue;
            }
            if (viewControl != null) {
                viewControl.applyForMatch(spectator, session);
            }
            if (anchor != null) {
                moveSpectatorTo(spectator, anchor.getLocation());
            }
            if (carried != null && carried.contains(id)) {
                spectator.sendMessage(spectatingLine(session));
            }
        }
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
        // Also drop them from any pending rematch hand-off, so a later countdown
        // never re-attaches someone who already left.
        for (Set<UUID> carried : pendingCarry.values()) {
            carried.remove(spectator.getUniqueId());
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
