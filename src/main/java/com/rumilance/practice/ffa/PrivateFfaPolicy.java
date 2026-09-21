package com.rumilance.practice.ffa;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Private / Team FFA rules — an FFA only the invited players may enter, with a cooldown after it
 * closes.
 *
 * <p>Pure so the timing rule is unit-tested rather than discovered in play: the owner cannot
 * re-open a private FFA for {@link #CLOSE_COOLDOWN} after closing the previous one, and joining
 * is decided by invite alone for a private arena.</p>
 */
public final class PrivateFfaPolicy {

    /** How long an owner must wait after closing a private FFA before opening another. */
    public static final Duration CLOSE_COOLDOWN = Duration.ofSeconds(60);

    /** Cap on the invite list; a private FFA is for a party, not for the whole server. */
    public static final int MAX_INVITES = 24;

    private PrivateFfaPolicy() {
    }

    /**
     * @param now          current time
     * @param lastClosedAt when the previous private FFA was closed, or null if never
     * @param cooldown     override for {@link #CLOSE_COOLDOWN}; null keeps the default
     */
    public static boolean canOpen(Instant now, Instant lastClosedAt, Duration cooldown) {
        if (lastClosedAt == null || now == null) {
            return true;
        }
        Duration limit = cooldown == null ? CLOSE_COOLDOWN : cooldown;
        if (limit.isZero() || limit.isNegative()) {
            return true;
        }
        return !now.isBefore(lastClosedAt.plus(limit));
    }

    /** Seconds left on the cooldown, rounded up; 0 when it may be opened. */
    public static long remainingSeconds(Instant now, Instant lastClosedAt, Duration cooldown) {
        if (canOpen(now, lastClosedAt, cooldown)) {
            return 0L;
        }
        Duration limit = cooldown == null ? CLOSE_COOLDOWN : cooldown;
        long millis = Duration.between(now, lastClosedAt.plus(limit)).toMillis();
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    /**
     * @param invited  true when the player is on the invite list
     * @param isPublic true for a normal (open) FFA arena
     */
    public static boolean mayJoin(boolean invited, boolean isPublic) {
        return isPublic || invited;
    }

    /** Deduplicates the invite list, drops nulls, and caps it at {@link #MAX_INVITES}. */
    public static Set<UUID> normalizeInvites(Collection<UUID> raw) {
        Set<UUID> invites = new LinkedHashSet<>();
        if (raw == null) {
            return invites;
        }
        for (UUID id : raw) {
            if (id == null) {
                continue;
            }
            invites.add(id);
            if (invites.size() >= MAX_INVITES) {
                break;
            }
        }
        return invites;
    }
}
