package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A generic administrative/audit trail entry (e.g. arena edits, punishment actions, reloads).
 */
public record AuditLogEntry(UUID id, UUID actorUuid, String action, String details, Instant createdAt) {

    public AuditLogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static AuditLogEntry of(UUID actorUuid, String action, String details) {
        return new AuditLogEntry(UUID.randomUUID(), actorUuid, action, details, Instant.now());
    }

    public Optional<UUID> actor() {
        return Optional.ofNullable(actorUuid);
    }
}
