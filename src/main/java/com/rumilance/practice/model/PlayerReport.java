package com.rumilance.practice.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A single player-submitted report against the opponent of their most recent 1v1 match.
 * The compressed movement evidence lives on disk (see {@code ReportEvidenceStore}); this row
 * only holds the metadata and the path to that file.
 */
public record PlayerReport(
        UUID id,
        UUID reporterUuid,
        String reporterName,
        UUID targetUuid,
        String targetName,
        UUID matchId,
        String reason,
        String kit,
        String mode,
        String status,
        String evidencePath,
        Instant createdAt
) {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVED = "RESOLVED";

    public boolean isPending() {
        return STATUS_PENDING.equalsIgnoreCase(status);
    }
}
