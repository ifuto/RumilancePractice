package com.rumilance.practice.queue;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-spam gate for queue interactions (queue menu clicks, queue signs, join commands).
 * Rejects re-triggers inside a short window so a player hammering a sign/menu cannot churn
 * the join/leave pipeline (matchmaking DB reads, ELO fetches, arena reservations) at click
 * speed. PvP combat traffic is explicitly out of scope — no fighting behaviour passes here.
 *
 * <p>Pure data structure: unit tests pin the cadence; the same instance is shared by the
 * queue coordinator and the sign queue service (wired in FeatureBootstrap), so alternating
 * between menu and sign cannot tunnel under either guard alone.
 */
public final class QueueClickGuard {

    /** Minimum interval between accepted queue interactions from one player. */
    public static final long INTERVAL_MS = 800L;
    /** Throttle feedback is chilled too: at most one "slow down" note per window. */
    public static final long FEEDBACK_INTERVAL_MS = 1500L;

    /** Decision for one interaction attempt. */
    public record Decision(boolean allowed, boolean warning) { }

    private final ConcurrentHashMap<UUID, Long> nextAllowedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> nextFeedbackAt = new ConcurrentHashMap<>();

    /** @return whether the interaction may proceed, and whether to nag about it. */
    public Decision evaluate(UUID playerId, long nowMs) {
        if (playerId == null) {
            return new Decision(true, false);
        }
        java.util.concurrent.atomic.AtomicReference<Decision> verdict = new java.util.concurrent.atomic.AtomicReference<>();
        nextAllowedAt.compute(playerId, (id, allowed) -> {
            if (allowed == null || nowMs >= allowed) {
                verdict.set(new Decision(true, false));
                return nowMs + INTERVAL_MS;
            }
            verdict.set(new Decision(false, shouldWarn(playerId, nowMs)));
            return allowed;
        });
        return verdict.get();
    }

    private boolean shouldWarn(UUID playerId, long nowMs) {
        Long at = nextFeedbackAt.get(playerId);
        if (at == null || nowMs >= at) {
            nextFeedbackAt.put(playerId, nowMs + FEEDBACK_INTERVAL_MS);
            return true;
        }
        return false;
    }

    /** Drops all remembered cadence for a player (leave flow / tests). */
    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        nextAllowedAt.remove(playerId);
        nextFeedbackAt.remove(playerId);
    }
}
