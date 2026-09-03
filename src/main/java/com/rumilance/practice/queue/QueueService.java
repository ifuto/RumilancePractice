package com.rumilance.practice.queue;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.guard.PracticeGuards;
import com.rumilance.practice.platform.PlayerPlatform;
import com.rumilance.practice.state.MatchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kit+mode separated matchmaking queues with expanding Elo range for ranked.
 */
public final class QueueService {

    public record QueueEntry(
            UUID playerId,
            String kitId,
            MatchMode mode,
            int elo,
            Instant joinedAt,
            String ip,
            PlayerPlatform platform
    ) {
    }

    public record MatchPair(QueueEntry a, QueueEntry b) {
    }

    private final PluginSettings settings;
    private final Map<UUID, QueueEntry> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, List<QueueEntry>> byQueue = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> recentOpponents = new ConcurrentHashMap<>();

    public QueueService(PluginSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    public static String queueKey(MatchMode mode, String kitId, PlayerPlatform platform) {
        return mode.name() + "_" + kitId.toLowerCase() + "_"
                + (platform == null ? PlayerPlatform.JAVA.queueToken() : platform.queueToken());
    }

    public synchronized boolean join(
            UUID playerId,
            String kitId,
            MatchMode mode,
            int elo,
            String ip,
            PlayerPlatform platform
    ) {
        if (!PracticeGuards.canEnterQueue(mode, byPlayer.containsKey(playerId))) {
            return false;
        }
        PlayerPlatform resolved = platform == null ? PlayerPlatform.JAVA : platform;
        QueueEntry entry = new QueueEntry(playerId, kitId.toLowerCase(), mode, elo, Instant.now(), ip, resolved);
        byPlayer.put(playerId, entry);
        byQueue.computeIfAbsent(queueKey(mode, kitId, resolved), k -> new ArrayList<>()).add(entry);
        return true;
    }

    public synchronized Optional<QueueEntry> leave(UUID playerId) {
        QueueEntry removed = byPlayer.remove(playerId);
        if (removed == null) {
            return Optional.empty();
        }
        List<QueueEntry> list = byQueue.get(queueKey(removed.mode(), removed.kitId(), removed.platform()));
        if (list != null) {
            list.removeIf(e -> e.playerId().equals(playerId));
        }
        return Optional.of(removed);
    }

    public Optional<QueueEntry> get(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    public boolean isQueued(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    public int waitingCount(MatchMode mode, String kitId, PlayerPlatform platform) {
        List<QueueEntry> list = byQueue.get(queueKey(mode, kitId, platform));
        return list == null ? 0 : list.size();
    }

    public int totalWaiting() {
        return byPlayer.size();
    }

    /** Total waiters in one mode across all kits, for the given client platform. */
    public int totalWaiting(MatchMode mode, PlayerPlatform platform) {
        int count = 0;
        for (QueueEntry entry : byPlayer.values()) {
            if (entry.mode() == mode && entry.platform() == platform) {
                count++;
            }
        }
        return count;
    }

    public synchronized void clearAll() {
        byPlayer.clear();
        byQueue.clear();
    }

    /** Remove entries for players who are no longer connected. Called before each matchmaking
     *  poll so a disconnect-during-queue can never leave a ghost entry that pairs with a live
     *  player. Runs on the main thread (scheduler tick). */
    public synchronized void pruneOffline() {
        byPlayer.values().removeIf(entry -> org.bukkit.Bukkit.getPlayer(entry.playerId()) == null);
        for (List<QueueEntry> list : byQueue.values()) {
            list.removeIf(e -> org.bukkit.Bukkit.getPlayer(e.playerId()) == null);
        }
    }

    public synchronized List<MatchPair> pollMatches(boolean blockSameIp, boolean avoidRecent, Instant now) {
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
                        // When only two players are waiting for this kit+mode, ignore Elo and
                        // recent-opponent blocks so they are never stuck alone forever.
                        boolean lonelyPair = list.size() == 2;
                        if (!canMatch(a, b, blockSameIp, avoidRecent && !lonelyPair, now, lonelyPair)) {
                            continue;
                        }
                        pairs.add(new MatchPair(a, b));
                        byPlayer.remove(a.playerId());
                        byPlayer.remove(b.playerId());
                        list.remove(j);
                        list.remove(i);
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
                             Instant now, boolean ignoreElo) {
        long waitedSeconds = Math.max(
                now.getEpochSecond() - a.joinedAt().getEpochSecond(),
                now.getEpochSecond() - b.joinedAt().getEpochSecond()
        );
        int intervals = (int) (waitedSeconds / Math.max(1, settings.queueGrowthIntervalSeconds()));
        int range = settings.queueInitialEloRange() + intervals * settings.queueEloRangeGrowthPerInterval();
        return PracticeGuards.canPairInQueue(
                a,
                b,
                blockSameIp,
                avoidRecent,
                ignoreElo,
                range,
                recentOpponents.get(a.playerId()),
                recentOpponents.get(b.playerId())
        );
    }

    public synchronized void removeStale(UUID playerId) {
        leave(playerId);
    }
}
