package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabFightLayoutTest {

    @Test
    void duelDetectedOnlyWhenEveryTeamIsSingle() {
        assertEquals(TabFightLayout.Scheme.DUEL, TabFightLayout.schemeFor(List.of(1, 1)));
        assertEquals(TabFightLayout.Scheme.TEAMS, TabFightLayout.schemeFor(List.of(2, 2)));
        assertEquals(TabFightLayout.Scheme.TEAMS, TabFightLayout.schemeFor(List.of(1, 3, 1)));
    }

    @Test
    void duelWithSpectatorsBuildsCombatThenSpectatingColumns() {
        List<TabFightLayout.Column> columns = TabFightLayout.plan(List.of(1, 1), 3);
        assertEquals(2, columns.size());
        // Combat column: header + spacer + 2 fighters -> 16 pads -> 20 rows exactly.
        TabFightLayout.Column combat = columns.get(0);
        assertEquals(2, combat.rosterSize());
        assertEquals(16, combat.padCount());
        assertEquals(20, combat.rows());
        // Spectating column: header + spacer + 3 spectators -> 15 pads.
        TabFightLayout.Column spectating = columns.get(1);
        assertEquals(3, spectating.rosterSize());
        assertEquals(15, spectating.padCount());
        assertEquals(20, spectating.rows());
    }

    @Test
    void duelWithoutSpectatorsHasOnlyTheCombatColumn() {
        List<TabFightLayout.Column> columns = TabFightLayout.plan(List.of(1, 1), 0);
        assertEquals(1, columns.size());
        assertEquals(20, columns.get(0).rows());
    }

    @Test
    void teamFightBuildsOneColumnPerTeamInOrderPlusSpectating() {
        List<TabFightLayout.Column> columns = TabFightLayout.plan(List.of(3, 0, 4), 2);
        assertEquals(3, columns.size());
        assertEquals(3, columns.get(0).rosterSize());
        assertEquals(15, columns.get(0).padCount());
        assertEquals(4, columns.get(1).rosterSize());
        assertEquals(14, columns.get(1).padCount());
        assertEquals(2, columns.get(2).rosterSize());
        assertEquals(16, columns.get(2).padCount());
    }

    @Test
    void bigRosterOverflowsWholeClientColumns() {
        // 22-person team: 2 header rows + 22 roster = 24 -> pads to 40 rows (2 client columns).
        assertEquals(16, TabFightLayout.padCount(22));
        List<TabFightLayout.Column> columns = TabFightLayout.plan(List.of(22, 5), 0);
        assertEquals(40, columns.get(0).rows());
    }

    @Test
    void exactFullColumnNeedsNoPads() {
        // header 2 + roster 18 = 20 exactly.
        assertEquals(0, TabFightLayout.padCount(18));
        // roster 38 fills two client columns exactly.
        assertEquals(0, TabFightLayout.padCount(38));
    }

    @Test
    void negativeRosterSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> TabFightLayout.padCount(-1));
    }

    @Test
    void emptyMatchProducesNoColumns() {
        assertTrue(TabFightLayout.plan(List.of(), 0).isEmpty());
    }
}
