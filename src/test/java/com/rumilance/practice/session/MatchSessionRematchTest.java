package com.rumilance.practice.session;

import com.rumilance.practice.state.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSessionRematchTest {

    @Test
    void clearRematchRequestsResetsBothSides() {
        MatchSession session = new MatchSession(
                UUID.randomUUID(),
                MatchMode.UNRANKED,
                "nodebuff",
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                null,
                1
        );
        session.setRematchRequested(session.participants().get(0), true);
        session.setRematchRequested(session.participants().get(1), true);
        assertTrue(session.bothRematchRequested());
        session.clearRematchRequests();
        assertFalse(session.bothRematchRequested());
    }

    @Test
    void teamRematchVotesArePerSide() {
        UUID red1 = UUID.randomUUID();
        UUID red2 = UUID.randomUUID();
        UUID blue1 = UUID.randomUUID();
        MatchSession session = new MatchSession(
                UUID.randomUUID(),
                MatchMode.TEAM,
                "nodebuff",
                List.of(red1, red2),
                List.of(blue1),
                null,
                1
        );
        session.setRematchRequested(red1, true);
        assertFalse(session.bothRematchRequested());
        session.setRematchRequested(blue1, true);
        assertTrue(session.bothRematchRequested());
        session.clearRematchRequests();
        assertFalse(session.bothRematchRequested());
    }
}
