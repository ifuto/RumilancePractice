package com.rumilance.practice.ban;

import java.util.UUID;

/**
 * One ban row. {@code expiresAtEpochMilli <= 0} means permanent. Inactive rows stay for /profile history.
 */
public record BanRecord(
        UUID id,
        UUID playerId,
        String playerName,
        String reason,
        String durationLabel,
        long createdAtEpochMilli,
        long expiresAtEpochMilli,
        boolean active,
        String staffName
) {

    public boolean permanent() {
        return expiresAtEpochMilli <= 0L;
    }

    public boolean inForce(long nowEpochMilli) {
        if (!active) {
            return false;
        }
        return permanent() || expiresAtEpochMilli > nowEpochMilli;
    }
}
