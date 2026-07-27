package com.rumilance.practice.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player active GUI session registry. Prevents stale GUI actions from applying.
 */
public final class GuiSessionRegistry {

    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();

    public GuiSession open(UUID playerId, GuiType type, int rows) {
        GuiSession session = new GuiSession(UUID.randomUUID(), playerId, type, rows);
        sessions.put(playerId, session);
        return session;
    }

    public Optional<GuiSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public boolean isCurrent(UUID playerId, UUID sessionId) {
        GuiSession session = sessions.get(playerId);
        return session != null && session.sessionId().equals(sessionId);
    }

    public void close(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }
}
