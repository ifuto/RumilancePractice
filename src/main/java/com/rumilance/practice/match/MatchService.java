package com.rumilance.practice.match;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.combat.KillFeed;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.result.FfaResultProcessor;
import com.rumilance.practice.match.result.MatchResultProcessor;
import com.rumilance.practice.match.result.RankedResultProcessor;
import com.rumilance.practice.match.result.UnrankedResultProcessor;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.ItemSerializer;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.PlayerVitals;
import com.rumilance.practice.util.SafeTeleport;
import com.rumilance.practice.scoreboard.TabVisibilityService;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.SplashPotionDurations;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.model.KitStartEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Orchestrates duel match lifecycle: reserve arena, countdown, fight, end, rematch.
 */
public final class MatchService {

    private final Plugin plugin;
    private final ArenaService arenaService;
    private final KitService kitService;
    private final KitLayoutCache layoutCache;
    private final LobbyService lobbyService;
    private final MatchRegistry registry;
    private final PlayerStateManager stateManager;
    private final DuelRequestService duelRequestService;
    private final SoundService soundService;
    private final AsyncExecutor asyncExecutor;
    private final RankedResultProcessor rankedResultProcessor;
    private final UnrankedResultProcessor unrankedResultProcessor;
    private final FfaResultProcessor ffaResultProcessor;
    private final int countdownSeconds;
    private final int rematchSeconds;
    private final int maxDurationSeconds;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private volatile boolean shuttingDown;
    /**
     * In-memory tally of consecutive pre-match-countdown leaves per player. Incremented when a
     * player runs {@code /leave} during the countdown, reset to 0 the moment one of their matches
     * actually reaches FIGHT. Three in a row triggers a 3-day ChatBan. (Bans themselves persist;
     * this streak resets on server restart.)
     */
    private final Map<UUID, Integer> countdownLeaveStreak = new ConcurrentHashMap<>();
    private final MatchCombatTracker combatTracker = new MatchCombatTracker();
    /** Each player's most recent match id, retained for a short time so /matchreport works. */
    private final Map<UUID, UUID> recentMatch = new ConcurrentHashMap<>();
    /**
     * Server tick at which each match last saw a lethal, plus the set of participants that went
     * lethal on that tick. A 1v1 is a draw ONLY when both fighters drop on the exact same tick
     * (truly simultaneous); any other death — self-inflicted ender-pearl fall damage, crystal
     * misfire, void, etc. — is a win for the survivor.
     */
    private final Map<UUID, Integer> lastLethalTickByMatch = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<UUID>> lethalPlayersByMatch = new ConcurrentHashMap<>();
    /** Attacker id recorded for each fighter that went lethal this match. Lets the 1-tick deferred
     *  ruling tell a true mutual kill (each player died to the OTHER player) apart from a
     *  suicide/environmental death that merely shares a tick — only the former is a draw. */
    private final Map<UUID, Map<UUID, UUID>> lethalAttackerByMatch = new ConcurrentHashMap<>();
    /** Count of matches that hit the absolute 1-hour hard limit (per player). 3 = 4-day ban. */
    private final Map<UUID, Integer> hardTimeoutStrikes = new ConcurrentHashMap<>();
    /** Matches already ruled by the hard 1-hour limit, so it never fires twice. */
    private final java.util.Set<UUID> hardTimedOut = ConcurrentHashMap.newKeySet();
    /** Per-match hard-cap task (independent of the normal timeout task keyed in {@link #tasks}). */
    private final Map<UUID, BukkitTask> hardTimeoutTasks = new ConcurrentHashMap<>();
    /** Absolute cap that always applies even when no admin timeout is configured. */
    private static final int HARD_TIMEOUT_SECONDS = 60 * 60;
    private static final int HARD_TIMEOUT_STRIKES_BAN = 3;
    private static final java.time.Duration HARD_TIMEOUT_BAN = java.time.Duration.ofDays(4);
    /** Fighters whose lethal has already been processed this match — guards against a second
     *  lethal event for the same player (double damage events in the 1-tick resolution window). */
    private final Map<UUID, java.util.Set<UUID>> resolvedLethalByMatch = new ConcurrentHashMap<>();
    private com.rumilance.practice.spectator.SpectatorService spectatorService;
    private com.rumilance.practice.punishment.ChatBanService chatBanService;
    private SettingsService settingsService;
    private QueueCoordinator queueCoordinator;
    private MessageService messageService;
    private com.rumilance.practice.cosmetic.TitleService titleService;
    private java.util.function.Consumer<Player> matchReportOpener;
    private java.util.function.Consumer<Player> hubReturn;
    private com.rumilance.practice.util.PlayerPlacedBlockTracker playerPlacedBlocks;

