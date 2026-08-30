package com.rumilance.practice.match.result;

import com.rumilance.practice.database.repository.AuditLogRepository;
import com.rumilance.practice.model.AuditLogEntry;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchMode;

import java.util.UUID;

/**
 * Unranked (and team-battle) results must never touch Elo or public ranked statistics.
 * Team battles are audit-logged the same way — they are casual by design.
 */
public final class UnrankedResultProcessor implements MatchResultProcessor {

    private final AuditLogRepository auditLogRepository;

    public UnrankedResultProcessor(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void process(MatchSession session, UUID winnerId, boolean draw) throws Exception {
        if (session.mode() != MatchMode.UNRANKED && session.mode() != MatchMode.TEAM) {
            throw new IllegalArgumentException(
                    "UnrankedResultProcessor only accepts UNRANKED or TEAM matches");
        }
        if (!session.tryMarkResultApplied()) {
            return;
        }
        auditLogRepository.insert(new AuditLogEntry(
                UUID.randomUUID(),
                null,
                session.mode() == MatchMode.TEAM ? "TEAM_MATCH_END" : "UNRANKED_MATCH_END",
                "match=" + session.id() + ", kit=" + session.kitName()
                        + ", winner=" + winnerId + ", draw=" + draw,
                java.time.Instant.now()
        ));
    }
}
