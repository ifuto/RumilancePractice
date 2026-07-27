package com.rumilance.practice.session;

import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSessionIdempotencyTest {

    @Test
    void resultAppliedOnlyOnce() {
        MatchSession session = new MatchSession(
                UUID.randomUUID(), MatchMode.RANKED, "nodebuff",
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), ArenaTerrain.ANY, 1
        );
        assertTrue(session.tryMarkResultApplied());
        assertFalse(session.tryMarkResultApplied());
        assertTrue(session.isResultApplied());
    }

    @Test
    void disconnectPenaltyOnlyOnceAndSkippedWhenShuttingDownFlagSeparate() {
        MatchSession session = new MatchSession(
                UUID.randomUUID(), MatchMode.UNRANKED, "nodebuff",
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                UUID.randomUUID(), ArenaTerrain.ANY, 1
        );
        session.setShuttingDown(true);
        assertTrue(session.isShuttingDown());
        assertTrue(session.tryMarkDisconnectPenalty());
        assertFalse(session.tryMarkDisconnectPenalty());
    }
}
