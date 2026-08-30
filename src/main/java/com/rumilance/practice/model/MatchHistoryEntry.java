package com.rumilance.practice.model;

import com.rumilance.practice.state.MatchMode;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of a finished match, suitable for statistics/history display.
 * {@code winner} is {@code null} for a draw.
 */
public record MatchHistoryEntry(
        UUID id,
        UUID playerA,
        UUID playerB,
        String kit,
        MatchMode mode,
        UUID winner,
        boolean ranked,
        Instant startedAt,
        Instant endedAt
) {

    public MatchHistoryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(playerA, "playerA");
        Objects.requireNonNull(playerB, "playerB");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
    }

    public Optional<UUID> winnerOptional() {
        return Optional.ofNullable(winner);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
