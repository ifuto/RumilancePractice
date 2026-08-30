package com.rumilance.practice.match.result;

import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchMode;

import java.util.UUID;

/**
 * FFA outcomes never affect ranked Elo/statistics.
 */
public final class FfaResultProcessor implements MatchResultProcessor {

    private final AuditLogRepository auditLogRepository;

    public FfaResultProcessor(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void process(MatchSession session, UUID winnerId, boolean draw) throws Exception {
        if (session.mode() != MatchMode.FFA) {
            throw new IllegalArgumentException("FfaResultProcessor only accepts FFA matches");
        }
        if (!session.tryMarkResultApplied()) {
            return;
        }
        auditLogRepository.insert(AuditLogEntry.of(null, "FFA_MATCH_END",
                "match=" + session.id() + ", kit=" + session.kitName()));
    }
}
