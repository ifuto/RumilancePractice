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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays back the compressed movement evidence of a report at its original world coordinates.
 * Two armour-stand avatars follow the recorded frames while the operator watches in spectator
 * mode with full transport controls (pause, speed, rewind, fast-forward, restart, stop).
 */
public final class ReplayService {

    private final Plugin plugin;
    private final LobbyService lobbyService;
    private final Map<UUID, ReplaySession> sessions = new ConcurrentHashMap<>();

    public ReplayService(Plugin plugin, LobbyService lobbyService) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
    }

    public boolean isReplaying(UUID operator) {
        return sessions.containsKey(operator);
    }

    /** Loads evidence for {@code reportId} via {@code reportService} and starts playback. */
    public void startFromReport(Player operator,
                                com.rumilance.practice.report.ReportService reportService,
                                UUID reportId) {
        if (reportService == null || reportId == null) {
            operator.sendMessage(Component.text("証跡データがありません。", NamedTextColor.RED));
            return;
        }
        ReportEvidence evidence = reportService.loadEvidence(reportId);
        start(operator, reportId, evidence);
    }

    /** Starts (or restarts) a replay for {@code operator} from the given evidence. */
    public void start(Player operator, UUID reportId, ReportEvidence evidence) {
        stop(operator);
        if (evidence == null) {
            operator.sendMessage(Component.text("証跡データがありません。", NamedTextColor.RED));
            return;
        }
        World world = evidence.world() == null ? null : Bukkit.getWorld(evidence.world());
        List<Frame> reporterFrames = evidence.reporterFrames();
        List<Frame> targetFrames = evidence.targetFrames();
        if (reporterFrames.isEmpty() && targetFrames.isEmpty()) {
            operator.sendMessage(Component.text("録画フレームが空です。", NamedTextColor.RED));
            return;
        }
        Frame anchor = !targetFrames.isEmpty() ? targetFrames.get(0) : reporterFrames.get(0);
        if (world == null) {
            world = operator.getWorld();
        }

        ReplaySession session = new ReplaySession(operator.getUniqueId(), reportId, evidence.world(),
                evidence.reporterName(), evidence.targetName(), reporterFrames, targetFrames);

        session.reporterAvatar = spawnAvatar(world, reporterFrames, evidence.reporterName(),
                evidence.reporterId(), NamedTextColor.AQUA);
        session.targetAvatar = spawnAvatar(world, targetFrames, evidence.targetName(),
                evidence.targetId(), NamedTextColor.RED);

        operator.setGameMode(GameMode.SPECTATOR);
        operator.teleport(new Location(world, anchor.x(), anchor.y() + 3, anchor.z(), anchor.yaw(), 30f));

        sessions.put(operator.getUniqueId(), session);
        World finalWorld = world;
        session.taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(session, finalWorld), 1L, 1L)
                .getTaskId();
        operator.sendMessage(Component.text("リプレイ開始: ", NamedTextColor.GREEN)
                .append(Component.text(evidence.reporterName() + " vs " + evidence.targetName(), NamedTextColor.WHITE)));
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
            s.setCustomNameVisible(true);
            s.customName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                head.setItemMeta(skull);
            }
            if (s.getEquipment() != null) {
                s.getEquipment().setHelmet(head);
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
        applyAvatar(session.reporterAvatar, session.reporterFrames, session.playheadTick, world);
        applyAvatar(session.targetAvatar, session.targetFrames, session.playheadTick, world);
        operator.sendActionBar(Component.text(
                (session.paused ? "⏸ " : "▶ ") + String.format("%.1fx  %d%%",
                        session.speed(), Math.round(session.progress() * 100)),
                NamedTextColor.AQUA));
    }

    private void applyAvatar(ArmorStand avatar, List<Frame> frames, double playheadTick, World world) {
        if (avatar == null || avatar.isDead() || frames.isEmpty()) {
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
        avatar.teleport(new Location(world, x, y, z, yaw, 0f));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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
            session.seek(-40); // ~2 seconds of game ticks
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
        if (session.reporterAvatar != null && !session.reporterAvatar.isDead()) {
            session.reporterAvatar.remove();
        }
        if (session.targetAvatar != null && !session.targetAvatar.isDead()) {
            session.targetAvatar.remove();
        }
        Player operator = Bukkit.getPlayer(session.operator);
        if (operator != null && operator.isOnline()) {
            operator.setGameMode(GameMode.SURVIVAL);
            if (returnToLobby) {
                lobbyService.sendToLobby(operator);
            }
            operator.sendMessage(Component.text("リプレイを終了しました。", NamedTextColor.YELLOW));
        }
    }

    public void shutdown() {
        for (ReplaySession session : sessions.values()) {
            if (session.taskId != -1) {
                Bukkit.getScheduler().cancelTask(session.taskId);
            }
            if (session.reporterAvatar != null && !session.reporterAvatar.isDead()) {
                session.reporterAvatar.remove();
            }
            if (session.targetAvatar != null && !session.targetAvatar.isDead()) {
                session.targetAvatar.remove();
            }
        }
        sessions.clear();
    }

    private void sendControls(Player operator, ReplaySession session) {
        Component bar = Component.text("操作: ", NamedTextColor.GRAY)
                .append(control("[⏮]", "/replay restart", NamedTextColor.AQUA)).append(Component.space())
                .append(control("[⏪]", "/replay rewind", NamedTextColor.AQUA)).append(Component.space())
                .append(control(session.paused ? "[▶]" : "[⏸]",
                        "/replay pause", NamedTextColor.GREEN)).append(Component.space())
                .append(control("[⏩]", "/replay forward", NamedTextColor.AQUA)).append(Component.space())
                .append(control("[" + String.format("%.2fx", session.speed()) + "]",
                        "/replay speed", NamedTextColor.YELLOW)).append(Component.space())
                .append(control("[✖停止]", "/replay stop", NamedTextColor.RED));
        operator.sendMessage(bar);
    }

    private static Component control(String label, String command, NamedTextColor color) {
        return Component.text(label, color)
                .decoration(TextDecoration.BOLD, true)
                .clickEvent(ClickEvent.runCommand(command));
    }
}
