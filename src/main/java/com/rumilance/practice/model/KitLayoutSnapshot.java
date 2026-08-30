package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's personal, per-kit inventory layout override, persisted as a base64-encoded
 * {@code ItemSerializer} payload (see {@code com.rumilance.practice.util.ItemSerializer}).
 */
public record KitLayoutSnapshot(UUID id, UUID uuid, String kit, String itemDataBase64, Instant updatedAt) {

    public KitLayoutSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(itemDataBase64, "itemDataBase64");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static KitLayoutSnapshot create(UUID uuid, String kit, String itemDataBase64) {
        return new KitLayoutSnapshot(UUID.randomUUID(), uuid, kit, itemDataBase64, Instant.now());
    }
}
