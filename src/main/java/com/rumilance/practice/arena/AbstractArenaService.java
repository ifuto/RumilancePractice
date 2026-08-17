package com.rumilance.practice.arena;

import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaInstanceState;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared bookkeeping for {@link ArenaService} implementations: lazily creates exactly one
 * {@link ArenaInstance} per enabled {@link ArenaTemplate} and guards reservation with a lock so
 * two matches can never be handed the same instance (mirrors the player-side double-registration
 * guard in {@code com.rumilance.practice.match.MatchRegistry}). Subclasses only need to implement
 * the actual world regeneration strategy.
 */
public abstract class AbstractArenaService implements ArenaService {

    private final ReentrantLock reservationLock = new ReentrantLock();
    private final Map<UUID, ArenaInstance> instancesByTemplate = new ConcurrentHashMap<>();
    private volatile List<ArenaTemplate> templates = List.of();

    @Override
    public void setTemplates(List<ArenaTemplate> templates) {
        this.templates = List.copyOf(templates);
        // Drop cached instances for templates that no longer exist so stale instances can't leak.
        instancesByTemplate.keySet().retainAll(
                this.templates.stream().map(ArenaTemplate::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Override
    public List<ArenaTemplate> templates() {
        return templates;
    }

    @Override
    public List<ArenaTemplate> templates(ArenaType type) {
        List<ArenaTemplate> result = new ArrayList<>();
        for (ArenaTemplate template : templates) {
            if (template.type() == type) {
                result.add(template);
            }
        }
        return result;
    }

    @Override
    public Optional<ArenaInstance> get(UUID instanceId) {
        return instancesByTemplate.values().stream().filter(i -> i.id().equals(instanceId)).findFirst();
    }

    /**
     * Finds a free, enabled template matching {@code type} and atomically marks
     * its backing instance {@code IN_USE} for {@code matchId} before releasing the lock, so two
     * concurrent callers can never be handed the same instance. Returns empty if none is
     * currently free. This method has no Bukkit-world-mutating side effects, so it is safe (and
     * intended) to unit test directly.
     */
    protected final Optional<ArenaInstance> reserveInstance(ArenaType type, UUID matchId) {
        reservationLock.lock();
        try {
            for (ArenaTemplate template : templates) {
                if (!template.enabled() || template.type() != type) {
                    continue;
                }
                ArenaInstance instance = instancesByTemplate.computeIfAbsent(
                        template.id(), id -> new ArenaInstance(UUID.randomUUID(), template));
                if (instance.isAvailable()) {
                    instance.assignMatch(matchId);
                    return Optional.of(instance);
                }
            }
            return Optional.empty();
        } finally {
            reservationLock.unlock();
        }
    }

    /** In-place reservation of ONE specific enabled template by name (case-insensitive). */
    protected final Optional<ArenaInstance> reserveInstanceNamed(String templateName, UUID matchId) {
        reservationLock.lock();
        try {
            for (ArenaTemplate template : templates) {
                if (!template.enabled() || !template.name().equalsIgnoreCase(templateName)) {
                    continue;
                }
                ArenaInstance instance = instancesByTemplate.computeIfAbsent(
                        template.id(), id -> new ArenaInstance(UUID.randomUUID(), template));
                if (instance.isAvailable()) {
                    instance.assignMatch(matchId);
                    return Optional.of(instance);
                }
            }
            return Optional.empty();
        } finally {
            reservationLock.unlock();
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Optional<ArenaInstance>> reserveNamed(
            String templateName, UUID matchId) {
        return java.util.concurrent.CompletableFuture.completedFuture(
                reserveInstanceNamed(templateName, matchId));
    }

    protected final void markRegenerating(ArenaInstance instance) {
        instance.setState(ArenaInstanceState.REGENERATING);
    }

    protected final void markAvailable(ArenaInstance instance) {
        instance.release();
    }

    @Override
    public Location spawnA(ArenaInstance instance) {
        return LocationUtil.safeTeleportLocation(
                instance.offset(LocationUtil.deserialize(instance.template().serializedSpawnA())));
    }

    @Override
    public Location spawnB(ArenaInstance instance) {
        return LocationUtil.safeTeleportLocation(
                instance.offset(LocationUtil.deserialize(instance.template().serializedSpawnB())));
    }
}
