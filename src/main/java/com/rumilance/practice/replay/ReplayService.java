package com.rumilance.practice.replay;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchActionRecorder.Frame;
import com.rumilance.practice.report.ReportEvidence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Plays back recorded movement of a recent match at its original world coordinates. Each
 * participant is rendered as a real player entity (a packet-only fake player with the
 * participant's own skin and player body), never an armour stand.
 *
 * <p>Arena reproduction: disposable arena copies are filled with air when the match releases
 * them, so the viewer would replay into an empty void. When the recording carries an arena
 * snapshot, the template schematic is re-pasted at the recorded origin for the duration of the
 * replay and cleared again when it stops.</p>
 *
 * <p>The operator watches in CREATIVE with a hotbar of transport-control items (restart, rewind,
 * pause/play, fast-forward, speed-cycle, stop) — right-click one to drive playback. Access is
 * rank-gated through {@link ReplayArchive} (default players have no replays; VIP+ keep the last
 * three matches up to 15 minutes, purged after two days).</p>
 */
public final class ReplayService {

    private final Plugin plugin;
    private final LobbyService lobbyService;
    private final Map<UUID, ReplaySession> sessions = new ConcurrentHashMap<>();
    /** Start generation per operator: only the newest async start may finish initialising. */
    private final Map<UUID, Long> startSequence = new ConcurrentHashMap<>();
    private final ReplayNpcService npcService;
    private org.bukkit.NamespacedKey controlKey;

    // Optional arena-reproduction support (wired from bootstrap when available).
    private FaweBridge faweBridge;
    private File schematicRoot;

    public ReplayService(Plugin plugin, LobbyService lobbyService, ReplayNpcService npcService) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
        this.npcService = npcService;
        this.controlKey = new org.bukkit.NamespacedKey(plugin, "replay_control");
    }

    /**
     * Wires arena re-paste support; without it replays play over whatever blocks exist.
     * {@code arenaService} is accepted for future collision checks and kept for callers.
     */
    public void setArenaSupport(ArenaService arenaService, FaweBridge faweBridge, File schematicRoot) {
        this.faweBridge = faweBridge;
        this.schematicRoot = schematicRoot;
    }

    public boolean isReplaying(UUID operator) {
        return sessions.containsKey(operator);
    }

    public void startFromReport(Player operator,
                                com.rumilance.practice.report.ReportService reportService,
                                UUID reportId) {
        if (reportService == null || reportId == null) {
            operator.sendMessage(Component.text("証跡データがありません。", NamedTextColor.RED));
            return;
        }
        ReportEvidence evidence = reportService.loadEvidence(reportId);
        startFromEvidence(operator, reportId, evidence);
    }

    /** Legacy entry point used by the report viewer. */
    public void start(Player operator, UUID reportId, ReportEvidence evidence) {
        startFromEvidence(operator, reportId, evidence);
    }

    private void startFromEvidence(Player operator, UUID id, ReportEvidence evidence) {
        stop(operator);
        // Supersede any still-initialising archive replay start.
        startSequence.merge(operator.getUniqueId(), 1L, Long::sum);
        if (evidence == null) {
            operator.sendMessage(Component.text("証跡データがありません。", NamedTextColor.RED));
            return;
        }
        World world = evidence.world() == null ? null : Bukkit.getWorld(evidence.world());
        List<ReplaySession.Avatar> avatars = new ArrayList<>();
        avatars.add(new ReplaySession.Avatar(evidence.reporterId(), evidence.reporterName(), evidence.reporterFrames()));
        avatars.add(new ReplaySession.Avatar(evidence.targetId(), evidence.targetName(), evidence.targetFrames()));
        List<Frame> all = new ArrayList<>();
        all.addAll(evidence.reporterFrames());
        all.addAll(evidence.targetFrames());
        if (all.isEmpty()) {
            operator.sendMessage(Component.text("録画フレームが空です。", NamedTextColor.RED));
            return;
        }
        Frame anchor = !evidence.targetFrames().isEmpty()
                ? evidence.targetFrames().get(0)
                : evidence.reporterFrames().get(0);
        if (world == null) {
            world = operator.getWorld();
        }
        begin(operator, id, world, avatars, anchor);
    }

    /** Starts playback of an archived (replay command) recorded match. */
    public void startArchive(Player operator, ReplayArchive.RecordedMatch recorded) {
        stop(operator);
        if (recorded == null) {
            operator.sendMessage(Component.text("リプレイが見つかりません。", NamedTextColor.RED));
            return;
        }
        World world = recorded.world() == null ? null : Bukkit.getWorld(recorded.world());
        if (world == null) {
            operator.sendMessage(Component.text("録画されたワールドが現在ありません。", NamedTextColor.RED));
            return;
        }
        List<ReplaySession.Avatar> avatars = new ArrayList<>();
        Frame anchor = null;
        int totalFrames = 0;
        for (ReplayArchive.Player rp : recorded.players()) {
            List<Frame> frames = rp.frames();
            avatars.add(new ReplaySession.Avatar(rp.id(), rp.name(), frames));
            totalFrames += frames.size();
            if (anchor == null && !frames.isEmpty()) {
                anchor = frames.get(0);
            }
        }
        if (anchor == null) {
            operator.sendMessage(Component.text("録画フレームが空です。", NamedTextColor.RED));
            return;
        }
        plugin.getLogger().info("[Replay] Starting replay of match " + recorded.matchId()
                + ": " + avatars.size() + " avatar(s), " + totalFrames + " frame(s), world=" + world.getName());

        // Re-paste the arena (it was air-cleared on release) BEFORE teleporting the operator,
        // so they arrive on solid ground instead of floating in a void. The paste is async;
        // playback begins on completion (or immediately if there is nothing to paste), and a
        // 10s timeout guarantees a hung paste can never block the replay from starting.
        java.util.concurrent.CompletableFuture<Boolean> paste = pasteArena(recorded.arena())
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
        final World startWorld = world;
        final Frame startAnchor = anchor;
        final long seq = startSequence.merge(operator.getUniqueId(), 1L, Long::sum);
        paste.whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!operator.isOnline()
                    || !Long.valueOf(seq).equals(startSequence.get(operator.getUniqueId()))) {
                // A newer replay start (or stop) superseded this one while the arena pasted.
                return;
            }
            begin(operator, recorded.matchId(), startWorld, avatars, startAnchor);
            ReplaySession session = sessions.get(operator.getUniqueId());
            // Clear only what THIS replay pasted: a relocated copy's region is normally air
            // after release, so removing it again is harmless. In-place arenas are shared and
            // must never be cleared here.
            if (session != null && pasteAttempted(recorded.arena())) {
                ReplayArenaSnapshot a = recorded.arena();
                World pasteWorld = Bukkit.getWorld(a.world());
                if (pasteWorld != null && faweBridge != null) {
                    session.cleanup = () -> faweBridge.clearRegion(pasteWorld,
                            a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ());
                }
            }
        }));
    }

    private boolean pasteAttempted(ReplayArenaSnapshot arena) {
        if (arena == null || !arena.relocated() || !arena.hasSchematic()) {
            return false;
        }
        if (faweBridge == null || !faweBridge.isAvailable()) {
            return false;
        }
        Path schematic = resolveSchematic(arena.schematicPath());
        return schematic != null && Files.isRegularFile(schematic)
                && Bukkit.getWorld(arena.world()) != null;
    }

    /**
     * Re-pastes the recorded arena copy and reports whether it happened. Disposable copies are
     * air after release; in-place arenas are skipped (they still exist in the world). Any
     * failure only degrades the visuals and completes the future with {@code false}.
     */
    private java.util.concurrent.CompletableFuture<Boolean> pasteArena(ReplayArenaSnapshot arena) {
        if (arena == null || !arena.relocated() || !arena.hasSchematic()) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (faweBridge == null || !faweBridge.isAvailable()) {
            plugin.getLogger().info("[Replay] No WorldEdit bridge - replay arena not reproduced.");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        Path schematic = resolveSchematic(arena.schematicPath());
        if (schematic == null || !Files.isRegularFile(schematic)) {
            plugin.getLogger().warning("[Replay] Schematic missing for replay arena: " + arena.schematicPath());
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        World world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        try {
            Location pasteAnchor = new Location(world, arena.minX(), arena.minY(), arena.minZ());
            return faweBridge.regenerate(schematic, pasteAnchor).handle((ok, error) -> {
                if (error != null || !Boolean.TRUE.equals(ok)) {
                    plugin.getLogger().log(Level.WARNING,
                            "[Replay] Failed to re-paste arena '" + arena.templateName() + "'", error);
                    return false;
                }
                plugin.getLogger().info("[Replay] Re-pasted arena '" + arena.templateName()
                        + "' at " + arena.minX() + "," + arena.minY() + "," + arena.minZ());
                return true;
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[Replay] Arena paste threw", t);
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
    }

    private Path resolveSchematic(String schematicPath) {
        File file = new File(schematicPath);
        return (file.isAbsolute() ? file : new File(schematicRoot, schematicPath)).toPath();
    }

    private void begin(Player operator, UUID id, World world,
                       List<ReplaySession.Avatar> avatars, Frame anchor) {
        if (npcService == null || !npcService.isAvailable()) {
            operator.sendMessage(Component.text(
                    "リプレイ表示には ProtocolLib が必要です。", NamedTextColor.RED));
            return;
        }
        ReplaySession session = new ReplaySession(operator.getUniqueId(), id, world.getName(), avatars);
        for (ReplaySession.Avatar a : avatars) {
            if (!a.frames.isEmpty()) {
                Frame f = a.frames.get(0);
                Location loc = new Location(world, f.x(), f.y(), f.z(), f.yaw(), 0f);
                a.npc = npcService.spawn(operator, a.playerId, a.name, loc);
            }
        }
        // Creative so the operator can fly around freely; avatars are packet-only and terrain is
        // never touched.
        operator.setGameMode(GameMode.CREATIVE);
        operator.setAllowFlight(true);
        operator.setFlying(true);
        operator.getInventory().clear();
        operator.teleport(new Location(world, anchor.x(), anchor.y() + 3, anchor.z(), anchor.yaw(), 30f));
        giveControlItems(operator);

        sessions.put(operator.getUniqueId(), session);
        World finalWorld = world;
        session.taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                tick(session, operator, finalWorld);
            } catch (Throwable t) {
                // A single bad tick must never kill the playback task (the scheduler cancels
                // repeating tasks that throw, which looks like the replay "ending" on its own).
                plugin.getLogger().log(Level.WARNING, "[Replay] tick failed", t);
            }
        }, 1L, 1L).getTaskId();
        operator.sendMessage(Component.text("リプレイ開始 (クリエイティブ: アイテムを右クリックで操作)", NamedTextColor.GREEN));
        sendControls(operator, session);
    }

    private void tick(ReplaySession session, Player operator, World world) {
        if (operator == null || !operator.isOnline()) {
            stopInternal(session, false);
            return;
        }
        if (!session.paused) {
            session.playheadTick += session.speed();
            if (session.playheadTick >= session.endTick) {
                session.playheadTick = session.endTick;
                session.paused = true;
                operator.sendMessage(Component.text("リプレイ終了（[⟲]で最初から再生）", NamedTextColor.YELLOW));
            }
        }
        for (ReplaySession.Avatar a : session.avatars) {
            applyAvatar(operator, a, session.playheadTick, world);
        }
        operator.sendActionBar(Component.text(
                (session.paused ? "⏸ " : "▶ ") + String.format("%.1fx  %d%%",
                        session.speed(), Math.round(session.progress() * 100)),
                NamedTextColor.AQUA));
    }

    private void applyAvatar(Player viewer, ReplaySession.Avatar avatar, double playheadTick, World world) {
        List<Frame> frames = avatar.frames;
        if (avatar.npc == null || frames.isEmpty()) {
            return;
        }
        // Binary-search the bracketing frames instead of scanning from the start every tick:
        // long recordings (hundreds of frames x several avatars x 20Hz) were a hot loop.
        int lo = 0;
        int hi = frames.size() - 1;
        Frame lower = frames.get(0);
        Frame upper = frames.get(hi);
        if (playheadTick >= upper.tick()) {
            lower = upper;
        } else if (playheadTick > lower.tick()) {
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (frames.get(mid).tick() <= playheadTick) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            lower = frames.get(lo);
            upper = frames.get(hi);
        }
        double span = upper.tick() - lower.tick();
        double t = span <= 0 ? 0 : Math.max(0, Math.min(1, (playheadTick - lower.tick()) / span));
        double x = lerp(lower.x(), upper.x(), t);
        double y = lerp(lower.y(), upper.y(), t);
        double z = lerp(lower.z(), upper.z(), t);
        float yaw = (float) lerp(lower.yaw(), upper.yaw(), t);
        npcService.teleport(viewer, avatar.npc, new Location(world, x, y, z, yaw, 0f));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ---- transport control items (creative hotbar) ----

    private void giveControlItems(Player operator) {
        operator.getInventory().setHeldItemSlot(0);
        operator.getInventory().setItem(0, controlItem(Material.YELLOW_DYE, "restart", "⏮ Restart"));
        operator.getInventory().setItem(1, controlItem(Material.ORANGE_DYE, "rewind", "⏪ Rewind"));
        operator.getInventory().setItem(2, controlItem(Material.LIME_DYE, "pause", "⏸/▶ Pause/Play"));
        operator.getInventory().setItem(3, controlItem(Material.CYAN_DYE, "forward", "⏩ Forward"));
        operator.getInventory().setItem(4, controlItem(Material.LIGHT_BLUE_DYE, "speed", "⏱ Speed"));
        operator.getInventory().setItem(8, controlItem(Material.BARRIER, "stop", "✖ Stop"));
    }

    private ItemStack controlItem(Material material, String action, String label) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(controlKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    /** @return true if the click was a replay-control item (and it was handled). */
    public boolean handleControlClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String action = item.getItemMeta().getPersistentDataContainer().get(controlKey, PersistentDataType.STRING);
        if (action == null || !sessions.containsKey(player.getUniqueId())) {
            return false;
        }
        switch (action) {
            case "restart" -> restart(player);
            case "rewind" -> rewind(player);
            case "pause" -> togglePause(player);
            case "forward" -> forward(player);
            case "speed" -> cycleSpeed(player);
            case "stop" -> stop(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    public void togglePause(Player operator) {
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session == null) {
            return;
        }
        session.paused = !session.paused;
        sendControls(operator, session);
    }

    public void cycleSpeed(Player operator) {
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session == null) {
            return;
        }
        session.cycleSpeed();
        sendControls(operator, session);
    }

    public void rewind(Player operator) {
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session != null) {
            session.seek(-40); // ~2 seconds
            sendControls(operator, session);
        }
    }

    public void forward(Player operator) {
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session != null) {
            session.seek(40);
            sendControls(operator, session);
        }
    }

    public void restart(Player operator) {
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session != null) {
            session.restart();
            sendControls(operator, session);
        }
    }

    public void stop(Player operator) {
        // Invalidates any still-initialising (async arena-paste) start as well.
        startSequence.merge(operator.getUniqueId(), 1L, Long::sum);
        ReplaySession session = sessions.get(operator.getUniqueId());
        if (session != null) {
            stopInternal(session, true);
        }
    }

    private void stopInternal(ReplaySession session, boolean returnToLobby) {
        sessions.remove(session.operator);
        if (session.taskId != -1) {
            Bukkit.getScheduler().cancelTask(session.taskId);
        }
        // Remove the re-pasted arena copy (async, best effort) before anything else reuses us.
        Runnable cleanup = session.cleanup;
        if (cleanup != null) {
            try {
                cleanup.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[Replay] arena cleanup failed", t);
            }
        }
        Player operator = Bukkit.getPlayer(session.operator);
        if (operator != null && operator.isOnline()) {
            if (npcService != null) {
                for (ReplaySession.Avatar a : session.avatars) {
                    if (a.npc != null) {
                        npcService.remove(operator, a.npc);
                    }
                }
            }
            operator.getInventory().clear();
            operator.setGameMode(GameMode.SURVIVAL);
            operator.setFlying(false);
            operator.setAllowFlight(false);
            if (returnToLobby && lobbyService != null) {
                lobbyService.sendToLobby(operator);
            }
            operator.sendMessage(Component.text("リプレイを終了しました。", NamedTextColor.YELLOW));
        }
    }

    public void shutdown() {
        for (ReplaySession session : List.copyOf(sessions.values())) {
            if (session.taskId != -1) {
                Bukkit.getScheduler().cancelTask(session.taskId);
            }
            // Best effort: the pasted replay arena should not survive a restart.
            Runnable cleanup = session.cleanup;
            if (cleanup != null) {
                try {
                    cleanup.run();
                } catch (Throwable ignored) {
                }
            }
            Player operator = Bukkit.getPlayer(session.operator);
            if (operator != null && npcService != null) {
                for (ReplaySession.Avatar a : session.avatars) {
                    if (a.npc != null) {
                        npcService.remove(operator, a.npc);
                    }
                }
            }
        }
        sessions.clear();
    }

    private void sendControls(Player operator, ReplaySession session) {
        Component bar = Component.text("操作: ", NamedTextColor.GRAY)
                .append(control("⏮", "/replay restart", NamedTextColor.AQUA)).append(Component.space())
                .append(control("⏪", "/replay rewind", NamedTextColor.AQUA)).append(Component.space())
                .append(control(session.paused ? "▶" : "⏸", "/replay pause", NamedTextColor.GREEN)).append(Component.space())
                .append(control("⏩", "/replay forward", NamedTextColor.AQUA)).append(Component.space())
                .append(control("[" + String.format("%.2fx", session.speed()) + "]", "/replay speed", NamedTextColor.YELLOW)).append(Component.space())
                .append(control("[✖停止]", "/replay stop", NamedTextColor.RED));
        operator.sendMessage(bar);
    }

    private static Component control(String label, String command, NamedTextColor color) {
        return Component.text(label, color)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand(command));
    }
}
