package com.rumilance.practice.replay;

import com.rumilance.practice.match.MatchActionRecorder;
import com.rumilance.practice.session.MatchSession;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory archive of recent matches for the replay viewer.
 *
 * <p>Access is gated by rank: default players get no replays; VIP+ players keep the last
 * {@link #VIP_SLOTS} matches, capped to {@link #VIP_MAX_MATCH_SECONDS} of fight each, and every
 * recording is purged {@link #RETENTION_MILLIS} after the match ends. The frames are the same
 * sampled movement traces the report system uses ({@link MatchActionRecorder}); at match end the
 * participants' traces for that match are collected into one {@link RecordedMatch}.</p>
 *
 * <p>This class never touches Bukkit API off the main thread: it is populated from
 * {@code MatchService.endMatch} and read on command.</p>
 */
public final class ReplayArchive {

    /** Default (non-donor) players cannot view replays at all. */
    public static final int DEFAULT_SLOTS = 0;
    public static final int DEFAULT_MAX_MATCH_SECONDS = 0;
    /** VIP+ keeps the last 3 matches, up to 15 minutes each. */
    public static final int VIP_SLOTS = 3;
    public static final int VIP_MAX_MATCH_SECONDS = 15 * 60;
    /** Matches vanish two days after they finish. */
    public static final long RETENTION_MILLIS = 2L * 24L * 60L * 60L * 1000L;

    /** A single participant's captured trace (movement/health frames for the match). */
    public record Player(UUID id, String name, List<MatchActionRecorder.Frame> frames) {
    }

    /** A finished match held for replay. */
    public static final class RecordedMatch {
        private final UUID matchId;
        private final String kit;
        private final String mode;
        private final String world;
        private final long endedAtEpochMs;
        private final long durationTicks;
        private final ReplayArenaSnapshot arena;
        private final Map<UUID, Player> players = new LinkedHashMap<>();

        RecordedMatch(UUID matchId, String kit, String mode, String world,
                      long endedAtEpochMs, long durationTicks, ReplayArenaSnapshot arena) {
            this.matchId = matchId;
            this.kit = kit;
            this.mode = mode;
            this.world = world;
            this.endedAtEpochMs = endedAtEpochMs;
            this.durationTicks = durationTicks;
            this.arena = arena;
        }

        public UUID matchId() {
            return matchId;
        }

        public String kit() {
            return kit;
        }

        public String mode() {
            return mode;
        }

        public String world() {
            return world;
        }

        public long durationTicks() {
            return durationTicks;
        }

        public long durationSeconds() {
            return durationTicks / 20L;
        }

        public long endedAtEpochMs() {
            return endedAtEpochMs;
        }

        /** @return the arena placement snapshot (nullable for legacy / arena-less records). */
        public ReplayArenaSnapshot arena() {
            return arena;
        }

        public List<Player> players() {
            return new ArrayList<>(players.values());
        }
    }

    /** Per-viewer list of recent matches, newest first, keyed by the viewer's UUID. */
    private final Map<UUID, LinkedHashMap<UUID, RecordedMatch>> byViewer = new ConcurrentHashMap<>();

    /** Backward-compatible overload (no arena snapshot). */
    public void record(MatchActionRecorder recorder, MatchSession session) {
        record(recorder, session, null);
    }

    /**
     * Records a finished match for each participant.
     *
     * @param recorder the action recorder holding the participants' traces
     * @param session  the just-ended session
     * @param arena    placement snapshot so the replay can re-paste the arena (nullable)
     */
    public void record(MatchActionRecorder recorder, MatchSession session, ReplayArenaSnapshot arena) {
        if (recorder == null || session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long durationSeconds = 0L;
        if (session.startedAt() != null && session.endedAt() != null) {
            durationSeconds = Math.max(0L,
                    java.time.Duration.between(session.startedAt(), session.endedAt()).getSeconds());
        }
        long durationTicks = durationSeconds * 20L;
        // Derive the world from a live participant if possible.
        String world = null;
        for (UUID id : session.participants()) {
            org.bukkit.entity.Player p = Bukkit.getPlayer(id);
            if (p != null) {
                world = p.getWorld().getName();
                break;
            }
        }
        RecordedMatch match = new RecordedMatch(
                session.id(), session.kitName(), session.mode().name(), world, now, durationTicks, arena);
        boolean anyFrames = false;
        for (UUID id : session.participants()) {
            List<MatchActionRecorder.Frame> frames = recorder.framesOf(id, session.id());
            match.players.put(id, new Player(id, nameOf(id), frames));
            if (!frames.isEmpty()) {
                anyFrames = true;
            }
        }
        if (!anyFrames) {
            return;
        }
        for (UUID id : session.participants()) {
            LinkedHashMap<UUID, RecordedMatch> list =
                    byViewer.computeIfAbsent(id, k -> new LinkedHashMap<>());
            synchronized (list) {
                list.put(session.id(), match);
                // Keep only the newest VIP_SLOTS entries per viewer.
                while (list.size() > VIP_SLOTS) {
                    UUID oldest = list.keySet().iterator().next();
                    list.remove(oldest);
                }
            }
        }
        purge(now);
    }

    /** @return the recorded matches available to {@code viewer}, newest first, expired ones dropped. */
    public List<RecordedMatch> recent(UUID viewer, boolean vipPlus) {
        purge(System.currentTimeMillis());
        if (!vipPlus) {
            return List.of();
        }
        LinkedHashMap<UUID, RecordedMatch> map = byViewer.get(viewer);
        if (map == null) {
            return List.of();
        }
        List<RecordedMatch> out = new ArrayList<>();
        synchronized (map) {
            List<RecordedMatch> values = new ArrayList<>(map.values());
            java.util.Collections.reverse(values);
            for (RecordedMatch m : values) {
                // Enforce the per-match length cap at read time.
                if (m.durationSeconds() <= VIP_MAX_MATCH_SECONDS || m.durationSeconds() == 0) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    /** @return a specific recorded match for a viewer, if still retained and they took part. */
    public RecordedMatch get(UUID viewer, UUID matchId) {
        purge(System.currentTimeMillis());
        LinkedHashMap<UUID, RecordedMatch> map = byViewer.get(viewer);
        if (map == null) {
            return null;
        }
        synchronized (map) {
            return map.get(matchId);
        }
    }

    private void purge(long now) {
        for (Map.Entry<UUID, LinkedHashMap<UUID, RecordedMatch>> entry : byViewer.entrySet()) {
            LinkedHashMap<UUID, RecordedMatch> map = entry.getValue();
            synchronized (map) {
                map.values().removeIf(m -> now - m.endedAtEpochMs() > RETENTION_MILLIS);
            }
        }
    }

    private static String nameOf(UUID id) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        String name = offline.getName();
        return name != null ? name : id.toString().substring(0, 8);
    }
}
