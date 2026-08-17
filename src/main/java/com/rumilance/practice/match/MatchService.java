package com.rumilance.practice.match;

import com.rumilance.practice.arena.ArenaService;
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
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private com.rumilance.practice.spectator.SpectatorService spectatorService;
    private com.rumilance.practice.punishment.ChatBanService chatBanService;
    private SettingsService settingsService;
    private QueueCoordinator queueCoordinator;
    private MessageService messageService;
    private com.rumilance.practice.cosmetic.TitleService titleService;
    private java.util.function.Consumer<Player> matchReportOpener;

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

    public void setChatBanService(com.rumilance.practice.punishment.ChatBanService chatBanService) {
        this.chatBanService = chatBanService;
    }

    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    public void setTitleService(com.rumilance.practice.cosmetic.TitleService titleService) {
        this.titleService = titleService;
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

    public MatchRegistry registry() {
        return registry;
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

    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode,
                          ArenaTerrain terrain, int bestOf) {
        startDuel(playerA, playerB, kitId, mode, terrain, bestOf, Map.of());
    }

    /**
     * Starts a duel. {@code carrySeriesWins} carries a rematch chain's accumulated wins into
     * the new session (empty for fresh queue/duel-request matches, so the score starts 0-0).
     */
    public void startDuel(UUID playerA, UUID playerB, String kitId, MatchMode mode,
                          ArenaTerrain terrain, int bestOf, Map<UUID, Integer> carrySeriesWins) {
        if (registry.isPlayerInMatch(playerA) || registry.isPlayerInMatch(playerB)) {
            return;
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || !kit.enabled()) {
            return;
        }

        MatchSession session = new MatchSession(
                UUID.randomUUID(), mode, kitId, List.of(playerA, playerB), null, terrain, bestOf);
        session.applySeries(carrySeriesWins);
        if (!registry.register(session)) {
            return;
        }

        duelRequestService.invalidateForPlayer(playerA);
        duelRequestService.invalidateForPlayer(playerB);
        tryTransition(playerA, PlayerState.PREPARING_MATCH);
        tryTransition(playerB, PlayerState.PREPARING_MATCH);
        session.setState(MatchState.RESERVING_ARENA);
        announceMatchFound(session);

        reserveArenaFor(kit, terrain, session.id())
                .whenComplete((opt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
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

    /**
     * Reserves the arena for {@code kit}: kits pinned to one arena ({@code /kit arena})
     * always use that template; otherwise falls back to the legacy terrain-based pick.
     */
    private java.util.concurrent.CompletableFuture<Optional<ArenaInstance>> reserveArenaFor(
            KitDefinition kit, ArenaTerrain terrain, UUID matchId) {
        if (kit.hasFixedArena()) {
            return arenaService.reserveNamed(kit.arenaName(), matchId);
        }
        return arenaService.reserve(ArenaType.DUEL, terrain == null ? kit.arenaTerrain() : terrain, matchId);
    }

    /**
     * Starts a RED-vs-BLUE team battle. Each side may hold 1..15 players and the ratio can be
     * arbitrarily uneven (e.g. 2v7). RED spawns at spawn A, BLUE at spawn B (each player gets a
     * small horizontal offset so teammates don't stack), and friendly fire is disabled. This is
     * the no-queue entry point used by the team hub GUI / {@code /team start}.
     */
    public void startTeamMatch(List<UUID> redTeam, List<UUID> blueTeam, String kitId,
                               MatchMode mode, ArenaTerrain terrain, int bestOf) {
        if (redTeam == null || blueTeam == null
                || redTeam.isEmpty() || blueTeam.isEmpty()
                || redTeam.size() > MatchSession.MAX_SIDE_SIZE
                || blueTeam.size() > MatchSession.MAX_SIDE_SIZE) {
            return;
        }
        List<UUID> all = new ArrayList<>();
        all.addAll(redTeam);
        all.addAll(blueTeam);
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

        MatchSession session = new MatchSession(
                UUID.randomUUID(), mode, kitId, redTeam, blueTeam, null, terrain, bestOf);
        if (!registry.register(session)) {
            return;
        }
        for (UUID id : all) {
            duelRequestService.invalidateForPlayer(id);
            tryTransition(id, PlayerState.PREPARING_MATCH);
        }
        session.setState(MatchState.RESERVING_ARENA);
        announceMatchFound(session);

        reserveArenaFor(kit, terrain, session.id())
                .whenComplete((opt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
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
        Location spawnA = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance));
        Location spawnB = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance));
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                failMatch(session, "Player offline during prepare");
                return;
            }
            // RED spawns at A, BLUE at B. Spread teammates on a line centred on the spawn
            // point (1.2 blocks apart) so even a 15-player side doesn't stack in one block.
            TeamColor color = session.teamColor(id);
            Location base = color == TeamColor.RED ? spawnA : spawnB;
            List<UUID> side = session.team(color);
            int withinTeam = side.indexOf(id);
            double offset = (withinTeam - (side.size() - 1) / 2.0) * 1.2;
            Location dest = base.clone().add(offset, 0, 0);
            dest.setYaw(base.getYaw());
            dest.setPitch(base.getPitch());
            player.teleport(LocationUtil.safeTeleportLocation(dest, player));
        }
        // Teleport happened above; give clients a full second to settle/render the arena
        // (possibly a far-away disposable copy) before kits and the countdown begin.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : session.participants()) {
                if (Bukkit.getPlayer(id) == null) {
                    failMatch(session, "Player offline during prepare");
                    return;
                }
            }
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    applyKit(player, kit);
                    applySight(player, session);
                }
            }
            startCountdown(session);
        }, 20L);
    }

    private void teleportAndPrepare(MatchSession session, KitDefinition kit, ArenaInstance instance) {
        Player p1 = Bukkit.getPlayer(session.participants().get(0));
        Player p2 = Bukkit.getPlayer(session.participants().get(1));
        if (p1 == null || p2 == null) {
            failMatch(session, "Player offline during prepare");
            return;
        }
        Location spawnA = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance), p1);
        Location spawnB = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance), p2);
        // Brief beat (0.5s) so players register the MATCH FOUND notification before the teleport.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(p1.getUniqueId()) == null || Bukkit.getPlayer(p2.getUniqueId()) == null) {
                failMatch(session, "Player offline during prepare");
                return;
            }
            // Wait for both teleports before countdown so clients never render a border-outside frame.
            var futureA = p1.teleportAsync(spawnA);
            var futureB = p2.teleportAsync(spawnB);
            futureA.thenCombine(futureB, (a, b) -> a && b).whenComplete((ok, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null || !Boolean.TRUE.equals(ok)) {
                            // Fallback sync teleport if async failed.
                            p1.teleport(spawnA);
                            p2.teleport(spawnB);
                        }
                        if (Bukkit.getPlayer(p1.getUniqueId()) == null || Bukkit.getPlayer(p2.getUniqueId()) == null) {
                            failMatch(session, "Player offline during prepare");
                            return;
                        }
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
                    Component.text("----------------------------", NamedTextColor.DARK_GRAY));
            player.showTitle(Title.title(
                    main, sub,
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300))));
            soundService.play(player, "match-found");
        }
    }

    private void applyKit(Player player, KitDefinition kit) {
        layoutCache.loadSyncIfAbsent(player.getUniqueId(), kit.name());
        ItemStack[] layout = layoutCache.get(player.getUniqueId(), kit.name()).orElse(null);
        kitService.apply(player, kit, layout);
    }

    private void startCountdown(MatchSession session) {
        session.setState(MatchState.COUNTDOWN);
        for (UUID id : session.participants()) {
            tryTransition(id, PlayerState.COUNTDOWN);
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

    private void beginFight(MatchSession session) {
        session.markActive();
        for (UUID id : session.participants()) {
            countdownLeaveStreak.remove(id);
            tryTransition(id, PlayerState.FIGHTING);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                ArenaInstance instance = session.arenaInstanceId() == null
                        ? null
                        : arenaService.get(session.arenaInstanceId()).orElse(null);
                if (instance != null) {
                    TeamColor color = session.teamColor(id);
                    Location spawn = color == TeamColor.RED
                            ? arenaService.spawnA(instance)
                            : arenaService.spawnB(instance);
                    if (session.isTeamMatch()) {
                        // Keep the per-teammate line offset so a full side doesn't stack.
                        List<UUID> side = session.team(color);
                        int withinTeam = side.indexOf(id);
                        double offset = (withinTeam - (side.size() - 1) / 2.0) * 1.2;
                        spawn = spawn.clone().add(offset, 0, 0);
                    }
                    player.teleport(LocationUtil.safeTeleportLocation(spawn, player));
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
        scheduleMatchTimeout(session);
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
    }

    public void handleLethal(MatchSession session, UUID victimId, UUID attackerId) {
        if (session.state() != MatchState.ACTIVE || session.isResultApplied()) {
            return;
        }
        // Count a kill for the attacker (skip self-inflicted / environmental deaths).
        if (attackerId != null && !attackerId.equals(victimId)) {
            session.addKill(attackerId);
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

        boolean draw = attackerId != null && victimId.equals(attackerId);
        UUID winner = draw ? null : attackerId;
        if (winner == null && !draw) {
            winner = session.opponentOf(victimId);
        }
        endMatch(session, winner, draw);
    }

    private void handleTeamLethal(MatchSession session, UUID victimId, UUID attackerId) {
        Player downed = Bukkit.getPlayer(victimId);
        if (downed != null) {
            downed.setGameMode(org.bukkit.GameMode.SPECTATOR);
            downed.sendActionBar(Component.text("You were eliminated!", NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
        }
        TeamColor victimTeam = session.teamColor(victimId);
        boolean teamAlive = false;
        for (UUID member : session.team(victimTeam)) {
            if (member.equals(victimId)) {
                continue;
            }
            Player mate = Bukkit.getPlayer(member);
            if (mate != null && mate.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                teamAlive = true;
                break;
            }
        }
        if (!teamAlive) {
            TeamColor winnerTeam = victimTeam.opposite();
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
        }
    }

    /** @return the combat stats for a finished match, or empty if the match has been cleaned up. */
    public Optional<MatchCombatTracker.CombatStats> combatStats(UUID matchId, UUID playerId) {
        return combatTracker.matchStats(matchId).map(map -> map.get(playerId));
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
        if (session.isTeamMatch()) {
            // A team-battle leaver counts as an elimination: if their whole side is now
            // offline/eliminated the other side wins, otherwise the fight continues.
            handleTeamLethal(session, playerId, null);
            return;
        }
        UUID winner = session.opponentOf(playerId);
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
        UUID opponentId = session.opponentOf(leaverId);
        cancelMatchNoResult(session);

        int streak = countdownLeaveStreak.merge(leaverId, 1, Integer::sum);
        Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);
        if (opponent != null && messageService != null) {
            messageService.send(opponent, "match.opponent-left-countdown");
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
                lobbyService.sendToLobby(p);
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
            player.getInventory().clear();
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
            player.showTitle(Title.title(
                    resultTitle,
                    Component.empty(),
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
            giveRematchItems(player);
            recentMatch.put(id, session.id());
            sendEndSummary(player, session, id, win);
        }
        // Drop the recent-match pointers after 60s so /matchreport cannot open a cleaned-up match.
        UUID matchIdForCleanup = session.id();
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> session.participants().forEach(recentMatch::remove), 60L * 20L);

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

    public void requestRematch(Player player) {
        registry.byPlayer(player.getUniqueId()).ifPresent(session -> {
            if (session.state() != MatchState.ENDING) {
                return;
            }
            session.setRematchRequested(player.getUniqueId(), true);
            // Notify the other side: the 1v1 opponent, or every enemy-team member in a team battle.
            List<UUID> others = session.isTeamMatch()
                    ? session.team(session.teamColor(player.getUniqueId()).opposite())
                    : (session.opponentOf(player.getUniqueId()) == null
                            ? List.of() : List.of(session.opponentOf(player.getUniqueId())));
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
                cancelTask(session.id());
                UUID a = session.participants().get(0);
                UUID b = session.participants().get(1);
                String kit = session.kitName();
                MatchMode mode = session.mode();
                ArenaTerrain terrain = session.terrain();
                int bestOf = session.bestOf();
                Map<UUID, Integer> carrySeries = session.seriesWinsSnapshot();
                cleanupSession(session, false);
                if (session.isTeamMatch()) {
                    // Rematch a team battle with the same rosters and sides.
                    startTeamMatch(new ArrayList<>(session.team(TeamColor.RED)),
                            new ArrayList<>(session.team(TeamColor.BLUE)),
                            kit, mode, terrain, bestOf);
                } else {
                    startDuel(a, b, kit, mode, terrain, bestOf, carrySeries);
                }
            }
        });
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
            session.setRematchRequested(player.getUniqueId(), false);
            session.setRematchRequested(session.opponentOf(player.getUniqueId()), false);
            returnPlayersToLobby(session);
        });
    }

    private void returnPlayersToLobby(MatchSession session) {
        String kit = session.kitName();
        MatchMode mode = session.mode();
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                lobbyService.sendToLobby(player);
            }
            stateManager.resetToLobby(id);
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
                PlayerSettings settings = settingsService.get(id);
                if (settings.autoRequeue()) {
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> queueCoordinator.join(player, kit, mode), 10L);
                }
            }
        }
    }

    private void failMatch(MatchSession session, String reason) {
        plugin.getLogger().warning("Match " + session.id() + " failed: " + reason);
        session.setState(MatchState.FAILED);
        for (UUID id : session.participants()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                lobbyService.sendToLobby(player);
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
        cancelTask(session.id());
        if (spectatorService != null) {
            spectatorService.clearMatch(session.id());
        }
        UUID arenaId = session.arenaInstanceId();
        registry.unregister(session.id());
        session.setState(MatchState.CLOSED);
        if (releaseArena && arenaId != null) {
            arenaService.release(arenaId);
        }
        combatTracker.clear(session.id());
    }

    private void giveRematchItems(Player player) {
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

    private void tryTransition(UUID playerId, PlayerState target) {
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
                    }
                }
            }
        } catch (Exception e) {
            stateManager.resetToLobby(playerId);
        }
    }

    private void cancelTask(UUID matchId) {
        BukkitTask task = tasks.remove(matchId);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        setShuttingDown(true);
        for (MatchSession session : List.copyOf(registry.all())) {
            cleanupSession(session, true);
            for (UUID id : session.participants()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    lobbyService.sendToLobby(player);
                }
                stateManager.resetToLobby(id);
            }
        }
    }
}
