package com.rumilance.practice.session;

import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Team colors and rematch-chain series scores live on MatchSession and are pure Java
 * (no Bukkit dependency), so they can be unit tested directly.
 */
class MatchSessionTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private MatchSession duel() {
        return new MatchSession(UUID.randomUUID(), MatchMode.RANKED, "nodebuff",
                List.of(a, b), null, 1);
    }

    @Test
    void firstParticipantIsRedSecondIsBlueNeverShared() {
        MatchSession session = duel();
        assertEquals(TeamColor.RED, session.teamColor(a));
        assertEquals(TeamColor.BLUE, session.teamColor(b));
        assertNotEquals(session.teamColor(a), session.teamColor(b));
    }

    @Test
    void teamColorIsStableAcrossRematchChain() {
        MatchSession first = duel();
        MatchSession second = duel(); // rematch reuses the same participant order
        assertEquals(first.teamColor(a), second.teamColor(a));
        assertEquals(first.teamColor(b), second.teamColor(b));
    }

    @Test
    void freshMatchHasZeroSeriesScore() {
        MatchSession session = duel();
        assertEquals(0, session.seriesWinsOf(a));
        assertEquals(0, session.seriesWinsOf(b));
        assertTrue(session.seriesWinsSnapshot().isEmpty());
    }

    @Test
    void addSeriesWinAccumulates() {
        MatchSession session = duel();
        session.addSeriesWin(a);
        session.addSeriesWin(a);
        session.addSeriesWin(b);
        assertEquals(2, session.seriesWinsOf(a));
        assertEquals(1, session.seriesWinsOf(b));
    }

    @Test
    void applySeriesCarriesScoreIntoRematch() {
        MatchSession first = duel();
        first.addSeriesWin(a);
        first.addSeriesWin(b);

        MatchSession second = duel();
        second.applySeries(first.seriesWinsSnapshot());
        assertEquals(1, second.seriesWinsOf(a));
        assertEquals(1, second.seriesWinsOf(b));
        // And it keeps accumulating.
        second.addSeriesWin(a);
        assertEquals(2, second.seriesWinsOf(a));
    }

    @Test
    void opponentOfReturnsTheOtherParticipant() {
        MatchSession session = duel();
        assertEquals(b, session.opponentOf(a));
        assertEquals(a, session.opponentOf(b));
    }

    @Test
    void resultAppliedOnlyOnce() {
        MatchSession session = duel();
        assertTrue(session.tryMarkResultApplied());
        assertFalse(session.tryMarkResultApplied());
    }

    @Test
    void endSetsStateEndingAndRecordsWinner() {
        MatchSession session = duel();
        session.markActive();
        session.end(a, false);
        assertEquals(MatchState.ENDING, session.state());
        assertEquals(a, session.winner());
        assertFalse(session.isDraw());
    }
}
