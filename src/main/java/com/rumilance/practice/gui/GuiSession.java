package com.rumilance.practice.gui;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable selection state for one open GUI (kit/map/best-of/page/etc.).
 */
public final class GuiSession {

    private final UUID sessionId;
    private final UUID playerId;
    private final GuiType type;
    private final int rows;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile String selectedKit;
    private volatile String selectedMap;
    private volatile int page;
    private volatile int bestOf = 1;
    private volatile boolean ranked = true;
    private volatile UUID targetPlayer;

    public GuiSession(UUID sessionId, UUID playerId, GuiType type, int rows) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.type = Objects.requireNonNull(type, "type");
        this.rows = rows;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public GuiType type() {
        return type;
    }

    public int rows() {
        return rows;
    }

    public String selectedKit() {
        return selectedKit;
    }

    public void setSelectedKit(String selectedKit) {
        this.selectedKit = selectedKit;
    }

    public String selectedMap() {
        return selectedMap;
    }

    public void setSelectedMap(String selectedMap) {
        this.selectedMap = selectedMap;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public int bestOf() {
        return bestOf;
    }

    public void setBestOf(int bestOf) {
        this.bestOf = bestOf;
    }

    public boolean ranked() {
        return ranked;
    }

    public void setRanked(boolean ranked) {
        this.ranked = ranked;
    }

    public UUID targetPlayer() {
        return targetPlayer;
    }

    public void setTargetPlayer(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return (T) value;
    }
}
