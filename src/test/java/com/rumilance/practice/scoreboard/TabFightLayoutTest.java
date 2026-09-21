package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TabFightLayoutTest {

    @Test
    void duelOnlyForAnActual1v1() {
        assertEquals(TabFightLayout.Scheme.DUEL, TabFightLayout.schemeFor(false, List.of(1, 1, 0)));
        assertEquals(TabFightLayout.Scheme.TEAMS, TabFightLayout.schemeFor(false, List.of(2, 2)));
        assertEquals(TabFightLayout.Scheme.TEAMS, TabFightLayout.schemeFor(false, List.of(1, 3, 1)));
        // A party fight stays a team grid even when it happens to be 1 player per side.
        assertEquals(TabFightLayout.Scheme.TEAMS, TabFightLayout.schemeFor(true, List.of(1, 1)));
    }

    @Test
    void everyColumnIsPaddedToOneClientColumn() {
        // 2 fighters + header/spacer = 4 rows -> 16 blank rows so the column ends on row 20.
        assertEquals(16, TabFightLayout.padCount(2));
        assertEquals(20, TabFightLayout.columnRows(2));
        // 3 spectators behind the header/spacer -> 15 blanks.
        assertEquals(15, TabFightLayout.padCount(3));
        assertEquals(20, TabFightLayout.columnRows(3));
    }

    @Test
    void fullColumnsNeedNoFillers() {
        assertEquals(0, TabFightLayout.padCount(18));
        assertEquals(20, TabFightLayout.columnRows(18));
        assertEquals(0, TabFightLayout.padCount(38));
        assertEquals(40, TabFightLayout.columnRows(38));
    }

    @Test
    void oversizedRosterSpansWholeClientColumns() {
        // 22 players: header + spacer + 22 = 24 rows -> 16 fillers -> 40 rows (two columns).
        assertEquals(16, TabFightLayout.padCount(22));
        assertEquals(40, TabFightLayout.columnRows(22));
    }

    @Test
    void emptyColumnIsStillOneClientColumn() {
        assertEquals(18, TabFightLayout.padCount(0));
        assertEquals(20, TabFightLayout.columnRows(0));
    }

    @Test
    void negativeRosterSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TabFightLayout.padCount(-1));
    }
}