    public MatchService(
            Plugin plugin,
            ArenaService arenaService,
            KitService kitService,
            KitLayoutCache layoutCache,
            LobbyService lobbyService,
            MatchRegistry registry,
            PlayerStateManager stateManager,
            DuelRequestService duelRequestService,
            SoundService soundService,
            AsyncExecutor asyncExecutor,
            RankedResultProcessor rankedResultProcessor,
            UnrankedResultProcessor unrankedResultProcessor,
            FfaResultProcessor ffaResultProcessor,
            int countdownSeconds,
            int rematchSeconds,
            int maxDurationSeconds
    ) {
        this.plugin = plugin;
        this.arenaService = arenaService;
        this.kitService = kitService;
        this.layoutCache = layoutCache;
        this.lobbyService = lobbyService;
        this.registry = registry;
        this.stateManager = stateManager;
        this.duelRequestService = duelRequestService;
        this.soundService = soundService;
        this.asyncExecutor = asyncExecutor;
        this.rankedResultProcessor = rankedResultProcessor;
        this.unrankedResultProcessor = unrankedResultProcessor;
        this.ffaResultProcessor = ffaResultProcessor;
        this.countdownSeconds = Math.max(1, countdownSeconds);
        this.rematchSeconds = Math.max(1, rematchSeconds);
        this.maxDurationSeconds = Math.max(0, maxDurationSeconds);
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void setQueueCoordinator(QueueCoordinator queueCoordinator) {
        this.queueCoordinator = queueCoordinator;
    }

    public void setSpectatorService(com.rumilance.practice.spectator.SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    private com.rumilance.practice.practice.PracticeService practiceService;

    public void setPracticeService(com.rumilance.practice.practice.PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void setChatBanService(com.rumilance.practice.punishment.ChatBanService chatBanService) {
        this.chatBanService = chatBanService;
    }

    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    public void setTitleService(com.rumilance.practice.cosmetic.TitleService titleService) {
        this.titleService = titleService;
    }

    public void setHubReturn(java.util.function.Consumer<Player> hubReturn) {
        this.hubReturn = hubReturn;
    }

    public void setPlayerPlacedBlockTracker(com.rumilance.practice.util.PlayerPlacedBlockTracker playerPlacedBlocks) {
        this.playerPlacedBlocks = playerPlacedBlocks;
    }

    private volatile TeamColoredArmorService teamColoredArmorService;
    private volatile TeamService teamService;
    private volatile TabVisibilityService tabVisibilityService;

    public void setTeamColoredArmorService(TeamColoredArmorService teamColoredArmorService) {
        this.teamColoredArmorService = teamColoredArmorService;
    }

    public void setTeamService(TeamService teamService) {
        this.teamService = teamService;
    }

    public void setTabVisibilityService(com.rumilance.practice.scoreboard.TabVisibilityService tabVisibilityService) {
        this.tabVisibilityService = tabVisibilityService;
    }

    /** Per-player border/view-distance control; optional (null = feature off). */
    private com.rumilance.practice.sight.ViewControlService viewControl;

    public void setViewControl(com.rumilance.practice.sight.ViewControlService viewControl) {
        this.viewControl = viewControl;
    }

    /** Applies the arena border + view cap to one player of {@code session} (safe if off). */
    private void applySight(Player player, MatchSession session) {
        if (viewControl != null && player != null) {
            viewControl.applyForMatch(player, session);
        }
    }

    public void setMatchReportOpener(java.util.function.Consumer<Player> matchReportOpener) {
        this.matchReportOpener = matchReportOpener;
    }

    private com.rumilance.practice.duel.DuelLogStore duelLogStore;
    private MatchActionRecorder actionRecorder;
    private com.rumilance.practice.replay.ReplayArchive replayArchive;

    public void setReplayArchive(com.rumilance.practice.replay.ReplayArchive replayArchive) {
        this.replayArchive = replayArchive;
    }

    private com.rumilance.practice.replay.ReplayService replayService;
    private com.rumilance.practice.match.history.MatchHistoryStore historyStore;

    public void setReplayService(com.rumilance.practice.replay.ReplayService replayService) {
        this.replayService = replayService;
    }

    public void setHistoryStore(com.rumilance.practice.match.history.MatchHistoryStore historyStore) {
        this.historyStore = historyStore;
    }
    private com.rumilance.practice.match.inventory.MatchInventoryStore inventoryStore;
    private com.rumilance.practice.ffa.FfaService ffaService;
    private com.rumilance.practice.combat.CombatNetTracker combatNet;
    private com.rumilance.practice.originalkit.OriginalKitService originalKitService;
    private com.rumilance.practice.database.repository.DailyRankedStatsRepository dailyStatsRepository;

    public void setOriginalKitService(com.rumilance.practice.originalkit.OriginalKitService originalKitService) {
        this.originalKitService = originalKitService;
    }

    public void setDailyStatsRepository(
            com.rumilance.practice.database.repository.DailyRankedStatsRepository dailyStatsRepository) {
        this.dailyStatsRepository = dailyStatsRepository;
    }

    /**
     * Monthly kill leaderboard accounting: one kill for the attacker, one death for the
     * victim. Failures only log at FINE — stat bookkeeping must never break a lethal.
     */
    private void recordMonthlyKillDeath(UUID attackerId, UUID victimId) {
        var repository = dailyStatsRepository;
        if (repository == null) {
            return;
        }
        try {
            repository.increment(attackerId, 1, 0, 0);
            repository.increment(victimId, 0, 0, 1);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "[KillLB] daily stat increment failed", t);
        }
    }

    public void setDuelLogStore(com.rumilance.practice.duel.DuelLogStore duelLogStore) {
        this.duelLogStore = duelLogStore;
    }

    public com.rumilance.practice.duel.DuelLogStore duelLogStore() {
        return duelLogStore;
    }

    private void assignDuelId(MatchSession session) {
        if (duelLogStore == null || session == null) {
            return;
        }
        String player1;
        String player2;
        if (session.isTeamMatch()) {
            List<UUID> red = session.team(TeamColor.RED);
            List<UUID> blue = session.team(TeamColor.BLUE);
            player1 = StatsService.nameOf(red.get(0));
            player2 = StatsService.nameOf(blue.get(0));
        } else {
            player1 = StatsService.nameOf(session.participants().get(0));
            player2 = StatsService.nameOf(session.participants().get(1));
        }
        session.setPublicDuelId(duelLogStore.append(player1, player2));
    }

    public void setActionRecorder(MatchActionRecorder actionRecorder) {
        this.actionRecorder = actionRecorder;
    }

    public MatchActionRecorder actionRecorder() {
        return actionRecorder;
    }

    public void setInventoryStore(com.rumilance.practice.match.inventory.MatchInventoryStore inventoryStore) {
        this.inventoryStore = inventoryStore;
    }

    public com.rumilance.practice.match.inventory.MatchInventoryStore inventoryStore() {
        return inventoryStore;
    }

    public void setFfaService(com.rumilance.practice.ffa.FfaService ffaService) {
        this.ffaService = ffaService;
    }

    public com.rumilance.practice.ffa.FfaService ffaService() {
        return ffaService;
    }

    public void setCombatNet(com.rumilance.practice.combat.CombatNetTracker combatNet) {
        this.combatNet = combatNet;
    }

    public com.rumilance.practice.combat.CombatNetTracker combatNet() {
        return combatNet;
    }

    public MatchRegistry registry() {
        return registry;
    }

    /**
     * True when the player is eliminated from an ongoing TEAM match — i.e. they died in the party
     * fight and are now watching the rest of it as a spectator (GameMode SPECTATOR) while their
     * team is still playing. Such a player must never be allowed to send/accept a solo duel.
     * Distinct from a free-roaming lobby spectator (PlayerState.SPECTATING, not in a match).
     */
    public boolean isEliminatedInTeamMatch(UUID playerId) {
        MatchSession session = registry.byPlayer(playerId).orElse(null);
        if (session == null || !session.isTeamMatch()) {
            return false;
        }
        MatchState state = session.state();
        if (state != MatchState.ACTIVE && state != MatchState.COUNTDOWN) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        return player != null && player.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    /**
     * Hard block on entering a solo duel: currently committed to an active match
     * (FIGHTING/COUNTDOWN/PREPARING) or eliminated from a team match (watching the rest of it).
     */
    public boolean isBusyForSoloDuel(UUID playerId) {
        PlayerState state = stateManager.getState(playerId);
        if (state == PlayerState.FIGHTING
                || state == PlayerState.COUNTDOWN
                || state == PlayerState.PREPARING_MATCH) {
            return true;
        }
        return isEliminatedInTeamMatch(playerId);
    }

    /**
     * Hard block on opening the kit editors (/ekit, /originalkit): the player is committed to
     * any match lifecycle, queue, spectating, FFA or practice. Editing mid-fight used to reset
     * the player to lobby items on GUI close and strand their real inventory in the kit room.
     */
    public boolean isBusyForKitEdit(UUID playerId) {
        if (registry().byPlayer(playerId).isPresent()) {
            return true;
        }
        PlayerState state = stateManager.getState(playerId);
        return switch (state) {
            case QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL,
                 PREPARING_MATCH, COUNTDOWN, FIGHTING, ENDING,
                 SPECTATING, FFA, PRACTICE_WAIT, PRACTICE_ACTIVE -> true;
            default -> false;
        };
    }

    private boolean inParty(UUID playerId) {
        return teamService != null && teamService.teamOf(playerId).isPresent();
    }

    private void denyDuel(UUID playerA, UUID playerB) {
        if (messageService != null) {
            for (UUID id : List.of(playerA, playerB)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    messageService.send(p, "party.solo-only");
                }
            }
        } else {
            for (UUID id : List.of(playerA, playerB)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage(net.kyori.adventure.text.Component.text(
                            "Leave your party to duel solo.",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }
    }


    public MatchCombatTracker combatTracker() {
        return combatTracker;
    }

    /** @return the id of the player's most recent match (if still cached), used by /matchreport. */
    public Optional<UUID> lastMatchId(UUID playerId) {
        return Optional.ofNullable(recentMatch.get(playerId));
    }

    public void setShuttingDown(boolean value) {
        this.shuttingDown = value;
        for (MatchSession session : registry.all()) {
            session.setShuttingDown(value);
        }
        if (chatBanService != null) {
            chatBanService.setShuttingDown(value);
        }
    }

    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode, int bestOf) {
        startDuel(playerA, playerB, kitId, mode, bestOf, Map.of(), null);
    }

    /**
     * Starts a duel. {@code carrySeriesWins} carries a rematch chain's accumulated wins into
     * the new session (empty for fresh queue/duel-request matches, so the score starts 0-0).
     */
    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode,
                          int bestOf, Map<UUID, Integer> carrySeriesWins) {
        startDuel(playerA, playerB, kitId, mode, bestOf, carrySeriesWins, null);
    }

    /**
     * Starts a duel, optionally reserving a named arena template when {@code preferredArena}
     * is set (from map select / rematch).
     */
    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode,
                          int bestOf, Map<UUID, Integer> carrySeriesWins, String preferredArena) {
        startDuel(playerA, playerB, kitId, mode, bestOf, carrySeriesWins, preferredArena, null);
    }

    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode,
                          int bestOf, Map<UUID, Integer> carrySeriesWins, String preferredArena,
                          UUID carryArenaInstanceId) {
        // Hard gates before anything is reserved: a solo duel must never start for a player who
        // is in a party (parties fight together as a team, never 1v1), and never for a player
        // committed to a fight — including someone ELIMINATED from a team match (watching the
        // rest of it). Rematch carries the same party-less context as the duel that spawned it.
        if (inParty(playerA) || inParty(playerB)) {
            denyDuel(playerA, playerB);
            return;
        }
        if (isBusyForSoloDuel(playerA) || isBusyForSoloDuel(playerB)) {
            messageAlreadyInMatch(playerA, playerB);
            return;
        }
        if (registry.isPlayerInMatch(playerA) || registry.isPlayerInMatch(playerB)) {
            messageAlreadyInMatch(playerA, playerB);
            return;
        }
        Player onlineA = Bukkit.getPlayer(playerA);
        Player onlineB = Bukkit.getPlayer(playerB);
        if (onlineA == null || onlineB == null) {
            sendBothToLobby(playerA, playerB);
            return;
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || !kit.enabled()) {
            sendBothToLobby(playerA, playerB);
            return;
        }

        MatchSession session = new MatchSession(
                UUID.randomUUID(), mode, kitId, List.of(playerA, playerB), null, bestOf);
        session.applySeries(carrySeriesWins);
        if (preferredArena != null && !preferredArena.isBlank()
                && !"random".equalsIgnoreCase(preferredArena)) {
            session.setPreferredArenaName(preferredArena);
        }
        if (!registry.register(session)) {
            messageAlreadyInMatch(playerA, playerB);
            return;
        }
        assignDuelId(session);

        duelRequestService.invalidateForPlayer(playerA);
        duelRequestService.invalidateForPlayer(playerB);
        // Pull both players out of FFA / spectate / practice / queue / kit-editor without
        // teleporting, so the arena teleport below is the only move and can't be raced.
        evictFromAnyActivity(onlineA);
        evictFromAnyActivity(onlineB);
        onlineA.closeInventory();
        onlineB.closeInventory();
        boolean okA = tryTransition(playerA, PlayerState.PREPARING_MATCH);
        boolean okB = tryTransition(playerB, PlayerState.PREPARING_MATCH);
        if (!okA || !okB) {
            failMatch(session, "Could not prepare players");
            return;
        }
        session.setState(MatchState.RESERVING_ARENA);
        announceMatchFound(session);
        logMatchStart(session);

        if (tryBeginWithCarriedArena(session, carryArenaInstanceId,
                instance -> teleportAndPrepare(session, kit, instance))) {
            return;
        }

        String named = session.preferredArenaName();
        CompletableFuture<Optional<ArenaInstance>> reservation;
        if (named != null && !named.isBlank()) {
            reservation = arenaService.reserveNamed(named, session.id());
        } else {
            reservation = reserveArenaFor(kit, session.id());
        }
        reservation.whenComplete((opt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || opt == null || opt.isEmpty()) {
                        failMatch(session, "No arena available");
                        return;
                    }
                    ArenaInstance instance = opt.get();
                    if (!registry.tryReserveArena(instance.id(), session.id())) {
                        arenaService.release(instance.id());
                        failMatch(session, "Arena already reserved");
                        return;
                    }
                    registry.bindArena(session.id(), instance.id());
                    session.setState(MatchState.WAITING_FOR_PLAYERS);
                    teleportAndPrepare(session, kit, instance);
                }));
    }

    private boolean tryBeginWithCarriedArena(MatchSession session, UUID carryArenaInstanceId,
                                             java.util.function.Consumer<ArenaInstance> prepare) {
        if (carryArenaInstanceId == null) {
            return false;
        }
        Optional<ArenaInstance> opt = arenaService.get(carryArenaInstanceId);
        if (opt.isEmpty() || !arenaService.reassignInstance(carryArenaInstanceId, session.id())) {
            return false;
        }
        if (!registry.tryReserveArena(carryArenaInstanceId, session.id())) {
            return false;
        }
        registry.bindArena(session.id(), carryArenaInstanceId);
        session.setState(MatchState.WAITING_FOR_PLAYERS);
        prepare.accept(opt.get());
        return true;
    }

    private void messageAlreadyInMatch(UUID playerA, UUID playerB) {
        for (UUID id : List.of(playerA, playerB)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (messageService != null) {
                messageService.send(player, "duel.already-in-match");
            } else {
                player.sendMessage(Component.text("Already in a match.", NamedTextColor.RED));
            }
        }
    }

    /**
     * Reserves the arena for {@code kit}: kits pinned to one arena ({@code /kit arena})
     * always use that exact template; unpinned kits get any free duel arena.
     */
    private java.util.concurrent.CompletableFuture<Optional<ArenaInstance>> reserveArenaFor(
            KitDefinition kit, UUID matchId) {
        if (kit.hasFixedArena()) {
            List<String> pool = kit.arenas();
            String chosen = pool.size() == 1
                    ? pool.getFirst()
                    : pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
            return arenaService.reserveNamed(chosen, matchId);
        }
        return arenaService.reserve(ArenaType.DUEL, matchId);
    }

    private java.util.concurrent.CompletableFuture<Optional<ArenaInstance>> reservePartyArenaFor(
            KitDefinition kit, UUID matchId) {
        if (kit.hasPartyArenaPool()) {
            List<String> pool = kit.partyArenas();
            String chosen = pool.size() == 1
                    ? pool.getFirst()
                    : pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
            return arenaService.reserveNamed(chosen, matchId);
        }
        return arenaService.reserve(ArenaType.DUEL, matchId);
    }

    /**
     * Starts a RED-vs-BLUE team battle. Each side may hold 1..15 players and the ratio can be
     * arbitrarily uneven (e.g. 2v7). RED spawns at spawn A, BLUE at spawn B (each player gets a
     * small horizontal offset so teammates don't stack), and friendly fire is disabled. This is
     * the no-queue entry point used by the team hub GUI / {@code /team start}.
     */
    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, int bestOf) {
        startTeamMatch(redTeam, blueTeam, kitId, mode, bestOf, null);
    }

    /**
     * Starts a RED-vs-BLUE team battle. When {@code partyArenaName} is set, that exact arena
     * template is reserved; otherwise falls back to the kit's pinned arena / any duel arena.
     */
    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, int bestOf, String partyArenaName) {
        startTeamMatch(redTeam, blueTeam, kitId, mode, bestOf, partyArenaName, false);
    }

    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, int bestOf, String partyArenaName,
                               boolean friendlyFire) {
        startTeamMatch(redTeam, blueTeam, kitId, mode, bestOf, partyArenaName, friendlyFire, Map.of());
    }

    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, int bestOf, String partyArenaName,
                               boolean friendlyFire, Map<UUID, Integer> carrySeriesWins) {
        startTeamMatch(redTeam, blueTeam, kitId, mode, bestOf, partyArenaName, friendlyFire, carrySeriesWins, null);
    }

    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, int bestOf, String partyArenaName,
                               boolean friendlyFire, Map<UUID, Integer> carrySeriesWins,
                               UUID carryArenaInstanceId) {
        startTeamMatch(redTeam == null || blueTeam == null
                        ? List.of() : List.of(redTeam, blueTeam),
                kitId, mode, bestOf, partyArenaName, friendlyFire,
                carrySeriesWins, carryArenaInstanceId, Map.of(), Map.of());
    }

    /**
     * Starts a party battle with 2..7 team rosters (canonical color order). Per-team kit
     * overrides and battle configs (health / size / effects) ride along with the session.
     */
    public void startTeamMatch(List<List<UUID>> rosters, String kitId, MatchMode mode, int bestOf,
                               String partyArenaName, boolean friendlyFire,
                               Map<UUID, Integer> carrySeriesWins, UUID carryArenaInstanceId,
                               Map<TeamColor, String> teamKits,
                               Map<TeamColor, com.rumilance.practice.team.TeamConfig> teamConfigs) {
        startTeamMatch(rosters, kitId, mode, bestOf, partyArenaName, friendlyFire,
                carrySeriesWins, carryArenaInstanceId, teamKits, teamConfigs, null);
    }

    /** Roster start with an optional owner original-kit loadout for the whole party. */
    public void startTeamMatch(List<List<UUID>> rosters, String kitId, MatchMode mode, int bestOf,
                               String partyArenaName, boolean friendlyFire,
                               Map<UUID, Integer> carrySeriesWins, UUID carryArenaInstanceId,
                               Map<TeamColor, String> teamKits,
                               Map<TeamColor, com.rumilance.practice.team.TeamConfig> teamConfigs,
                               com.rumilance.practice.team.OriginalKitRef originalKit) {
        if (rosters == null || rosters.size() < 2 || rosters.size() > TeamColor.MAX_TEAMS) {
            return;
        }
        List<UUID> all = new ArrayList<>();
        for (List<UUID> roster : rosters) {
            if (roster == null || roster.isEmpty() || roster.size() > MatchSession.MAX_SIDE_SIZE) {
                return;
            }
            all.addAll(roster);
        }
        if (all.stream().distinct().count() != all.size()) {
            return;
        }
        for (UUID id : all) {
            if (registry.isPlayerInMatch(id) || Bukkit.getPlayer(id) == null) {
                return;
            }
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || !kit.enabled()) {
            return;
        }

        MatchSession session;
        try {
            session = MatchSession.forTeams(UUID.randomUUID(), mode, kitId, rosters, null, bestOf);
        } catch (IllegalArgumentException e) {
            return;
        }
        session.setFriendlyFire(friendlyFire);
        session.applySeries(carrySeriesWins);
        if (teamKits != null) {
            teamKits.forEach(session::setTeamKit);
        }
        if (teamConfigs != null) {
            teamConfigs.forEach(session::setTeamConfig);
        }
        session.setOriginalKitRef(originalKit);
        if (partyArenaName != null && !partyArenaName.isBlank()
                && !"random".equalsIgnoreCase(partyArenaName)) {
            session.setPreferredArenaName(partyArenaName);
        }
        if (!registry.register(session)) {
            return;
        }
        assignDuelId(session);
        boolean allPrepared = true;
        for (UUID id : all) {
            duelRequestService.invalidateForPlayer(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                evictFromAnyActivity(online);
                online.closeInventory();
            }
            if (!tryTransition(id, PlayerState.PREPARING_MATCH)) {
                allPrepared = false;
            }
        }
        if (!allPrepared) {
            failMatch(session, "Could not prepare players");
            return;
        }
        session.setState(MatchState.RESERVING_ARENA);
        announceMatchFound(session);
        logMatchStart(session);

        if (tryBeginWithCarriedArena(session, carryArenaInstanceId,
                instance -> teleportAndPrepareTeam(session, kit, instance))) {
            return;
        }

        java.util.concurrent.CompletableFuture<Optional<ArenaInstance>> reservation;
        if (partyArenaName != null && !partyArenaName.isBlank()) {
            reservation = arenaService.reserveNamed(partyArenaName, session.id());
        } else {
            reservation = reservePartyArenaFor(kit, session.id());
        }
        reservation.whenComplete((opt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || opt == null || opt.isEmpty()) {
                        failMatch(session, "No arena available");
                        return;
                    }
                    ArenaInstance instance = opt.get();
                    if (!registry.tryReserveArena(instance.id(), session.id())) {
                        arenaService.release(instance.id());
                        failMatch(session, "Arena already reserved");
                        return;
                    }
                    registry.bindArena(session.id(), instance.id());
                    session.setState(MatchState.WAITING_FOR_PLAYERS);
                    teleportAndPrepareTeam(session, kit, instance);
                }));
    }

    private void teleportAndPrepareTeam(MatchSession session, KitDefinition kit, ArenaInstance instance) {
        teleportAndPrepareTeam(session, kit, instance, false);
    }

    private void teleportAndPrepareTeam(MatchSession session, KitDefinition kit, ArenaInstance instance,
                                         boolean arenaRetried) {
        sweepLeftoverEntities(instance);
        List<java.util.concurrent.CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                failMatch(session, "Player offline during prepare");
                return;
            }
            TeamColor color = session.teamColor(id);
            Location base = teamSpawnBase(session, instance, color);
            List<UUID> side = session.team(color);
            int withinTeam = side.indexOf(id);
            double offset = (withinTeam - (side.size() - 1) / 2.0) * 1.2;
            Location dest = base.clone().add(offset, 0, 0);
            dest.setYaw(base.getYaw());
            dest.setPitch(base.getPitch());
            teleports.add(SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(dest)));
        }
        java.util.concurrent.CompletableFuture.allOf(
                teleports.toArray(new java.util.concurrent.CompletableFuture[0])
        ).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !allTeleportsSucceeded(teleports)) {
                retryPrepareAfterBadArena(session, kit, instance, arenaRetried);
                return;
            }
            for (UUID id : session.participants()) {
                if (Bukkit.getPlayer(id) == null) {
                    failMatch(session, "Player offline during prepare");
                    return;
                }
            }
            com.rumilance.practice.team.OriginalKitRef originalKit = session.originalKitRef();
            ItemStack[] originalLayout = null;
            if (originalKit != null && originalKitService != null) {
                originalLayout = originalKitService.loadLayout(originalKit.owner(), originalKit.slot());
            }
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    PlayerVitals.clearCombatState(player);
                    boolean ownKitOverride = !session.kitFor(id).equals(session.kitName());
                    KitDefinition playerKit = kitService.get(session.kitFor(id)).orElse(kit);
                    // Owner's original kit supplies the loadout for everyone who does not run
                    // a per-team kit override; rules always stay with the shared match kit.
                    if (originalLayout != null && !ownKitOverride) {
                        applyKit(player, kit, originalLayout);
                    } else {
                        applyKit(player, playerKit);
                    }
                    applySight(player, session);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : session.participants()) {
                    if (Bukkit.getPlayer(id) == null) {
                        failMatch(session, "Player offline during prepare");
                        return;
                    }
                }
                startCountdown(session);
            }, 20L);
        }));
    }

    /**
     * Team spawn for a color: RED anchors at spawn A, BLUE at spawn B, and any further teams
     * spread evenly along the A→B line, so multi-team battles work on every duel arena.
     * With the classic two teams this is exactly spawnA / spawnB as before.
     */
    private Location teamSpawnBase(MatchSession session, ArenaInstance instance, TeamColor color) {
        Location a = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance));
        Location b = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance));
        List<TeamColor> colors = session.distinctTeamColors();
        int index = colors.indexOf(color);
        if (index <= 0) {
            return a;
        }
        if (index >= colors.size() - 1) {
            return b;
        }
        double t = (double) index / Math.max(1, colors.size() - 1);
        Location mid = a.clone();
        mid.setX(a.getX() + (b.getX() - a.getX()) * t);
        mid.setY(a.getY() + (b.getY() - a.getY()) * t);
        mid.setZ(a.getZ() + (b.getZ() - a.getZ()) * t);
        mid.setYaw(t < 0.5 ? a.getYaw() : b.getYaw());
        mid.setPitch(t < 0.5 ? a.getPitch() : b.getPitch());
        return mid;
    }

    private void teleportAndPrepare(MatchSession session, KitDefinition kit, ArenaInstance instance) {
        teleportAndPrepare(session, kit, instance, false);
    }

    private void teleportAndPrepare(MatchSession session, KitDefinition kit, ArenaInstance instance,
                                   boolean arenaRetried) {
        Player p1 = Bukkit.getPlayer(session.participants().get(0));
        Player p2 = Bukkit.getPlayer(session.participants().get(1));
        if (p1 == null || p2 == null) {
            failMatch(session, "Player offline during prepare");
            return;
        }
        Location spawnA = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance));
        Location spawnB = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance));
        sweepLeftoverEntities(instance);
        // Brief beat (0.5s) so players register the MATCH FOUND notification before the teleport.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(p1.getUniqueId()) == null || Bukkit.getPlayer(p2.getUniqueId()) == null) {
                failMatch(session, "Player offline during prepare");
                return;
            }
            // Wait for both teleports before countdown so clients never render a border-outside frame.
            var futureA = SafeTeleport.teleport(p1, spawnA);
            var futureB = SafeTeleport.teleport(p2, spawnB);
            futureA.thenCombine(futureB, (a, b) -> a && b).whenComplete((ok, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null || !Boolean.TRUE.equals(ok)) {
                            retryPrepareAfterBadArena(session, kit, instance, arenaRetried);
                            return;
                        }
                        if (Bukkit.getPlayer(p1.getUniqueId()) == null || Bukkit.getPlayer(p2.getUniqueId()) == null) {
                            failMatch(session, "Player offline during prepare");
                            return;
                        }
                        PlayerVitals.clearCombatState(p1);
                        PlayerVitals.clearCombatState(p2);
                        applyKit(p1, kit);
                        applyKit(p2, kit);
                        applySight(p1, session);
                        applySight(p2, session);
                        // Teleport FIRST, then a 1s settle beat so both clients finish
                        // rendering the (possibly far-away) arena before the countdown starts.
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (Bukkit.getPlayer(p1.getUniqueId()) == null
                                    || Bukkit.getPlayer(p2.getUniqueId()) == null) {
                                failMatch(session, "Player offline during prepare");
                                return;
                            }
                            startCountdown(session);
                        }, 20L);
                    }));
        }, 10L);
    }

    /**
     * Removes combat leftovers from the previous occupant of a reused arena before players are
     * teleported in: arrows/tridents, ender crystals, primed TNT, falling blocks, dropped items
     * and other projectiles. Without this, arenas served by the FAWE-free SimpleArenaService
     * (which only flips a reservation flag and never repastes) would carry an old fight's arrows
     * and crystals into the next match — a stray arrow or armed crystal could hit a spawning
     * fighter. Players and permanent fixtures (item frames, armor stands, NPCs) are left intact.
     */
    private void sweepLeftoverEntities(ArenaInstance instance) {
        if (instance == null) {
            return;
        }
        try {
            com.rumilance.practice.util.Cuboid bounds = instance.bounds();
            if (bounds == null) {
                return;
            }
            org.bukkit.World world = bounds.world();
            if (world == null) {
                return;
            }
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Player) {
                    continue;
                }
                if (!bounds.contains(entity.getLocation())) {
                    continue;
                }
                if (entity instanceof org.bukkit.entity.AbstractArrow
                        || entity instanceof org.bukkit.entity.EnderCrystal
                        || entity instanceof org.bukkit.entity.TNTPrimed
                        || entity instanceof org.bukkit.entity.minecart.ExplosiveMinecart
                        || entity instanceof org.bukkit.entity.FallingBlock
                        || entity instanceof org.bukkit.entity.Item
                        || entity instanceof org.bukkit.entity.Projectile) {
                    entity.remove();
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Failed to sweep leftover entities in arena " + instance.id(), e);
        }
    }

    private static boolean allTeleportsSucceeded(java.util.List<java.util.concurrent.CompletableFuture<Boolean>> futures) {        if (futures == null || futures.isEmpty()) {
            return false;
        }
        for (java.util.concurrent.CompletableFuture<Boolean> future : futures) {
            try {
                if (!Boolean.TRUE.equals(future.join())) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * When collision-free footing fails (common on freshly pasted arenas), release and reserve
     * a new instance once before failing the match.
     */
    private void retryPrepareAfterBadArena(MatchSession session, KitDefinition kit,
                                           ArenaInstance failed, boolean arenaRetried) {
        if (arenaRetried) {
            failMatch(session, "Could not teleport to arena");
            return;
        }
        plugin.getLogger().warning("Teleport footing failed for match " + session.id()
                + " — releasing arena and retrying once.");
        UUID arenaId = session.arenaInstanceId();
        if (arenaId != null) {
            arenaService.release(arenaId);
        }
        session.setArenaInstanceId(null);
        String named = session.preferredArenaName();
        java.util.concurrent.CompletableFuture<java.util.Optional<ArenaInstance>> reservation;
        if (named != null && !named.isBlank()) {
            reservation = arenaService.reserveNamed(named, session.id());
        } else {
            reservation = reserveArenaFor(kit, session.id());
        }
        reservation.whenComplete((opt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || opt == null || opt.isEmpty()) {
                failMatch(session, "No arena available after retry");
                return;
            }
            ArenaInstance instance = opt.get();
            if (!registry.tryReserveArena(instance.id(), session.id())) {
                arenaService.release(instance.id());
                failMatch(session, "Arena already reserved");
                return;
            }
            registry.bindArena(session.id(), instance.id());
            session.setState(MatchState.WAITING_FOR_PLAYERS);
            if (session.isTeamMatch()) {
                teleportAndPrepareTeam(session, kit, instance, true);
            } else {
                teleportAndPrepare(session, kit, instance, true);
            }
        }));
    }

    /**
     * Shows the MATCH FOUND title and plays the match-found jingle to both participants the
     * instant a match is created (before the arena is reserved and players are teleported).
     */
    private void announceMatchFound(MatchSession session) {
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            Component main = title(player, "match.found-title",
                    Component.text("⚔ MATCH FOUND ⚔")
                            .color(NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD));
            Component sub = title(player, "match.found-subtitle",
                    Component.text("· · ·", NamedTextColor.DARK_GRAY));
            player.showTitle(Title.title(
                    main, sub,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300))));
            soundService.play(player, "match-found");
        }
    }

    private void logMatchStart(MatchSession session) {
        StringBuilder line = new StringBuilder("Match start kit=");
        line.append(session.kitName());
        if (session.isTeamMatch()) {
            line.append(" RED=");
            appendPlayerNames(line, session.team(TeamColor.RED));
            line.append(" BLUE=");
            appendPlayerNames(line, session.team(TeamColor.BLUE));
        } else {
            List<UUID> people = session.participants();
            line.append(' ');
            line.append(onlineName(people.isEmpty() ? null : people.get(0)));
            line.append(" vs ");
            line.append(onlineName(people.size() < 2 ? null : people.get(1)));
        }
        plugin.getLogger().info(line.toString());
    }

    private static void appendPlayerNames(StringBuilder out, List<UUID> ids) {
        boolean first = true;
        for (UUID id : ids) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(onlineName(id));
        }
    }

    private static String onlineName(UUID id) {
        if (id == null) {
            return "?";
        }
        Player player = Bukkit.getPlayer(id);
        return player != null ? player.getName() : id.toString().substring(0, 8);
    }

    private void sendHome(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (hubReturn != null) {
            hubReturn.accept(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && hubReturn != null) {
                    hubReturn.accept(player);
                }
            }, 8L);
            return;
        }
        lobbyService.sendToLobby(player);
    }

    private void applyKit(Player player, KitDefinition kit) {
        layoutCache.loadSyncIfAbsent(player.getUniqueId(), kit.name());
        ItemStack[] layout = layoutCache.get(player.getUniqueId(), kit.name()).orElse(null);
        kitService.apply(player, kit, layout);
        PlayerVitals.applyCombatStart(player, kit.maxHealth());
    }

    /** Applies a kit with an explicit shared layout (party owner's original kit). */
    private void applyKit(Player player, KitDefinition kit, ItemStack[] layout) {
        kitService.apply(player, kit, layout);
        PlayerVitals.applyCombatStart(player, kit.maxHealth());
    }

    private void startCountdown(MatchSession session) {
        // Never start the countdown while a participant is outside the arena (or buried):
        // on rematch especially, players stand where the last fight ended and an earlier
        // teleport that technically succeeded but landed on a bad spot would otherwise
        // freeze the fight outside the walls. Re-pin everyone onto their team spawn first.
        ensureParticipantsInsideArena(session, () -> beginCountdown(session));
    }

    private void beginCountdown(MatchSession session) {
        session.setState(MatchState.COUNTDOWN);
        if (!transitionAll(session, PlayerState.COUNTDOWN)) {
            failMatch(session, "Could not start countdown");
            return;
        }
        // Rematch hand-off: fighters are now placed on their spawns, so carried-over
        // spectators of the previous match can be bound to this one and moved in.
        if (spectatorService != null) {
            spectatorService.attachCarried(session);
        }
        final int[] remaining = {countdownSeconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.state() != MatchState.COUNTDOWN) {
                cancelTask(session.id());
                return;
            }
            if (remaining[0] <= 0) {
                cancelTask(session.id());
                beginFight(session);
                return;
            }
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.sendActionBar(Component.text(String.valueOf(remaining[0]), NamedTextColor.GOLD)
                            .decorate(TextDecoration.BOLD));
                    soundService.play(player, "match-countdown-tick", 1.0f);
                }
            }
            remaining[0]--;
        }, 0L, 20L);
        tasks.put(session.id(), task);
    }

    /**
     * Confirms every participant is standing inside the arena bounds and not buried before
     * the countdown starts. Anyone misplaced is re-teleported to their team spawn. Only when
     * all re-teleports report success does {@code andThen} run; if a spawn cannot be resolved
     * (incomplete arena), the arena is released once and preparation is retried — so the
     * countdown never begins with a player outside the walls.
     */
    private void ensureParticipantsInsideArena(MatchSession session, Runnable andThen) {
        ArenaInstance instance = session.arenaInstanceId() == null
                ? null
                : arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            failMatch(session, "Arena missing before countdown");
            return;
        }
        com.rumilance.practice.util.Cuboid bounds = instance.bounds();
        Location spawnABase = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance));
        Location spawnBBase = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance));

        java.util.List<java.util.concurrent.CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                failMatch(session, "Player offline during prepare");
                return;
            }
            if (isCorrectlyPlaced(player, bounds)) {
                continue;
            }
            TeamColor color = session.teamColor(id);
            Location spawn = teamSpawnBase(session, instance, color);
            if (session.isTeamMatch()) {
                List<UUID> side = session.team(color);
                int withinTeam = side.indexOf(id);
                double offset = (withinTeam - (side.size() - 1) / 2.0) * 1.2;
                spawn = spawn.clone().add(offset, 0, 0);
                spawn.setYaw(spawn.getYaw());
                spawn.setPitch(spawn.getPitch());
            }
            teleports.add(SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(spawn)));
        }

        if (teleports.isEmpty()) {
            andThen.run();
            return;
        }
        plugin.getLogger().info("Re-pinning " + teleports.size()
                + " participant(s) into arena " + instance.id() + " before countdown (match " + session.id() + ").");
        java.util.concurrent.CompletableFuture.allOf(
                teleports.toArray(new java.util.concurrent.CompletableFuture[0])
        ).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || !allTeleportsSucceeded(teleports) || !allParticipantsPlaced(session, bounds)) {
                // A spawn could not be resolved (freshly pasted / broken arena): reset the arena
                // and retry the whole prepare once, matching the teleport-failure path.
                KitDefinition kit = kitService.get(session.kitName()).orElse(null);
                retryPrepareAfterBadArena(session, kit, instance, false);
                return;
            }
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    applySight(player, session);
                }
            }
            andThen.run();
        }));
    }

    private boolean allParticipantsPlaced(MatchSession session, com.rumilance.practice.util.Cuboid bounds) {
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !isCorrectlyPlaced(player, bounds)) {
                return false;
            }
        }
        return true;
    }

    /** Inside the arena horizontal bounds, within its vertical span, and not overlapping blocks. */
    private boolean isCorrectlyPlaced(Player player, com.rumilance.practice.util.Cuboid bounds) {
        Location at = player.getLocation();
        if (at == null || at.getWorld() == null || bounds == null) {
            return false;
        }
        if (!bounds.worldName().equals(at.getWorld().getName())) {
            return false;
        }
        if (!bounds.contains(at)) {
            return false;
        }
        return !com.rumilance.practice.util.SpawnFooting.isBuried(player);
    }

    private void beginFight(MatchSession session) {
        session.markActive();
        if (!transitionAll(session, PlayerState.FIGHTING)) {
            failMatch(session, "Could not start fight");
            return;
        }
        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
        for (UUID id : session.participants()) {
            countdownLeaveStreak.remove(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                // Clear stuck arrows / fire / absorption / cooldowns before fight-start effects.
                PlayerVitals.clearCombatState(player);
                ArenaInstance instance = session.arenaInstanceId() == null
                        ? null
                        : arenaService.get(session.arenaInstanceId()).orElse(null);
                if (instance != null) {
                    TeamColor color = session.teamColor(id);
                    Location spawn = teamSpawnBase(session, instance, color);
                    if (session.isTeamMatch()) {
                        // Keep the per-teammate line offset so a full side doesn't stack.
                        List<UUID> side = session.team(color);
                        int withinTeam = side.indexOf(id);
                        double offset = (withinTeam - (side.size() - 1) / 2.0) * 1.2;
                        spawn = spawn.clone().add(offset, 0, 0);
                    }
                    SafeTeleport.teleport(player, LocationUtil.safeTeleportLocation(spawn))
                            .whenComplete((ok, err) -> {
                                if (Boolean.TRUE.equals(ok)) {
                                    // Re-apply THIS match arena's per-player border/view only
                                    // AFTER the teleport landed, so it can never clamp the
                                    // player while they are still being moved (the source of
                                    // the "rematch snaps to a border corner" bug).
                                    applySight(player, session);
                                }
                            });
                } else {
                    applySight(player, session);
                }
                if (kit != null) {
                    applyStartEffects(player, kit);
                    runStartCommands(player, kit);
                }
                if (session.isTeamMatch()) {
                    // Team config (max HP / body scale / always-on effects). HP + scale are
                    // attribute-based and reliably reset by PlayerVitals when the fight ends.
                    session.teamConfigOf(id).apply(player);
                }
                soundService.play(player, "match-start");
                player.showTitle(Title.title(
                        title(player, "match.start-title",
                                Component.text("FIGHT!")
                                        .color(TextColor.color(0xAA55FF))
                                        .decorate(TextDecoration.BOLD)),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(200))
                ));
            }
        }
        if (session.isTeamMatch() && teamColoredArmorService != null) {
            teamColoredArmorService.scheduleRefreshMatch(session);
        }
        scheduleMatchTimeout(session);
    }

    /**
     * Applies kit-configured start potion effects with splash-potion durations and break sound.
     */
    private void applyStartEffects(Player player, KitDefinition kit) {
        if (player == null || kit == null || kit.startEffects().isEmpty()) {
            return;
        }
        boolean any = false;
        for (KitStartEffect start : kit.startEffects()) {
            String key = SplashPotionDurations.normalizeKey(start.potionEffectKey());
            if (key.isEmpty()) {
                continue;
            }
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(key));
            if (type == null) {
                continue;
            }
            int duration = SplashPotionDurations.ticks(type, start.amplifier());
            player.addPotionEffect(new PotionEffect(type, duration, start.amplifier(), false, true, true));
            any = true;
        }
        if (any) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 1.0f);
        }
    }

    /** Dispatches kit {@code startCommands} as console with {@code {player}} replaced. */
    private void runStartCommands(Player player, KitDefinition kit) {
        if (player == null || kit == null || kit.startCommands().isEmpty()) {
            return;
        }
        for (String raw : kit.startCommands()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = raw.replace("{player}", player.getName());
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void scheduleMatchTimeout(MatchSession session) {
        KitDefinition kit = kitService.get(session.kitName()).orElse(null);
        int kitTimeout = kit == null ? 0 : kit.timeoutSeconds();
        int seconds = kitTimeout > 0 ? kitTimeout : maxDurationSeconds;
        if (seconds <= 0) {
            return;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.state() == MatchState.ACTIVE) {
                for (UUID id : session.participants()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        if (messageService != null) {
                            messageService.send(player, "match.timeout-draw");
                        } else {
                            player.sendMessage(Component.text("Match timed out — draw.", NamedTextColor.YELLOW));
                        }
                    }
                }
                endMatch(session, null, true);
            }
        }, seconds * 20L);
        tasks.put(session.id(), timeout);

        // Absolute hard cap: even if no admin/kit timeout is set, a match that runs a full hour
        // is forcibly drawn. Three such forced draws for a player earn a 4-day ban (anti-stall).
        BukkitTask hard = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.state() == MatchState.ACTIVE
                    && hardTimedOut.add(session.id())) {
                for (UUID id : session.participants()) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) {
                        if (messageService != null) {
                            messageService.send(player, "match.hard-timeout-draw");
                        } else {
                            player.sendMessage(Component.text(
                                    "Match hit the 1 hour limit — forced draw.", NamedTextColor.YELLOW));
                        }
                    }
                    int strikes = hardTimeoutStrikes.merge(id, 1, Integer::sum);
                    if (strikes >= HARD_TIMEOUT_STRIKES_BAN && chatBanService != null) {
                        Player online = Bukkit.getPlayer(id);
                        if (online == null || !online.hasPermission("rumilance.punishment.bypass")) {
                            chatBanService.issueWithNotice(id, null, "CHATBAN",
                                    "Repeated 1-hour forced draws (" + strikes + ")", HARD_TIMEOUT_BAN);
                        }
                    }
                }
                endMatch(session, null, true);
            }
        }, HARD_TIMEOUT_SECONDS * 20L);
        hardTimeoutTasks.put(session.id(), hard);
    }

    public void handleLethal(MatchSession session, UUID victimId, UUID attackerId) {
        if (session.state() != MatchState.ACTIVE || session.isResultApplied()) {
            return;
        }
        // Dedupe: a fighter can only be processed once per match. Without this, two lethal damage
        // events for the same player (e.g. a pearl fall and a sword hit resolving in the same tick,
        // before the 1-tick-deferred solo ruling ends the match) would double-count the kill, play
        // the death sound twice and post two kill-feed lines.
        java.util.Set<UUID> resolved = resolvedLethalByMatch
                .computeIfAbsent(session.id(), k -> ConcurrentHashMap.newKeySet());
        if (!resolved.add(victimId)) {
            return;
        }
        // Snapshot victim inventory before spectator / lobby wipe so kill-feed End Inv is accurate.
        Player victimPre = Bukkit.getPlayer(victimId);
        if (victimPre != null) {
            byte[] inv = serializePlayerInventory(victimPre);
            session.rememberEndInventory(victimId, inv);
            if (inventoryStore != null) {
                inventoryStore.captureIfAbsent(
                        session.id(), victimId, StatsService.nameOf(victimId), inv,
                        teamColorName(session, victimId));
            }
        }
        // Count a kill for the attacker (skip self-inflicted / environmental deaths).
        if (attackerId != null && !attackerId.equals(victimId)) {
            session.addKill(attackerId);
            recordMonthlyKillDeath(attackerId, victimId);
            combatTracker.forParticipant(session.id(), attackerId).hits.incrementAndGet();
            if (titleService != null) {
                Player killer = Bukkit.getPlayer(attackerId);
                if (killer != null) {
                    titleService.showKillTitle(killer);
                }
            }
        }
        Player victim = Bukkit.getPlayer(victimId);
        if (victim != null) {
            soundService.play(victim, "death");
        }

        if (session.isTeamMatch()) {
            handleTeamLethal(session, victimId, attackerId);
            return;
        }

        // A duel is a draw ONLY when both fighters die on the exact same tick (truly
        // simultaneous). Damage events are processed one at a time, so defer the final ruling by
        // one tick: if the opponent also goes lethal on the very same tick we score a draw,
        // otherwise the survivor wins. Any single death — an environmental / self-inflicted one
        // such as an ender-pearl collision fall, void, or own crystal — is therefore a win for
        // the surviving opponent, never a draw.
        int now = Bukkit.getCurrentTick();
        java.util.Set<UUID> lethalSet = lethalPlayersByMatch.computeIfAbsent(session.id(), k -> ConcurrentHashMap.newKeySet());
        Map<UUID, UUID> attackerMap =
                lethalAttackerByMatch.computeIfAbsent(session.id(), k -> new ConcurrentHashMap<>());
        Integer lastTick = lastLethalTickByMatch.get(session.id());
        if (lastTick == null || lastTick != now) {
            lethalSet.clear();
            attackerMap.clear();
            lastLethalTickByMatch.put(session.id(), now);
        }
        lethalSet.add(victimId);
        // Normalise the attacker: a self-inflicted blast (owner == victim) or environmental death
        // records null so the draw check below cannot mistake a suicide for a kill dealt by the
        // opponent.
        UUID killerId = (attackerId != null && !attackerId.equals(victimId)
                && session.isParticipant(attackerId)) ? attackerId : null;
        attackerMap.put(victimId, killerId);
        final UUID lethalAttacker = attackerId;
        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveSoloOutcome(session, victimId, lethalAttacker, now), 1L);
    }

    private void resolveSoloOutcome(MatchSession session, UUID victimId, UUID attackerId, int lethalTick) {
        if (session.state() != MatchState.ACTIVE || session.isResultApplied()) {
            return;
        }
        java.util.Set<UUID> lethalSet = lethalPlayersByMatch.getOrDefault(session.id(), java.util.Set.of());
        java.util.Map<UUID, UUID> attackerMap =
                lethalAttackerByMatch.getOrDefault(session.id(), java.util.Map.of());
        boolean sameTick = lastLethalTickByMatch.getOrDefault(session.id(), -1) == lethalTick;
        // A draw requires a TRUE mutual kill: BOTH participants went lethal on the very same tick
        // AND each was killed by the OTHER player (recorded attacker == their opponent). A
        // self-inflicted or environmental death (own crystal, pearl fall, void, anchor misfire)
        // must never be a draw even if it lands in the same tick — the survivor wins.
        boolean bothLethal = sameTick
                && session.participants().size() == 2
                && lethalSet.containsAll(session.participants());
        boolean draw = bothLethal && session.participants().stream().allMatch(id -> {
            UUID killer = attackerMap.get(id);
            return killer != null && !killer.equals(id) && session.isParticipant(killer);
        });
        UUID winner;
        if (draw) {
            winner = null;
        } else if (attackerId != null && !attackerId.equals(victimId) && session.isParticipant(attackerId)) {
            // Killed by the opponent.
            winner = attackerId;
        } else {
            // Self-inflicted / environmental death, or killed by a non-participant: the
            // surviving opponent wins.
            winner = session.opponentOf(victimId);
        }
        broadcastKillFeed(session, winner, victimId, draw);
        endMatch(session, winner, draw);
    }

    private void broadcastKillFeed(MatchSession session, UUID winnerId, UUID loserId, boolean draw) {
        if (draw || winnerId == null || loserId == null) {
            return;
        }
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        if (winner == null || loser == null) {
            return;
        }
        KillFeed.broadcast(winner, loser, session.teamColor(winnerId), session.id());
    }

    private void handleTeamLethal(MatchSession session, UUID victimId, UUID attackerId) {
        session.markEliminated(victimId);
        Player downed = Bukkit.getPlayer(victimId);
        if (downed != null) {
            // Freeze end-inventory at death so mid-fight eliminations are not lost / emptied later.
            captureParticipantInventory(session, downed);
            // Party Fight death: everything the player was carrying drops on the spot — but any
            // personalisation is stripped first: custom (anvil) names are removed and applied
            // smithing trims are reset, so dropped loot is always plain vanilla gear.
            spawnStrippedDrops(downed);
            downed.setGameMode(org.bukkit.GameMode.SPECTATOR);
            downed.sendActionBar(Component.text("You were eliminated!", NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
            if (spectatorService != null) {
                spectatorService.hideInWorld(downed);
            }
        }
        Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
        if (attacker != null && downed != null) {
            KillFeed.broadcast(attacker, downed, session.teamColor(attackerId), session.id());
        }
        // Tell EVERYONE in the party fight who just went down (both teams + spectators), so the
        // surviving teammates know they are down a player.
        broadcastElimination(session, victimId, attackerId);
        // A fighter counts as alive only when they are online, NOT on the elimination
        // ledger, and not parked in spectator mode. The ledger is authoritative: a limbo
        // death (killing blow swallowed right after a totem pop) can leave a player in
        // SURVIVAL game mode while effectively out — without the ledger their team never
        // counts as wiped and the party fight never ends. Works for 2..7 teams alike.
        List<TeamColor> aliveTeams = new ArrayList<>();
        for (TeamColor color : session.distinctTeamColors()) {
            boolean teamAlive = false;
            for (UUID member : session.team(color)) {
                if (session.isEliminated(member)) {
                    continue;
                }
                Player mate = Bukkit.getPlayer(member);
                if (mate != null && mate.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    teamAlive = true;
                    break;
                }
            }
            if (teamAlive) {
                aliveTeams.add(color);
            }
        }
        if (aliveTeams.size() == 1) {
            TeamColor winnerTeam = aliveTeams.get(0);
            UUID winnerUuid = session.team(winnerTeam).stream()
                    .filter(u -> {
                        Player p = Bukkit.getPlayer(u);
                        return p != null && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
                    })
                    .findFirst()
                    .orElse(session.team(winnerTeam).get(0));
            // endMatch() records the winner and (via MatchSession.end) the winning team colour.
            // Do NOT pre-set ENDING here: endMatch's idempotency guard would then no-op.
            endMatch(session, winnerUuid, false);
        } else if (aliveTeams.isEmpty()) {
            // Last fighters of every remaining team went down together: a draw.
            endMatch(session, null, true);
        }
    }

    /**
     * Spawns the player's entire carried load as ground items at their position, with custom
     * display names and applied smithing trims stripped (party-fight death loot must always
     * be plain vanilla gear).
     */
    private void spawnStrippedDrops(Player player) {
        org.bukkit.World world = player.getWorld();
        org.bukkit.Location at = player.getLocation();
        java.util.List<org.bukkit.inventory.ItemStack> loadout = new java.util.ArrayList<>();
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                loadout.add(item.clone());
            }
        }
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                loadout.add(item.clone());
            }
        }
        org.bukkit.inventory.ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            loadout.add(offhand.clone());
        }
        for (org.bukkit.inventory.ItemStack item : loadout) {
            stripPersonalisation(item);
            world.dropItemNaturally(at, item);
        }
    }

    /**
     * Removes personalisation from a dropped item: custom (anvil) display names, applied
     * smithing trims, and shield custom-model artwork all revert to vanilla. Durability and
     * enchantments stay exactly as they were.
     */
    public static void stripPersonalisation(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(null); // strip any anvil / custom rename
        if (meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta) {
            armorMeta.setTrim(null); // strip the applied smithing trim
        }
        if (item.getType() == org.bukkit.Material.SHIELD && meta.hasCustomModelData()) {
            meta.setCustomModelData(null); // custom shield artwork never survives as a drop
        }
        item.setItemMeta(meta);
    }

    /** Sends an "X was eliminated (by Y)" line to every participant and spectator of the match. */
    private void broadcastElimination(MatchSession session, UUID victimId, UUID attackerId) {
        String victimName = StatsService.nameOf(victimId);
        TeamColor victimColor = session.teamColor(victimId);
        NamedTextColor victimTextColor = victimColor == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.AQUA;
        Component line;
        if (attackerId != null && !attackerId.equals(victimId)) {
            String attackerName = StatsService.nameOf(attackerId);
            TeamColor attackerColor = session.teamColor(attackerId);
            NamedTextColor attackerTextColor = attackerColor == TeamColor.RED ? NamedTextColor.RED : NamedTextColor.AQUA;
            line = Component.text("✖ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(victimName, victimTextColor).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" was eliminated by ", NamedTextColor.GRAY))
                    .append(Component.text(attackerName, attackerTextColor).decoration(TextDecoration.BOLD, true));
        } else {
            line = Component.text("✖ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(victimName, victimTextColor).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" was eliminated", NamedTextColor.GRAY));
        }
        java.util.Set<UUID> recipients = new java.util.HashSet<>(session.participants());
        if (spectatorService != null) {
            recipients.addAll(spectatorService.spectatorsWatching(session.id()));
        }
        for (UUID id : recipients) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                p.sendMessage(line);
                soundService.play(p, "match-end-anvil");
            }
        }
    }

    /** @return the combat stats for a finished match, or empty if the match has been cleaned up. */
    public Optional<MatchCombatTracker.CombatStats> combatStats(UUID matchId, UUID playerId) {
        return combatTracker.matchStats(matchId).map(map -> map.get(playerId));
    }

    /**
     * Operator helper: why this player cannot be forced into a match right now, or {@code null}
     * when they are free. Feeds the {@code /forcematch} rejection messages.
     */
    public String busyReason(UUID playerId) {
        if (registry.byPlayer(playerId).isPresent() || registry.isPlayerInMatch(playerId)) {
            return "in-match";
        }
        if (inParty(playerId)) {
            return "in-party";
        }
        if (isBusyForSoloDuel(playerId)) {
            return "busy";
        }
        return null;
    }

    /**
     * Operator force-end: finishes the target's current match as a DRAW and runs the normal
     * end flow (screens, lobby return, cleanup). Works from a participant or a spectator of
     * the match.
     *
     * @return {@code false} when the player is not in / watching a live match.
     */
    public boolean forceEndMatch(UUID playerId) {
        MatchSession session = registry.byPlayer(playerId).orElse(null);
        if (session == null && spectatorService != null) {
            session = spectatorService.matchOf(playerId)
                    .flatMap(registry::get).orElse(null);
        }
        if (session == null) {
            return false;
        }
        MatchState state = session.state();
        if (state == MatchState.CLOSED || state == MatchState.ENDING) {
            return false;
        }
        endMatch(session, null, true);
        return true;
    }

    public void handleDisconnect(UUID playerId) {
        Optional<MatchSession> opt = registry.byPlayer(playerId);
        if (opt.isEmpty()) {
            return;
        }
        MatchSession session = opt.get();
        if (session.state() != MatchState.ACTIVE) {
            if (session.state() == MatchState.COUNTDOWN
                    || session.state() == MatchState.WAITING_FOR_PLAYERS
                    || session.state() == MatchState.RESERVING_ARENA
                    || session.state() == MatchState.CREATED
                    || session.state() == MatchState.PASTING_ARENA) {
                failMatch(session, "Player disconnected before fight");
            }
            return;
        }
        if (shuttingDown || session.isShuttingDown()) {
            return;
        }
        // The match is decided by a forfeit: the opponent who is still here must NOT be
        // offered rematch items / flow (the other side is gone anyway).
        session.markDisconnectForfeit();
        if (session.isTeamMatch()) {
            // A team-battle leaver counts as an elimination: if their whole side is now
            // offline/eliminated the other side wins, otherwise the fight continues.
            handleTeamLethal(session, playerId, null);
            return;
        }
        UUID winner = session.opponentOf(playerId);
        broadcastKillFeed(session, winner, playerId, false);
        endMatch(session, winner, false);
        if (!shuttingDown && !session.isShuttingDown() && session.tryMarkDisconnectPenalty()) {
            if (chatBanService != null) {
                Player offline = Bukkit.getPlayer(playerId);
                if (offline == null || !offline.hasPermission("rumilance.punishment.bypass")) {
                    chatBanService.issueDisconnectBan(playerId, session.id());
                }
            }
            plugin.getLogger().info("Disconnect forfeit recorded for " + playerId + " in match " + session.id());
        }
    }

    /** Outcome of {@link #leaveDuringCountdown(Player)}, used by the {@code /leave} command. */
    public enum LeaveOutcome {
        /** Player is not in a match / not in the countdown phase. */
        NOT_COUNTDOWN,
        /** Match was cancelled, no result recorded. */
        CANCELLED,
        /** Match cancelled and a 3-day ChatBan issued for repeated dodging. */
        CANCELLED_AND_BANNED
    }

    /**
     * Lets a player leave a match during the pre-match countdown with no result recorded: the
     * match is cancelled, both participants return to the lobby and no Elo/stats change happens.
     * Repeated dodging is penalised - the third consecutive countdown-leave issues a 3-day
     * ChatBan. The streak resets the moment one of the player's matches reaches FIGHT.
     */
    public LeaveOutcome leaveDuringCountdown(Player player) {
        Optional<MatchSession> opt = registry.byPlayer(player.getUniqueId());
        if (opt.isEmpty()) {
            return LeaveOutcome.NOT_COUNTDOWN;
        }
        MatchSession session = opt.get();
        if (session.state() != MatchState.COUNTDOWN) {
            return LeaveOutcome.NOT_COUNTDOWN;
        }
        UUID leaverId = player.getUniqueId();
        cancelMatchNoResult(session);

        int streak = countdownLeaveStreak.merge(leaverId, 1, Integer::sum);
        for (UUID id : session.participants()) {
            if (id.equals(leaverId)) {
                continue;
            }
            Player other = Bukkit.getPlayer(id);
            if (other != null && messageService != null) {
                messageService.send(other, "match.opponent-left-countdown",
                        MessageService.tags("player", player.getName()));
            }
        }
        if (streak >= 3 && chatBanService != null) {
            String reason = messageService != null
                    ? messageService.localeService().rawMessage(messageService.resolveLocale(player), "match.leave-ban-reason")
                    : "Repeated countdown leaves";
            try {
                chatBanService.issue(leaverId, null, "CHATBAN", reason, Duration.ofDays(3));
            } catch (Exception ignored) {
                // best-effort; the leave still succeeds
            }
            return LeaveOutcome.CANCELLED_AND_BANNED;
        }
        return LeaveOutcome.CANCELLED;
    }

    private void cancelMatchNoResult(MatchSession session) {
        cancelTask(session.id());
        if (spectatorService != null) {
            spectatorService.clearMatch(session.id());
        }
        for (UUID id : session.participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (teamColoredArmorService != null) {
                    teamColoredArmorService.clearForPlayer(p);
                }
                sendHome(p);
            }
            stateManager.resetToLobby(id);
        }
        UUID arenaId = session.arenaInstanceId();
        registry.unregister(session.id());
        session.setState(MatchState.CLOSED);
        if (arenaId != null) {
            arenaService.release(arenaId);
        }
    }

    public void endMatch(MatchSession session, UUID winnerId, boolean draw) {
        if (session.state() == MatchState.ENDING || session.state() == MatchState.CLOSED
                || session.state() == MatchState.CLEANING || session.state() == MatchState.FAILED) {
            return;
        }
        cancelTask(session.id());
        // Capture end inventories before rematch items wipe them (winner + loser / both sides).
        snapshotMatchInventories(session);
        session.end(winnerId, draw);
        if (!draw && winnerId != null) {
            session.addSeriesWin(winnerId);
        }
        for (UUID id : session.participants()) {
            try {
                stateManager.transition(id, PlayerState.ENDING);
            } catch (Exception ignored) {
                stateManager.resetToLobby(id);
            }
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            // Players stay in the arena during the rematch window — clear arrows/fire/etc.
            PlayerVitals.clearCombatState(player);
            PlayerVitals.resetMaxHealth(player);
            player.getInventory().clear();
            // A fighter who died in the void keeps falling forever during the ENDING window
            // (post-match damage is cancelled, so nothing stops them). Park them just above
            // the world floor — gravity off is reverted by clearCombatState (lobby return /
            // rematch start both run it).
            if (player.getLocation().getY() < player.getWorld().getMinHeight() - 2.0) {
                org.bukkit.Location parked = player.getLocation().clone();
                parked.setY(player.getWorld().getMinHeight() + 1.0);
                player.setVelocity(new org.bukkit.util.Vector());
                player.teleport(parked);
                player.setGravity(false);
            }
            // Eliminated team players were parked in spectator mode — restore them so they
            // can see and use the rematch/report items during the ENDING window.
            if (session.isTeamMatch() && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            }
            // In a team battle everyone on the winning side sees WIN, not just the last killer.
            boolean win = !draw && winnerId != null
                    && (session.isTeamMatch()
                            ? session.teamColor(id) == session.teamColor(winnerId)
                            : winnerId.equals(id));
            Component resultTitle = title(player,
                    draw ? "match.draw-title" : (win ? "match.win-title" : "match.lose-title"),
                    Component.text(draw ? "DRAW" : (win ? "WIN" : "LOSE"))
                            .color(draw ? NamedTextColor.YELLOW : (win ? NamedTextColor.GREEN : NamedTextColor.RED))
                            .decorate(TextDecoration.BOLD));
            // Duels (party fights excluded) put the series score in the subtitle with the
            // viewer's own side on the left — addSeriesWin() above already counts this round.
            Component resultSubtitle = Component.empty();
            if (!draw && !session.isTeamMatch() && session.participants().size() == 2) {
                UUID opp = session.opponentOf(id);
                if (opp != null) {
                    resultSubtitle = Component.text(session.seriesWinsOf(id), NamedTextColor.WHITE)
                            .append(Component.text(" - ", NamedTextColor.GRAY))
                            .append(Component.text(session.seriesWinsOf(opp), NamedTextColor.WHITE));
                }
            }
            player.showTitle(Title.title(
                    resultTitle,
                    resultSubtitle,
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(400))
            ));
            // Distinct end sting by outcome: winner hears the celebratory level-up jingle,
            // loser/draw hears the heavy anvil thud. (Previously both played to both players.)
            if (win) {
                soundService.play(player, "match-end-levelup");
                if (titleService != null) {
                    titleService.showWinTitle(player);
                }
            } else {
                soundService.play(player, "match-end-anvil");
            }
            // Disconnect forfeits skip the rematch offer entirely — the opponent is gone.
            if (!session.isDisconnectForfeit()) {
                giveRematchItems(player, session);
            }
            recentMatch.put(id, session.id());
            sendEndSummary(player, session, id, win);
        }
        // Drop the recent-match pointers after 60s so /matchreport cannot open a cleaned-up match.
        // Only remove while the pointer still points at THIS match: a fast rematch / queue into a
        // new match overwrites the entry, and an unconditional remove would then clear the NEW
        // match's report pointer (making /matchreport fail for the follow-up game).
        final UUID endedMatchId = session.id();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : session.participants()) {
                recentMatch.remove(id, endedMatchId);
            }
        }, 60L * 20L);

        // Capture the movement traces for the replay archive BEFORE cleanup clears the
        // recorder's per-match data. Team fights and duels are both recorded. The arena
        // snapshot lets the replay viewer re-paste the (disposable, already air-cleared on
        // release) arena copy at its original coordinates.
        try {
            if (actionRecorder != null) {
                actionRecorder.completeMatch(session);
            }
            if (replayArchive != null && actionRecorder != null) {
                replayArchive.record(actionRecorder, session, arenaSnapshot(session));
            }
        } catch (RuntimeException ignored) {
            // Replay capture must never break match end.
        }
        recordHistory(session);

        MatchResultProcessor processor = switch (session.mode()) {
            case RANKED -> rankedResultProcessor;
            case UNRANKED, TEAM -> unrankedResultProcessor;
            case FFA -> ffaResultProcessor;
        };
        asyncExecutor.execute(() -> {
            try {
                processor.process(session, winnerId, draw);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to process match result " + session.id(), e);
            }
        });

        BukkitTask rematchTimeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.bothRematchRequested()) {
                return;
            }
            returnPlayersToLobby(session);
        }, rematchSeconds * 20L);
        tasks.put(session.id(), rematchTimeout);
    }

    /**
     * Captures the arena placement of {@code session} (if still resolvable) so the replay
     * viewer can re-paste disposable arena copies that have since been cleared to air.
     */
    private com.rumilance.practice.replay.ReplayArenaSnapshot arenaSnapshot(MatchSession session) {
        if (session == null || session.arenaInstanceId() == null || arenaService == null) {
            return null;
        }
        com.rumilance.practice.model.ArenaInstance instance =
                arenaService.get(session.arenaInstanceId()).orElse(null);
        if (instance == null) {
            return null;
        }
        return new com.rumilance.practice.replay.ReplayArenaSnapshot(
                instance.template().name(),
                instance.template().world(),
                instance.template().schematicPath(),
                instance.minX(), instance.minY(), instance.minZ(),
                instance.maxX(), instance.maxY(), instance.maxZ(),
                instance.isRelocated());
    }

    /** Persists the finished match for the Battle-menu history (outcome, score, participants). */
    private void recordHistory(MatchSession session) {
        if (historyStore == null || session == null) {
            return;
        }
        try {
            boolean draw = session.isDraw();
            UUID winner = session.winner();
            com.rumilance.practice.state.TeamColor winningTeam = session.winningTeam();
            boolean teamMatch = session.isTeamMatch();
            List<com.rumilance.practice.match.history.MatchHistoryStore.Participant> participants =
                    new ArrayList<>();
            for (UUID id : session.participants()) {
                // teamColor() defaults to RED in non-team sessions; only real teams carry one.
                com.rumilance.practice.state.TeamColor color = teamMatch ? session.teamColor(id) : null;
                boolean win = !draw && (winner != null
                        ? winner.equals(id)
                        : winningTeam != null && color == winningTeam);
                participants.add(new com.rumilance.practice.match.history.MatchHistoryStore.Participant(
                        id, StatsService.nameOf(id),
                        color == null ? null : color.name(),
                        session.killsOf(id), win));
            }
            long durationMs = 0L;
            if (session.startedAt() != null && session.endedAt() != null) {
                durationMs = Math.max(0L,
                        java.time.Duration.between(session.startedAt(), session.endedAt()).toMillis());
            }
            historyStore.record(new com.rumilance.practice.match.history.MatchHistoryStore.Entry(
                    session.id(), session.mode().name(), session.kitName(),
                    System.currentTimeMillis(), durationMs, draw, participants));
        } catch (RuntimeException ignored) {
            // History is a convenience feature; it must never break match end.
        }
    }

    public void requestRematch(Player player) {
        registry.byPlayer(player.getUniqueId()).ifPresent(session -> {
            if (session.state() != MatchState.ENDING) {
                return;
            }
            if (session.rematchStarting()) {
                return;
            }
            if (session.isDisconnectForfeit()) {
                // A forfeit win never offers a rematch: the loser left the server.
                return;
            }
            if (session.isTeamMatch()) {
                Team team = teamService == null
                        ? null
                        : teamService.teamOf(player.getUniqueId()).orElse(null);
                if (team == null || !team.isOwner(player.getUniqueId())) {
                    return;
                }
            }
            // Avoid racing into PREPARING while cleanup / rematch is already underway.
            PlayerState selfState = stateManager.getState(player.getUniqueId());
            if (selfState == PlayerState.PREPARING_MATCH || selfState == PlayerState.COUNTDOWN
                    || selfState == PlayerState.FIGHTING) {
                return;
            }
            session.setRematchRequested(player.getUniqueId(), true);
            // Notify the other side: the 1v1 opponent, or every other team's members in a
            // team battle (any number of teams).
            List<UUID> others;
            if (session.isTeamMatch()) {
                TeamColor mine = session.teamColor(player.getUniqueId());
                others = session.participants().stream()
                        .filter(otherId -> session.teamColor(otherId) != mine)
                        .toList();
            } else {
                others = session.opponentOf(player.getUniqueId()) == null
                        ? List.of() : List.of(session.opponentOf(player.getUniqueId()));
            }
            for (UUID otherId : others) {
                Player opponent = Bukkit.getPlayer(otherId);
                if (opponent == null) {
                    continue;
                }
                if (messageService != null) {
                    messageService.send(opponent, "match.rematch-requested",
                            MessageService.tags("player", player.getName()));
                } else {
                    opponent.sendMessage(Component.text(player.getName() + " wants a rematch!")
                            .color(NamedTextColor.YELLOW));
                }
            }
            if (session.bothRematchRequested()) {
                boolean teamMatch = session.isTeamMatch();
                // The session roster is FROZEN at match start; during the ENDING window members
                // can leave or get kicked from their party (or disconnect). Drafting them into
                // the rematch would teleport them against their will, and offline members make
                // startTeamMatch reject AFTER the old session is cleaned up — stranding everyone
                // in ENDING state with no match. Filter to online, still-in-party members.
                List<List<UUID>> rosters = new ArrayList<>();
                if (teamMatch) {
                    for (TeamColor color : session.distinctTeamColors()) {
                        rosters.add(currentTeamRoster(session.team(color)));
                    }
                }
                if (teamMatch && (rosters.size() < 2
                        || rosters.stream().anyMatch(List::isEmpty))) {
                    for (UUID id : session.participants()) {
                        Player member = Bukkit.getPlayer(id);
                        if (member != null && messageService != null) {
                            messageService.send(member, "match.could-not-start",
                                    MessageService.tags("reason", "a team lost its members"));
                        }
                    }
                    returnPlayersToLobby(session);
                    return;
                }
                if (!session.tryBeginRematch()) {
                    return;
                }
                cancelTask(session.id());
                clearRematchItems(session);
                // Clear combat leftovers while players remain in the arena before the next duel.
                for (UUID id : session.participants()) {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        PlayerVitals.clearCombatState(online);
                    }
                }
                UUID a = session.participants().get(0);
                UUID b = session.participants().get(1);
                String kit = session.kitName();
                MatchMode mode = session.mode();
                int bestOf = session.bestOf();
                Map<UUID, Integer> carrySeries = session.seriesWinsSnapshot();
                String preferredArena = session.preferredArenaName();
                UUID carryArena = session.arenaInstanceId();
                boolean friendlyFire = session.friendlyFire();
                // Spectators follow the rematch: capture them before cleanup detaches the old
                // match id, and register the hand-off BEFORE starting the new match — with a
                // carried arena the countdown can begin synchronously inside startDuel.
                Set<UUID> watching = spectatorService == null
                        ? Set.of() : spectatorService.spectatorsWatching(session.id());
                List<UUID> carried = new ArrayList<>();
                for (List<UUID> roster : rosters) {
                    carried.addAll(roster);
                }
                if (carried.isEmpty()) {
                    carried = List.of(a, b);
                }
                Map<TeamColor, String> carryTeamKits = session.teamKitsSnapshot();
                Map<TeamColor, com.rumilance.practice.team.TeamConfig> carryTeamConfigs =
                        session.teamConfigsSnapshot();
                com.rumilance.practice.team.OriginalKitRef carryOriginalKit = session.originalKitRef();
                if (spectatorService != null && !watching.isEmpty()) {
                    spectatorService.scheduleCarry(carried, watching);
                }
                cleanupSession(session, false, !watching.isEmpty());
                if (teamMatch) {
                    startTeamMatch(rosters, kit, mode, bestOf, preferredArena,
                            friendlyFire, carrySeries, carryArena, carryTeamKits, carryTeamConfigs,
                            carryOriginalKit);
                } else {
                    startDuel(a, b, kit, mode, bestOf, carrySeries, preferredArena, carryArena);
                }
            }
        });
    }

    /**
     * Filters a frozen match roster down to players who can legitimately rematch right now:
     * online AND still a member of their party. Members who left / were kicked during the
     * ENDING window must never be pulled into the follow-up fight.
     */
    private List<UUID> currentTeamRoster(List<UUID> frozenRoster) {
        List<UUID> current = new ArrayList<>();
        for (UUID id : frozenRoster) {
            if (Bukkit.getPlayer(id) == null) {
                continue;
            }
            if (teamService != null
                    && teamService.teamOf(id).map(team -> team.members().contains(id)).orElse(false) == false) {
                continue;
            }
            current.add(id);
        }
        return current;
    }

    private void clearRematchItems(MatchSession session) {
        for (UUID id : session.participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
                ItemStack stack = p.getInventory().getItem(slot);
                if (stack == null || !stack.hasItemMeta()) {
                    continue;
                }
                var pdc = stack.getItemMeta().getPersistentDataContainer();
                if (pdc.has(ItemKeys.rematch(), PersistentDataType.BYTE)
                        || pdc.has(ItemKeys.returnLobby(), PersistentDataType.BYTE)
                        || pdc.has(ItemKeys.matchReport(), PersistentDataType.BYTE)) {
                    p.getInventory().setItem(slot, null);
                }
            }
        }
    }

    /** Opens the match report for the player's most recent (still cached) match, if any. */
    public void openMatchReport(Player player) {
        if (matchReportOpener == null) {
            return;
        }
        UUID matchId = recentMatch.get(player.getUniqueId());
        if (matchId == null) {
            player.sendMessage(Component.text("No recent match report available.", NamedTextColor.RED));
            return;
        }
        if (registry.get(matchId).isEmpty()) {
            recentMatch.remove(player.getUniqueId());
            player.sendMessage(Component.text("That match report is no longer available.", NamedTextColor.RED));
            return;
        }
        matchReportOpener.accept(player);
    }

    public void returnToLobby(Player player) {
        registry.byPlayer(player.getUniqueId()).ifPresent(session -> {
            session.clearRematchRequests();
            returnPlayersToLobby(session);
        });
    }

    private void sendBothToLobby(UUID playerA, UUID playerB) {
        for (UUID id : List.of(playerA, playerB)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                sendHome(player);
            }
            stateManager.resetToLobby(id);
        }
    }

    private void returnPlayersToLobby(MatchSession session) {
        String kit = session.kitName();
        MatchMode mode = session.mode();
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                if (replayService != null && replayService.isReplaying(id)) {
                    // The player is watching this match's replay in creative flight at the
                    // arena: do NOT teleport them home or wipe the control hotbar. The replay
                    // viewer returns them to the lobby on stop. State is still reset so
                    // queue/match guards behave afterwards.
                    stateManager.resetToLobby(id);
                    continue;
                }
                if (teamColoredArmorService != null) {
                    teamColoredArmorService.clearForPlayer(player);
                }
                MatchTeamVisuals.clear(player.getScoreboard());
                if (tabVisibilityService != null) {
                    tabVisibilityService.showAll(player);
                }
                stateManager.resetToLobby(id);
                sendHome(player);
            } else {
                stateManager.resetToLobby(id);
            }
        }
        cleanupSession(session, true);
        // Keep combat stats around a little longer so players who open the report book after the
        // 5s rematch window still see their numbers. The report GUI itself degrades gracefully if
        // the stats have been cleared.
        UUID matchId = session.id();
        // cleanupSession already cleared the tracker when releaseArena=true; schedule a safety net
        // in case cleanup was called with releaseArena=false (rematch path) so stats do not leak.
        Bukkit.getScheduler().runTaskLater(plugin, () -> combatTracker.clear(matchId), 60L * 20L);
        if (queueCoordinator != null && settingsService != null && mode != MatchMode.FFA && !session.isTeamMatch()) {
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) {
                    continue;
                }
                if (replayService != null && replayService.isReplaying(id)) {
                    // Never yank a replay viewer into a fresh queue mid-playback.
                    continue;
                }
                PlayerSettings settings = settingsService.get(id);
                if (settings.autoRequeue()) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> queueCoordinator.join(player, kit, mode), 10L);
                }
            }
        }
    }

    private void snapshotMatchInventories(MatchSession session) {
        if (inventoryStore == null || session == null || session.participants().isEmpty()) {
            return;
        }
        // Death path already captured eliminated teammates; save anyone still missing (alive).
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            captureParticipantInventory(session, player);
        }
        inventoryStore.finalizeMatch(session.id(), System.currentTimeMillis());
    }

    private void captureParticipantInventory(MatchSession session, Player player) {
        if (inventoryStore == null || session == null || player == null) {
            return;
        }
        inventoryStore.captureIfAbsent(
                session.id(),
                player.getUniqueId(),
                StatsService.nameOf(player.getUniqueId()),
                serializePlayerInventory(player),
                teamColorName(session, player.getUniqueId())
        );
    }

    private static String teamColorName(MatchSession session, UUID playerId) {
        // teamColor() defaults to RED for non-team sessions — only team fights carry a real
        // colour worth persisting.
        if (session == null || !session.isTeamMatch()) {
            return null;
        }
        TeamColor color = session.teamColor(playerId);
        return color == null ? null : color.name();
    }

    private static byte[] serializePlayerInventory(Player player) {
        if (player == null) {
            return ItemSerializer.serialize(new ItemStack[41]);
        }
        ItemStack[] contents = new ItemStack[41];
        ItemStack[] storage = player.getInventory().getStorageContents();
        System.arraycopy(storage, 0, contents, 0, Math.min(storage.length, 36));
        ItemStack[] armor = player.getInventory().getArmorContents();
        System.arraycopy(armor, 0, contents, 36, Math.min(armor.length, 4));
        contents[40] = player.getInventory().getItemInOffHand();
        return ItemSerializer.serialize(contents);
    }

    private void failMatch(MatchSession session, String reason) {
        plugin.getLogger().warning("Match " + session.id() + " failed: " + reason);
        session.setState(MatchState.FAILED);
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                sendHome(player);
                if (messageService != null) {
                    messageService.send(player, "match.could-not-start", MessageService.tags("reason", reason));
                } else {
                    player.sendMessage(Component.text("Match could not start: " + reason, NamedTextColor.RED));
                }
            }
            stateManager.resetToLobby(id);
        }
        cleanupSession(session, true);
    }

    private void cleanupSession(MatchSession session, boolean releaseArena) {
        cleanupSession(session, releaseArena, false);
    }

    /**
     * @param preserveSpectators when true (rematch path) spectators are detached from the old
     *                           match id but left in the arena — they follow the new match via
     *                           {@link com.rumilance.practice.spectator.SpectatorService#attachCarried}.
     */
    private void cleanupSession(MatchSession session, boolean releaseArena, boolean preserveSpectators) {
        cancelTask(session.id());
        if (spectatorService != null) {
            if (preserveSpectators) {
                spectatorService.detachForRematch(session.id());
            } else {
                spectatorService.clearMatch(session.id());
            }
        }
        UUID arenaId = session.arenaInstanceId();
        registry.unregister(session.id());
        session.setState(MatchState.CLOSED);
        if (releaseArena && arenaId != null) {
            arenaService.release(arenaId);
        }
        combatTracker.clear(session.id());
        lastLethalTickByMatch.remove(session.id());
        lethalPlayersByMatch.remove(session.id());
        lethalAttackerByMatch.remove(session.id());
        resolvedLethalByMatch.remove(session.id());
        if (playerPlacedBlocks != null) {
            playerPlacedBlocks.clearScope(session.id().toString());
        }
    }

    private void giveRematchItems(Player player, MatchSession session) {
        if (session.isTeamMatch()) {
            Team team = teamService == null ? null : teamService.teamOf(player.getUniqueId()).orElse(null);
            if (team == null || !team.isOwner(player.getUniqueId())) {
                return;
            }
        }
        ItemStack rematch = new ItemStack(Material.LIME_DYE);
        ItemMeta rematchMeta = rematch.getItemMeta();
        rematchMeta.displayName(Component.text("Rematch", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        rematchMeta.getPersistentDataContainer().set(ItemKeys.rematch(), PersistentDataType.BYTE, (byte) 1);
        rematch.setItemMeta(rematchMeta);
        player.getInventory().setItem(3, rematch);

        // Only hand the report book to players who opted in via /setting; everyone else can open
        // the same GUI with /matchreport if they want the numbers.
        if (settingsService != null && settingsService.get(player).showMatchReport()) {
            ItemStack report = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta reportMeta = report.getItemMeta();
            reportMeta.displayName(Component.text("Match Report", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            reportMeta.getPersistentDataContainer().set(ItemKeys.matchReport(), PersistentDataType.BYTE, (byte) 1);
            report.setItemMeta(reportMeta);
            player.getInventory().setItem(4, report);
        }

        ItemStack lobby = new ItemStack(Material.RED_DYE);
        ItemMeta lobbyMeta = lobby.getItemMeta();
        lobbyMeta.displayName(Component.text("Return to Lobby", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lobbyMeta.getPersistentDataContainer().set(ItemKeys.returnLobby(), PersistentDataType.BYTE, (byte) 1);
        lobby.setItemMeta(lobbyMeta);
        player.getInventory().setItem(5, lobby);
    }

    /**
     * Sends a one-line combat summary after a match (DMG dealt / taken / hits), so players who
     * don't want the GUI still get the headline numbers without any extra clicks.
     */
    private void sendEndSummary(Player player, MatchSession session, UUID playerId, boolean won) {
        MatchCombatTracker.CombatStats stats = combatTracker
                .matchStats(session.id()).map(m -> m.get(playerId)).orElse(null);
        Component outcome = won
                ? Component.text("WIN", NamedTextColor.GREEN)
                : (session.isDraw() ? Component.text("DRAW", NamedTextColor.YELLOW)
                        : Component.text("LOSS", NamedTextColor.RED));
        Component body = stats == null
                ? Component.text(" (no combat stats)", NamedTextColor.GRAY)
                : Component.text("  DMG " + stats.damageDealt() + " / " + stats.damageTaken()
                        + "  •  Hits " + stats.hits() + "  •  Best Combo " + stats.bestCombo(),
                        NamedTextColor.AQUA);
        player.sendMessage(Component.text("Match: ", NamedTextColor.GRAY)
                .append(outcome)
                .append(body)
                .append(Component.text("  "))
                .append(com.rumilance.practice.chat.ChatButtons.subtle(
                        "Report", "/matchreport", "Open the full match report"))
                .decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Resolves a locale-localised title component for {@code player}, falling back to
     * {@code fallback} when no {@link MessageService} is wired (e.g. in tests).
     */
    private Component title(Player player, String key, Component fallback) {
        if (messageService == null) {
            return fallback;
        }
        try {
            return messageService.render(messageService.resolveLocale(player), key);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean transitionAll(MatchSession session, PlayerState target) {
        boolean ok = true;
        for (UUID id : session.participants()) {
            if (!tryTransition(id, target)) {
                ok = false;
            }
        }
        return ok;
    }

    /**
     * Pulls a player out of whatever non-match activity they are in so a duel/team match can take
     * over cleanly, regardless of how the match was started (queue pairing, a duel invite accepted
     * while in FFA / spectating / a practice room / editing a kit, rematch...).
     *
     * <p>Every eviction is <strong>bookkeeping-only — never teleports</strong>. The match flow
     * performs the one and only teleport (to the arena spawn) and applies the kit / vitals / sight
     * only AFTER that teleport lands, so there is never a lobby-vs-arena teleport race and the
     * player always arrives in the arena fully kitted.</p>
     */
    private void evictFromAnyActivity(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        // Pulled into a match while editing an original kit: force-save the draft and leave the
        // room FIRST — while the inventory still holds the kit — before anything else touches it.
        try {
            if (originalKitService != null) {
                originalKitService.forceSaveAndExitForMatch(player);
            }
        } catch (RuntimeException ignored) {
        }
        PlayerState state = stateManager.getState(id);
        try {
            if (queueCoordinator != null) {
                queueCoordinator.leave(player);
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (ffaService != null && (state == PlayerState.FFA || ffaService.isInFfa(id))) {
                ffaService.leaveSilentlyForMatch(player);
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (spectatorService != null && spectatorService.isSpectating(id)) {
                spectatorService.leave(player, false);
            }
        } catch (RuntimeException ignored) {
        }
        // A pulled-out spectator may still be hidden from (or hiding) others; restore full
        // visibility before the match re-applies its own sight rules.
        try {
            if (tabVisibilityService != null) {
                tabVisibilityService.showAll(player);
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (spectatorService != null) {
                spectatorService.revealInWorld(player);
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (practiceService != null
                    && (state == PlayerState.PRACTICE_WAIT || state == PlayerState.PRACTICE_ACTIVE
                    || practiceService.isInPractice(id))) {
                practiceService.leaveSilentlyForMatch(player);
            }
        } catch (RuntimeException ignored) {
        }
        try {
            player.closeInventory();
        } catch (RuntimeException ignored) {
        }
        // Ensure the state flag is LOBBY so the later PREPARING_MATCH transition always succeeds
        // even if the activity-specific reset above missed a state.
        PlayerState now = stateManager.getState(id);
        if (now != PlayerState.LOBBY && now != PlayerState.PREPARING_MATCH) {
            stateManager.resetToLobby(id);
        }
    }

    private boolean tryTransition(UUID playerId, PlayerState target) {
        try {
            PlayerState current = stateManager.getState(playerId);
            if (current == PlayerState.IDLE) {
                stateManager.initialize(playerId);
            }
            if (stateManager.getState(playerId) != target) {
                if (PlayerStateManager.canTransition(stateManager.getState(playerId), target)) {
                    stateManager.transition(playerId, target);
                } else {
                    stateManager.resetToLobby(playerId);
                    if (PlayerStateManager.canTransition(PlayerState.LOBBY, target)) {
                        stateManager.transition(playerId, target);
                    } else {
                        return false;
                    }
                }
            }
            return stateManager.getState(playerId) == target;
        } catch (Exception e) {
            try {
                stateManager.resetToLobby(playerId);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private void cancelTask(UUID matchId) {
        BukkitTask task = tasks.remove(matchId);
        if (task != null) {
            task.cancel();
        }
        BukkitTask hard = hardTimeoutTasks.remove(matchId);
        if (hard != null) {
            hard.cancel();
        }
    }

    public void shutdown() {
        setShuttingDown(true);
        for (MatchSession session : List.copyOf(registry.all())) {
            cleanupSession(session, true);
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    sendHome(player);
                }
                stateManager.resetToLobby(id);
            }
        }
    }
}
