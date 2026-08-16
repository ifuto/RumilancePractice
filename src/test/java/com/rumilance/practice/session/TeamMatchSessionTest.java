package com.rumilance.practice.session;

import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.TeamColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Team-battle {@link MatchSession} invariants: explicit RED/BLUE rosters, arbitrarily uneven
 * ratios (1v15 is legal), max 15 per side, no duplicate players.
 */
class TeamMatchSessionTest {

    private static List<UUID> players(int n) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(UUID.randomUUID());
        }
        return out;
    }

    private static MatchSession team(List<UUID> red, List<UUID> blue) {
        return new MatchSession(UUID.randomUUID(), MatchMode.TEAM, "nodebuff",
                red, blue, null, ArenaTerrain.ANY, 1);
    }

    @Test
    void unevenSplitIsAllowed() {
        List<UUID> red = players(1);
        List<UUID> blue = players(15);
        MatchSession session = team(red, blue);
        assertTrue(session.isTeamMatch());
        assertEquals(1, session.teamSize(TeamColor.RED));
        assertEquals(15, session.teamSize(TeamColor.BLUE));
        assertEquals(16, session.participants().size());
        assertEquals(TeamColor.RED, session.teamColor(red.get(0)));
        for (UUID b : blue) {
            assertEquals(TeamColor.BLUE, session.teamColor(b));
        }
    }

    @Test
    void sideLargerThanFifteenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> team(players(16), players(1)));
        assertThrows(IllegalArgumentException.class, () -> team(players(1), players(16)));
    }

    @Test
    void emptySideIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> team(players(0), players(3)));
        assertThrows(IllegalArgumentException.class, () -> team(players(3), players(0)));
    }

    @Test
    void duplicatePlayerAcrossSidesIsRejected() {
        UUID shared = UUID.randomUUID();
        List<UUID> red = new ArrayList<>(players(2));
        red.add(shared);
        List<UUID> blue = new ArrayList<>(players(2));
        blue.add(shared);
        assertThrows(IllegalArgumentException.class, () -> team(red, blue));
    }

    @Test
    void teammatesAndOpponents() {
        List<UUID> red = players(2);
        List<UUID> blue = players(3);
        MatchSession session = team(red, blue);
        assertTrue(session.areTeammates(red.get(0), red.get(1)));
        assertFalse(session.areTeammates(red.get(0), blue.get(0)));
        // opponentOf is 1v1-only; team matches return null by contract.
        org.junit.jupiter.api.Assertions.assertNull(session.opponentOf(red.get(0)));
        assertEquals(List.of(red.get(1)), session.teammatesOf(red.get(0)));
    }

    @Test
    void duelConstructorStillWorksAndIsNotTeamMatch() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        MatchSession session = new MatchSession(UUID.randomUUID(), MatchMode.RANKED, "nodebuff",
                List.of(a, b), null, ArenaTerrain.ANY, 1);
        assertFalse(session.isTeamMatch());
        assertEquals(TeamColor.RED, session.teamColor(a));
        assertEquals(TeamColor.BLUE, session.teamColor(b));
        assertEquals(b, session.opponentOf(a));
    }

    @Test
    void winningTeamRecordedOnEnd() {
        List<UUID> red = players(2);
        List<UUID> blue = players(2);
        MatchSession session = team(red, blue);
        session.end(blue.get(1), false);
        assertEquals(TeamColor.BLUE, session.winningTeam());
    }
}
