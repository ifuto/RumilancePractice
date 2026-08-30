package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A pending 1v1 duel challenge from {@code sender} to {@code target}.
 */
public record DuelRequest(UUID id, UUID sender, UUID target, String kitName, boolean ranked, Instant createdAt, Instant expiresAt) {

    public DuelRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(kitName, "kitName");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (sender.equals(target)) {
            throw new IllegalArgumentException("A player cannot send a duel request to themselves");
        }
    }

    public static DuelRequest create(UUID sender, UUID target, String kitName, boolean ranked, long ttlSeconds) {
        Instant now = Instant.now();
        return new DuelRequest(UUID.randomUUID(), sender, target, kitName, ranked, now, now.plusSeconds(ttlSeconds));
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
