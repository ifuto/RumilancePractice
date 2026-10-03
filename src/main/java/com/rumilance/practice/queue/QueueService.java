package com.rumilance.practice.queue;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.state.MatchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Kit+mode separated matchmaking queues with expanding PT range for ranked.
 * Supports multiple simultaneous queues per player (multi-queue).
 */
public final class QueueService {

    public record QueueEntry(
            UUID playerId,
            String kitId,
            MatchMode mode,
            int pt, // queued Glicko-2 display rating snapshot, used only for matchmaking range
            Instant joinedAt,
            String ip,
            PlayerPlatform platform
    ) {
    }

    public record MatchPair(QueueEntry a, QueueEntry b) {
    }

    private final PluginSettings settings;
    /** player → all their queue entries (multi-queue). */
    private final Map<UUID, List<QueueEntry>> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, List<QueueEntry>> byQueue = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> recentOpponents = new ConcurrentHashMap<>();

    public QueueService(PluginSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public static String queueKey(MatchMode mode, String kitId, PlayerPlatform platform) {
        return mode.name() + "_" + kitId.toLowerCase() + "_"
                + (platform == null ? PlayerPlatform.JAVA.queueToken() : platform.queueToken());
    }

    /**
     * Join a queue. Multi-queue: a player may be in multiple kit queues simultaneously.
     * Returns false only if already queued for this exact kit+mode.
     */
    public synchronized boolean join(
            UUID playerId,
            String kitId,
            MatchMode mode,
            int pt,
            String ip,
            PlayerPlatform platform
    ) {
        if (!PracticeGuards.canEnterQueue(mode, isQueued(playerId))) {
            return false;
        }
        PlayerPlatform resolved = platform == null ? PlayerPlatform.JAVA : platform;
        String key = queueKey(mode, kitId, resolved);
        // 同一キット+モードに既にキューしていればスキップ
        List<QueueEntry> playerEntries = byPlayer.computeIfAbsent(playerId, k -> new ArrayList<>());
        for (QueueEntry existing : playerEntries) {
            if (existing.mode() == mode && existing.kitId().equalsIgnoreCase(kitId)
                    && existing.platform() == resolved) {
                return false;
            }
        }
        QueueEntry entry = new QueueEntry(playerId, kitId.toLowerCase(), mode, pt, Instant.now(), ip, resolved);
        playerEntries.add(entry);
        byQueue.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
        return true;
    }

    /** Leave ALL queues for this player. Returns the first removed entry (for backward compat). */
    public synchronized Optional<QueueEntry> leave(UUID playerId) {
        List<QueueEntry> entries = byPlayer.remove(playerId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        for (QueueEntry removed : entries) {
            List<QueueEntry> list = byQueue.get(queueKey(removed.mode(), removed.kitId(), removed.platform()));
            if (list != null) {
                list.removeIf(e -> e.playerId().equals(playerId));
            }
        }
        return Optional.of(entries.get(0));
    }

    /** Leave a specific kit+mode queue. */
    public synchronized boolean leaveKit(UUID playerId, String kitId, MatchMode mode, PlayerPlatform platform) {
        List<QueueEntry> entries = byPlayer.get(playerId);
        if (entries == null) return false;
        PlayerPlatform resolved = platform == null ? PlayerPlatform.JAVA : platform;
        Iterator<QueueEntry> it = entries.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            QueueEntry e = it.next();
            if (e.mode() == mode && e.kitId().equalsIgnoreCase(kitId) && e.platform() == resolved) {
                it.remove();
                List<QueueEntry> list = byQueue.get(queueKey(mode, kitId, resolved));
                if (list != null) list.removeIf(x -> x.playerId().equals(playerId));
                removed = true;
                break;
            }
        }
        if (entries.isEmpty()) byPlayer.remove(playerId);
        return removed;
    }

    /** First entry for backward compat (action bar, etc). */
    public Optional<QueueEntry> get(UUID playerId) {
        List<QueueEntry> entries = byPlayer.get(playerId);
        if (entries == null || entries.isEmpty()) return Optional.empty();
        return Optional.of(entries.get(0));
    }

    /** All queue entries for a player. */
    public List<QueueEntry> getAll(UUID playerId) {
        return byPlayer.getOrDefault(playerId, List.of());
    }

    public boolean isQueued(UUID playerId) {
        List<QueueEntry> entries = byPlayer.get(playerId);
        return entries != null && !entries.isEmpty();
    }

    /** Check if player is queued for a specific kit+mode. */
    public boolean isQueuedFor(UUID playerId, String kitId, MatchMode mode) {
        List<QueueEntry> entries = byPlayer.get(playerId);
        if (entries == null) return false;
        for (QueueEntry e : entries) {
            if (e.mode() == mode && e.kitId().equalsIgnoreCase(kitId)) return true;
        }
        return false;
    }

    /** All kitIds the player is currently queued for in a given mode. */
    public java.util.Set<String> queuedKitIds(UUID playerId, MatchMode mode) {
        List<QueueEntry> entries = byPlayer.get(playerId);
        if (entries == null) return java.util.Set.of();
        return entries.stream()
                .filter(e -> e.mode() == mode)
                .map(QueueEntry::kitId)
                .collect(Collectors.toSet());
    }

    /** 1-based position of {@code playerId} within their own kit+mode+platform wait list. */
    public int positionOf(UUID playerId) {
        QueueEntry entry = get(playerId).orElse(null);
        if (entry == null) {
            return 0;
        }
        List<QueueEntry> list = byQueue.get(
                queueKey(entry.mode(), entry.kitId(), entry.platform()));
        if (list == null) {
            return 0;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).playerId().equals(playerId)) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Total waiters in the same kit+mode+platform list as {@code playerId}. */
    public int listSizeOf(UUID playerId) {
        QueueEntry entry = get(playerId).orElse(null);
        if (entry == null) {
            return 0;
        }
        List<QueueEntry> list = byQueue.get(
                queueKey(entry.mode(), entry.kitId(), entry.platform()));
        return list == null ? 0 : list.size();
    }

    public int waitingCount(MatchMode mode, String kitId, PlayerPlatform platform) {
        List<QueueEntry> list = byQueue.get(queueKey(mode, kitId, platform));
        return list == null ? 0 : list.size();
    }

    /** Total unique queued players. */
    public int totalWaiting() {
        return byPlayer.size();
    }

    /** Total entries in one mode across all kits, for the given client platform. */
    public int totalWaiting(MatchMode mode, PlayerPlatform platform) {
        int count = 0;
        for (List<QueueEntry> entries : byPlayer.values()) {
            for (QueueEntry entry : entries) {
                if (entry.mode() == mode && entry.platform() == platform) {
                    count++;
                }
            }
        }
        return count;
    }

    public synchronized void clearAll() {
        byPlayer.clear();
        byQueue.clear();
    }

    /** Remove entries for players who are no longer connected. */
    public synchronized void pruneOffline() {
        byPlayer.values().removeIf(entries -> {
            entries.removeIf(entry -> org.bukkit.Bukkit.getPlayer(entry.playerId()) == null);
            return entries.isEmpty();
        });
        for (List<QueueEntry> list : byQueue.values()) {
            list.removeIf(e -> org.bukkit.Bukkit.getPlayer(e.playerId()) == null);
        }
    }

    public synchronized List<MatchPair> pollMatches(boolean blockSameIp, boolean avoidRecent, Instant now) {
        return pollMatches(blockSameIp, avoidRecent, now, null);
    }

    public synchronized List<MatchPair> pollMatches(boolean blockSameIp, boolean avoidRecent, Instant now,
                                                    java.util.function.BiPredicate<UUID, UUID> pairBlocked) {
        List<MatchPair> pairs = new ArrayList<>();
        for (Map.Entry<String, List<QueueEntry>> entry : byQueue.entrySet()) {
            List<QueueEntry> list = entry.getValue();
            if (list.size() < 2) {
                continue;
            }
            boolean matched;
            do {
                matched = false;
                outer:
                for (int i = 0; i < list.size(); i++) {
                    QueueEntry a = list.get(i);
                    for (int j = i + 1; j < list.size(); j++) {
                        QueueEntry b = list.get(j);
                        // When only two players are waiting for this kit+mode, ignore PT and
                        // recent-opponent blocks so they are never stuck alone forever.
                        boolean lonelyPair = list.size() == 2;
                        if (!canMatch(a, b, blockSameIp, avoidRecent && !lonelyPair, now, lonelyPair,
                                pairBlocked)) {
                            continue;
                        }
                        pairs.add(new MatchPair(a, b));
                        // Remove matched players from ALL queues (multi-queue cleanup).
                        // removePlayerFromAllQueues also cleans up byQueue references, so
                        // remove from the current `list` manually first, then clear byPlayer.
                        list.remove(j);
                        list.remove(i);
                        removePlayerFromAllQueues(a.playerId());
                        removePlayerFromAllQueues(b.playerId());
                        if (avoidRecent) {
                            recentOpponents.put(a.playerId(), b.playerId());
                            recentOpponents.put(b.playerId(), a.playerId());
                        }
                        matched = true;
                        break outer;
                    }
                }
            } while (matched && list.size() >= 2);
        }
        return pairs;
    }

    private boolean canMatch(QueueEntry a, QueueEntry b, boolean blockSameIp, boolean avoidRecent,
                             Instant now, boolean ignorePt,
                             java.util.function.BiPredicate<UUID, UUID> pairBlocked) {
        long waitedSeconds = Math.max(
                now.getEpochSecond() - a.joinedAt().getEpochSecond(),
                now.getEpochSecond() - b.joinedAt().getEpochSecond()
        );
        int intervals = (int) (waitedSeconds / Math.max(1, settings.queueGrowthIntervalSeconds()));
        int range = settings.queueInitialPtRange() + intervals * settings.queuePtRangeGrowthPerInterval();
        return PracticeGuards.canPairInQueue(
                a,
                b,
                blockSameIp,
                avoidRecent,
                ignorePt,
                range,
                recentOpponents.get(a.playerId()),
                recentOpponents.get(b.playerId()),
                pairBlocked
        );
    }

    public synchronized void removeStale(UUID playerId) {
        leave(playerId);
    }

    /** Remove a player from ALL queues and clean up byQueue references. */
    private void removePlayerFromAllQueues(UUID playerId) {
        List<QueueEntry> entries = byPlayer.remove(playerId);
        if (entries == null) return;
        for (QueueEntry e : entries) {
            List<QueueEntry> list = byQueue.get(queueKey(e.mode(), e.kitId(), e.platform()));
            if (list != null) list.removeIf(x -> x.playerId().equals(playerId));
        }
    }


    /** ランクキュー参加時の案内用: 同モード+キット+プラットフォームに待機中の同一IPエントリがあるか。 */
    public synchronized boolean hasSameIpWaiter(String kitId, MatchMode mode, PlayerPlatform platform,
                                                String ip, UUID self) {
        if (ip == null) {
            return false;
        }
        String key = queueKey(mode, kitId, platform);
        for (QueueEntry entry : byQueue.getOrDefault(key, List.of())) {
            if (!entry.playerId().equals(self) && ip.equals(entry.ip())) {
                return true;
            }
        }
        return false;
    }
}
