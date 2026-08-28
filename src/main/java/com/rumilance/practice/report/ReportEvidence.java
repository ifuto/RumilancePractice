package com.rumilance.practice.report;

import com.rumilance.practice.match.MatchActionRecorder.Frame;

import java.util.List;
import java.util.UUID;

/**
 * Compressed movement evidence captured at report time: both participants' sampled frames plus
 * enough metadata to reconstruct the scene for replay. Persisted by {@link ReportEvidenceStore}.
 */
public record ReportEvidence(
        UUID matchId,
        String world,
        String kit,
        String mode,
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        long capturedAtEpochMilli,
        List<Frame> reporterFrames,
        List<Frame> targetFrames
) {
}
