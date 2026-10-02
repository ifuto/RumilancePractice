package com.rumilance.practice.queue;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime state for ranked queue availability.
 *
 * <ul>
 *   <li>OP can toggle ranked on/off at any time ({@link #setEnabled(boolean)})</li>
 *   <li>Ranked auto-unlocks once {@value #AUTO_UNLOCK_THRESHOLD} unique players have
 *       joined the server ({@link #onPlayerJoin(UUID)})</li>
 *   <li>When locked, queued ranked players see a message and are rejected</li>
 * </ul>
 */
public final class RankedQueueState {

    /** How many unique player joins are needed before ranked auto-unlocks. */
    public static final int AUTO_UNLOCK_THRESHOLD = 30;

    /** Master enable flag: false = ranked queue locked (OP override or not yet unlocked). */
    private volatile boolean enabled = true;

    /**
     * When true, ranked is automatically unlocked when enough unique players join.
     * Set to false by OP to keep ranked permanently locked regardless of join count.
     */
    private volatile boolean autoUnlockEnabled = true;

    /** Unique player UUIDs that have ever joined this server session. */
    private final Set<UUID> everJoined = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoUnlockEnabled() {
        return autoUnlockEnabled;
    }

    public void setAutoUnlockEnabled(boolean autoUnlockEnabled) {
        this.autoUnlockEnabled = autoUnlockEnabled;
    }

    /**
     * Called on every player join to track unique joiners.
     * If the threshold is reached and auto-unlock is on, ranked is enabled.
     *
     * @return true if this join triggered an auto-unlock
     */
    public boolean onPlayerJoin(UUID playerId) {
        if (playerId == null) return false;
        boolean added = everJoined.add(playerId);
        if (added && autoUnlockEnabled && !enabled
                && everJoined.size() >= AUTO_UNLOCK_THRESHOLD) {
            enabled = true;
            return true;
        }
        return false;
    }

    /** Unique players that have joined this session. */
    public int uniqueJoinCount() {
        return everJoined.size();
    }
}