package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single punishment (mute/ban/warn) applied to a player for practice-related misconduct.
 */
public record PunishmentRecord(
        UUID id,
        UUID targetUuid,
        UUID staffUuid,
        String type,
        String reason,
        Instant issuedAt,
        Instant expiresAt,
        boolean revoked
) {

    public PunishmentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targetUuid, "targetUuid");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public Optional<Instant> expiresAtOptional() {
        return Optional.ofNullable(expiresAt);
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    public boolean isActive(Instant now) {
        if (revoked) {
            return false;
        }
        return isPermanent() || now.isBefore(expiresAt);
    }

    public PunishmentRecord revokedCopy() {
        return new PunishmentRecord(id, targetUuid, staffUuid, type, reason, issuedAt, expiresAt, true);
    }
}
