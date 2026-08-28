package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory-only, sampled evidence for the latest match of each player. It deliberately never
 * writes movement data to disk on its own; only when a player is reported does
 * {@code ReportService} pull a snapshot and persist a compressed copy. A sample every five
 * ticks is enough for post-report context while keeping the per-player footprint bounded, and
 * every completed trace expires after 60 seconds of inactivity.
 */
public final class MatchActionRecorder {

    public record Frame(long tick, UUID matchId, double x, double y, double z,
                        float yaw, float pitch, double vx, double vy, double vz,
                        double health, boolean sprinting, boolean onGround) {
    }

    /** Metadata about the most recent 1v1 match a player took part in (valid while the trace lives). */
    public record LastMatch(UUID matchId, UUID opponentId, String kit, String mode, String world) {
    }

    private static final int SAMPLE_PERIOD_TICKS = 5;
    private static final int MAX_FRAMES = 720;
    private static final long RETAIN_MILLIS = 60_000L;

    private static final class Trace {
        final ArrayDeque<Frame> frames = new ArrayDeque<>();
        UUID activeMatch;
        UUID opponentId;
        String kit;
        String mode;
        String world;
        long expiresAt;
    }

    private final MatchRegistry registry;
    private final Map<UUID, Trace> traces = new ConcurrentHashMap<>();
    private BukkitTask task;

    public MatchActionRecorder(Plugin plugin, MatchRegistry registry) {
        this.registry = registry;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, 1L, SAMPLE_PERIOD_TICKS);
    }

    public int recentFrameCount(UUID playerId) {
        Trace trace = traces.get(playerId);
        if (trace == null || (trace.expiresAt > 0L && trace.expiresAt < System.currentTimeMillis())) {
            return 0;
        }
        return trace.frames.size();
    }

    /**
     * @return the most recent 1v1 match metadata for {@code playerId} while its trace is still
     *         alive (used by {@code /report} to resolve the last opponent).
     */
    public Optional<LastMatch> lastMatch(UUID playerId) {
        Trace trace = traces.get(playerId);
        if (trace == null || trace.activeMatch == null || trace.opponentId == null) {
            return Optional.empty();
        }
        if (trace.expiresAt > 0L && trace.expiresAt < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(new LastMatch(trace.activeMatch, trace.opponentId, trace.kit, trace.mode, trace.world));
    }

    /** @return a copy of the recorded frames for {@code playerId} belonging to {@code matchId}. */
    public List<Frame> framesOf(UUID playerId, UUID matchId) {
        Trace trace = traces.get(playerId);
        if (trace == null || !matchId.equals(trace.activeMatch)) {
            return List.of();
        }
        return new ArrayList<>(trace.frames);
    }

    /**
     * Records the just-ended 1v1 match so {@code /report} can resolve the opponent and the
     * frames stay retrievable for {@link #RETAIN_MILLIS}. Team/FFA matches are ignored (reports
     * target a single opponent only).
     */
    public void completeMatch(MatchSession session) {
        if (session == null || session.isTeamMatch()) {
            return;
        }
        long now = System.currentTimeMillis();
        String world = null;
        var arenaId = session.arenaInstanceId();
        for (UUID id : session.participants()) {
            UUID opponent = session.opponentOf(id);
            Trace trace = traces.computeIfAbsent(id, ignored -> new Trace());
            trace.activeMatch = session.id();
            trace.opponentId = opponent;
            trace.kit = session.kitName();
            trace.mode = session.mode().name();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                trace.world = p.getWorld().getName();
                if (world == null) {
                    world = p.getWorld().getName();
                }
            }
            trace.expiresAt = now + RETAIN_MILLIS;
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        traces.clear();
    }

    private void sample() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            MatchSession match = registry.byPlayer(player.getUniqueId()).orElse(null);
            if (match == null || (match.state() != MatchState.ACTIVE && match.state() != MatchState.COUNTDOWN)) {
                expireIfCompleted(player.getUniqueId(), now);
                continue;
            }
            Trace trace = traces.computeIfAbsent(player.getUniqueId(), ignored -> new Trace());
            if (!match.id().equals(trace.activeMatch)) {
                trace.frames.clear();
                trace.activeMatch = match.id();
                trace.opponentId = match.opponentOf(player.getUniqueId());
                trace.kit = match.kitName();
                trace.mode = match.mode().name();
                trace.world = player.getWorld().getName();
            }
            trace.expiresAt = 0L;
            Location at = player.getLocation();
            Vector velocity = player.getVelocity();
            trace.frames.addLast(new Frame(
                    player.getWorld().getFullTime(), match.id(), at.getX(), at.getY(), at.getZ(),
                    at.getYaw(), at.getPitch(), velocity.getX(), velocity.getY(), velocity.getZ(),
                    player.getHealth(), player.isSprinting(), player.isOnGround()
            ));
            while (trace.frames.size() > MAX_FRAMES) {
                trace.frames.removeFirst();
            }
        }
        for (Map.Entry<UUID, Trace> entry : traces.entrySet()) {
            if (Bukkit.getPlayer(entry.getKey()) == null && entry.getValue().activeMatch != null
                    && entry.getValue().expiresAt == 0L) {
                entry.getValue().expiresAt = now + RETAIN_MILLIS;
            }
        }
        traces.entrySet().removeIf(entry -> entry.getValue().expiresAt > 0L && entry.getValue().expiresAt < now);
    }

    private void expireIfCompleted(UUID playerId, long now) {
        Trace trace = traces.get(playerId);
        if (trace != null && trace.activeMatch != null && trace.expiresAt == 0L) {
            trace.expiresAt = now + RETAIN_MILLIS;
        }
    }
}
