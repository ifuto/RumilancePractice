package com.rumilance.practice.match;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private com.rumilance.practice.spectator.SpectatorService spectatorService;
    private com.rumilance.practice.punishment.ChatBanService chatBanService;
    private SettingsService settingsService;
    private QueueCoordinator queueCoordinator;

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

    public MatchRegistry registry() {
        return registry;
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
        if (registry.isPlayerInMatch(playerA) || registry.isPlayerInMatch(playerB)) {
            return;
        }
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || !kit.enabled()) {
            return;
        }

        MatchSession session = new MatchSession(
                UUID.randomUUID(), mode, kitId, List.of(playerA, playerB), null, terrain, bestOf);
        if (!registry.register(session)) {
            return;
        }

        duelRequestService.invalidateForPlayer(playerA);
        duelRequestService.invalidateForPlayer(playerB);
        tryTransition(playerA, PlayerState.PREPARING_MATCH);
        tryTransition(playerB, PlayerState.PREPARING_MATCH);
        session.setState(MatchState.RESERVING_ARENA);

        arenaService.reserve(ArenaType.DUEL, terrain == null ? kit.arenaTerrain() : terrain, session.id())
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

    private void teleportAndPrepare(MatchSession session, KitDefinition kit, ArenaInstance instance) {
        Player p1 = Bukkit.getPlayer(session.participants().get(0));
        Player p2 = Bukkit.getPlayer(session.participants().get(1));
        if (p1 == null || p2 == null) {
            failMatch(session, "Player offline during prepare");
            return;
        }
        Location spawnA = LocationUtil.safeTeleportLocation(arenaService.spawnA(instance), p1);
        Location spawnB = LocationUtil.safeTeleportLocation(arenaService.spawnB(instance), p2);
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
                    startCountdown(session);
                }));
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
            tryTransition(id, PlayerState.FIGHTING);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                ArenaInstance instance = session.arenaInstanceId() == null
                        ? null
                        : arenaService.get(session.arenaInstanceId()).orElse(null);
                if (instance != null) {
                    Location spawn = session.participants().get(0).equals(id)
                            ? arenaService.spawnA(instance)
                            : arenaService.spawnB(instance);
                    player.teleport(LocationUtil.safeTeleportLocation(spawn, player));
                }
                soundService.play(player, "match-start");
                player.showTitle(Title.title(
                        Component.text("⚔ FIGHT ⚔")
                                .color(TextColor.color(0xAA55FF))
                                .decorate(TextDecoration.BOLD),
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
                        player.sendMessage(Component.text("Match timed out — draw.", NamedTextColor.YELLOW));
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
        boolean draw = attackerId != null && victimId.equals(attackerId);
        UUID winner = draw ? null : attackerId;
        if (winner == null && !draw) {
            winner = session.opponentOf(victimId);
        }
        endMatch(session, winner, draw);
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

    public void endMatch(MatchSession session, UUID winnerId, boolean draw) {
        if (session.state() == MatchState.ENDING || session.state() == MatchState.CLOSED
                || session.state() == MatchState.CLEANING || session.state() == MatchState.FAILED) {
            return;
        }
        cancelTask(session.id());
        session.end(winnerId, draw);
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
            boolean win = !draw && winnerId != null && winnerId.equals(id);
            player.showTitle(Title.title(
                    Component.text(draw ? "- DRAW -" : (win ? "- WIN -" : "- LOSE -"))
                            .color(win ? NamedTextColor.RED : NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(400))
            ));
            soundService.play(player, "match-end-anvil");
            soundService.play(player, "match-end-levelup");
            giveRematchItems(player);
        }

        MatchResultProcessor processor = switch (session.mode()) {
            case RANKED -> rankedResultProcessor;
            case UNRANKED -> unrankedResultProcessor;
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
            UUID opponentId = session.opponentOf(player.getUniqueId());
            Player opponent = opponentId == null ? null : Bukkit.getPlayer(opponentId);
            if (opponent != null) {
                opponent.sendMessage(Component.text(player.getName() + " wants a rematch!")
                        .color(NamedTextColor.YELLOW));
            }
            if (session.bothRematchRequested()) {
                cancelTask(session.id());
                UUID a = session.participants().get(0);
                UUID b = session.participants().get(1);
                String kit = session.kitName();
                MatchMode mode = session.mode();
                ArenaTerrain terrain = session.terrain();
                int bestOf = session.bestOf();
                cleanupSession(session, false);
                startDuel(a, b, kit, mode, terrain, bestOf);
            }
        });
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
        if (queueCoordinator != null && settingsService != null && mode != MatchMode.FFA) {
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
                player.sendMessage(Component.text("Match could not start: " + reason, NamedTextColor.RED));
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
    }

    private void giveRematchItems(Player player) {
        ItemStack rematch = new ItemStack(Material.LIME_DYE);
        ItemMeta rematchMeta = rematch.getItemMeta();
        rematchMeta.displayName(Component.text("Rematch", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        rematchMeta.getPersistentDataContainer().set(ItemKeys.rematch(), PersistentDataType.BYTE, (byte) 1);
        rematch.setItemMeta(rematchMeta);
        player.getInventory().setItem(3, rematch);

        ItemStack lobby = new ItemStack(Material.RED_DYE);
        ItemMeta lobbyMeta = lobby.getItemMeta();
        lobbyMeta.displayName(Component.text("Return to Lobby", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lobbyMeta.getPersistentDataContainer().set(ItemKeys.returnLobby(), PersistentDataType.BYTE, (byte) 1);
        lobby.setItemMeta(lobbyMeta);
        player.getInventory().setItem(5, lobby);
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
