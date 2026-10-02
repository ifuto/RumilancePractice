package com.rumilance.practice.kit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last kit a player selected (for any queue, duel, or kit picker context).
 * Lightweight in-memory only — does not persist across restarts.
 * Zero disk I/O, zero DB writes, zero allocations beyond the map entry itself.
 */
public final class LastSelectedKitTracker {

    private final Map<UUID, String> lastKit = new ConcurrentHashMap<>();

    public void record(UUID playerId, String kitId) {
        if (playerId != null && kitId != null) {
            lastKit.put(playerId, kitId.toLowerCase());
        }
    }

    /** @return the last selected kit id, or null if the player has never selected one. */
    public String get(UUID playerId) {
        return playerId == null ? null : lastKit.get(playerId);
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            lastKit.remove(playerId);
        }
    }
}