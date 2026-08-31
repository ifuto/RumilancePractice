package com.rumilance.practice.replay;

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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays back recorded movement of a recent match at its original world coordinates. Each
 * participant is represented by an armour stand wearing the player's own head and carrying the
 * player's name, so the avatars read as real players rather than floating items.
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
    private org.bukkit.NamespacedKey controlKey;

    public ReplayService(Plugin plugin, LobbyService lobbyService) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
        this.controlKey = new org.bukkit.NamespacedKey(plugin, "replay_control");
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
        for (ReplayArchive.Player rp : recorded.players()) {
            List<Frame> frames = rp.frames();
            avatars.add(new ReplaySession.Avatar(rp.id(), rp.name(), frames));
            if (anchor == null && !frames.isEmpty()) {
                anchor = frames.get(0);
            }
        }
        if (anchor == null) {
            operator.sendMessage(Component.text("録画フレームが空です。", NamedTextColor.RED));
            return;
        }
        begin(operator, recorded.matchId(), world, avatars, anchor);
    }

    private void begin(Player operator, UUID id, World world,
                       List<ReplaySession.Avatar> avatars, Frame anchor) {
        ReplaySession session = new ReplaySession(operator.getUniqueId(), id, world.getName(), avatars);
        for (ReplaySession.Avatar a : avatars) {
            a.stand = spawnAvatar(world, a.frames, a.name, a.playerId,
                    operator.getUniqueId().equals(a.playerId) ? NamedTextColor.GREEN : NamedTextColor.AQUA);
        }
        // Creative so the operator can fly around freely; they are not in survival/adventure and
        // cannot affect the world (avengers are armour stands, terrain is untouched).
        operator.setGameMode(GameMode.CREATIVE);
        operator.setAllowFlight(true);
        operator.setFlying(true);
        operator.getInventory().clear();
        operator.teleport(new Location(world, anchor.x(), anchor.y() + 3, anchor.z(), anchor.yaw(), 30f));
        giveControlItems(operator);

        sessions.put(operator.getUniqueId(), session);
        World finalWorld = world;
        session.taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session, finalWorld), 1L, 1L)
                .getTaskId();
        operator.sendMessage(Component.text("リプレイ開始 (クリエイティブ: アイテムを右クリックで操作)", NamedTextColor.GREEN));
        sendControls(operator, session);
    }

    private ArmorStand spawnAvatar(World world, List<Frame> frames, String name, UUID owner, NamedTextColor color) {
        if (frames.isEmpty()) {
            return null;
        }
        Frame first = frames.get(0);
        Location loc = new Location(world, first.x(), first.y(), first.z(), first.yaw(), 0f);
        ArmorStand stand = world.spawn(loc, ArmorStand.class, s -> {
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setBasePlate(false);
            s.setArms(true);
            s.setPersistent(false);
            s.setMarker(false);
            s.setInvisible(false);
            // Make it look like a real player: full player skin on the head, no base plate, arms
            // out. The skin comes from the participant's own account head.
            s.setCustomNameVisible(true);
            s.customName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                skull.displayName(Component.text(name, color));
                head.setItemMeta(skull);
            }
            if (s.getEquipment() != null) {
                s.getEquipment().setHelmet(head);
                // Hide the body armour so only the skin head + name reads as the player.
                s.getEquipment().setChestplate(new ItemStack(Material.AIR));
                s.getEquipment().setLeggings(new ItemStack(Material.AIR));
                s.getEquipment().setBoots(new ItemStack(Material.AIR));
            }
        });
        return stand;
    }

    private void tick(ReplaySession session, World world) {
        Player operator = Bukkit.getPlayer(session.operator);
        if (operator == null || !operator.isOnline()) {
            stopInternal(session, false);
            return;
        }
        if (!session.paused) {
            session.playheadTick += session.speed();
            if (session.playheadTick > session.endTick) {
                session.playheadTick = session.endTick;
                session.paused = true;
                operator.sendMessage(Component.text("リプレイ終了（[⟲]で最初から再生）", NamedTextColor.YELLOW));
            }
        }
        for (ReplaySession.Avatar a : session.avatars) {
            applyAvatar(a, session.playheadTick, world);
        }
        operator.sendActionBar(Component.text(
                (session.paused ? "⏸ " : "▶ ") + String.format("%.1fx  %d%%",
                        session.speed(), Math.round(session.progress() * 100)),
                NamedTextColor.AQUA));
    }

    private void applyAvatar(ReplaySession.Avatar avatar, double playheadTick, World world) {
        ArmorStand stand = avatar.stand;
        List<Frame> frames = avatar.frames;
        if (stand == null || stand.isDead() || frames.isEmpty()) {
            return;
        }
        Frame lower = frames.get(0);
        Frame upper = frames.get(frames.size() - 1);
        for (int i = 0; i < frames.size() - 1; i++) {
            if (frames.get(i).tick() <= playheadTick && frames.get(i + 1).tick() >= playheadTick) {
                lower = frames.get(i);
                upper = frames.get(i + 1);
                break;
            }
        }
        double span = upper.tick() - lower.tick();
        double t = span <= 0 ? 0 : Math.max(0, Math.min(1, (playheadTick - lower.tick()) / span));
        double x = lerp(lower.x(), upper.x(), t);
        double y = lerp(lower.y(), upper.y(), t);
        double z = lerp(lower.z(), upper.z(), t);
        float yaw = (float) lerp(lower.yaw(), upper.yaw(), t);
        Location target = new Location(world, x, y, z, yaw, 0f);
        if (stand.getLocation().distanceSquared(target) > 0.0001) {
            stand.teleport(target);
        }
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
        for (ReplaySession.Avatar a : session.avatars) {
            if (a.stand != null && !a.stand.isDead()) {
                a.stand.remove();
            }
        }
        Player operator = Bukkit.getPlayer(session.operator);
        if (operator != null && operator.isOnline()) {
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
            for (ReplaySession.Avatar a : session.avatars) {
                if (a.stand != null && !a.stand.isDead()) {
                    a.stand.remove();
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
