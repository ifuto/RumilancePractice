package com.rumilance.practice.social;

import com.rumilance.practice.database.repository.BlockRepository;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player block lists ({@code /block}, {@code /ignore}).
 *
 * <p>The only gameplay effect today is matchmaking: {@link #isBlockedEitherWay} is handed to
 * {@code QueueService#pollMatches} as its {@code pairBlocked} predicate, so two players who
 * blocked each other in either direction are never paired. A block is one-directional on
 * purpose — each player decides for themselves who they do not want to be matched with.</p>
 *
 * <p>Reads go through an in-memory map so the pairing tick never touches the database; writes
 * are fire-and-forget on the async pool. The service degrades to a pure in-memory list when no
 * repository is wired (tests, database down).</p>
 */
public final class BlockListService {

    private final Plugin plugin;
    private final BlockRepository repository;
    private final Map<UUID, Set<UUID>> blocked = new ConcurrentHashMap<>();

    public BlockListService(Plugin plugin, BlockRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Loads (or refreshes) one player's list. Call on join. */
    public void load(UUID playerId) {
        if (repository == null) {
            blocked.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
            return;
        }
        async(() -> {
            try {
                Set<UUID> ids = repository.findBlocked(playerId);
                Set<UUID> target = ConcurrentHashMap.<UUID>newKeySet();
                target.addAll(ids);
                blocked.put(playerId, target);
            } catch (SQLException e) {
                plugin.getLogger().warning("[blocks] load failed for " + playerId + ": " + e.getMessage());
            }
        });
    }

    /** Drops the cached list. Call on quit. */
    public void unload(UUID playerId) {
        blocked.remove(playerId);
    }

    public boolean isBlocked(UUID blocker, UUID target) {
        Set<UUID> ids = blocked.get(blocker);
        return ids != null && ids.contains(target);
    }

    /** True when either side blocked the other — the predicate the queue uses. */
    public boolean isBlockedEitherWay(UUID a, UUID b) {
        return isBlocked(a, b) || isBlocked(b, a);
    }

    /** Adds when absent, removes when present. Returns the state after the call. */
    public boolean toggle(UUID blocker, UUID target) {
        Set<UUID> ids = blocked.computeIfAbsent(blocker, k -> ConcurrentHashMap.newKeySet());
        boolean nowBlocked;
        if (ids.contains(target)) {
            ids.remove(target);
            nowBlocked = false;
        } else {
            ids.add(target);
            nowBlocked = true;
        }
        persist(blocker, target, nowBlocked);
        return nowBlocked;
    }

    public int count(UUID blocker) {
        Set<UUID> ids = blocked.get(blocker);
        return ids == null ? 0 : ids.size();
    }

    public Set<UUID> blockedBy(UUID blocker) {
        Set<UUID> ids = blocked.get(blocker);
        return ids == null ? Set.of() : Collections.unmodifiableSet(ids);
    }

    private void persist(UUID blocker, UUID target, boolean nowBlocked) {
        if (repository == null) {
            return;
        }
        async(() -> {
            try {
                if (nowBlocked) {
                    repository.insert(blocker, target, Instant.now());
                } else {
                    repository.delete(blocker, target);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[blocks] save failed for " + blocker + ": " + e.getMessage());
            }
        });
    }

    private void async(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }
}
