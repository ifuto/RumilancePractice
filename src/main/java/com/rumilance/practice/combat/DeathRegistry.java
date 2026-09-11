package com.rumilance.practice.combat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending death-catch plans, keyed by player id. Pure marker store kept Bukkit-free so the
 * lifecycle (mark / peek / consume / clear) is verifiable in the local test runner.
 *
 * @param <T> mode-owned payload (a {@code DeathBridge.RespawnPlan} at runtime)
 */
public final class DeathRegistry<T> {

    private final ConcurrentHashMap<UUID, T> pending = new ConcurrentHashMap<>();

    /** A {@code null} id or payload is a silent no-op: a broken caller must not poison the map. */
    public void mark(UUID id, T payload) {
        if (id != null && payload != null) {
            pending.put(id, payload);
        }
    }

    public T peek(UUID id) {
        return id == null ? null : pending.get(id);
    }

    /** Take the plan out: exactly-once semantics for the respawn consumer. */
    public T consume(UUID id) {
        return id == null ? null : pending.remove(id);
    }

    public boolean isMarked(UUID id) {
        return id != null && pending.containsKey(id);
    }

    /** Drop a plan without running it (disconnect, mode abort). */
    public void clear(UUID id) {
        if (id != null) {
            pending.remove(id);
        }
    }

    public int size() {
        return pending.size();
    }
}
