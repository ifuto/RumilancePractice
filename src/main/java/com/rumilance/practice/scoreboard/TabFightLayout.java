package com.rumilance.practice.scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout math for the fight TAB columns — kept free of Bukkit types so the column
 * arithmetic can be unit tested directly (see {@code TabFightLayoutTest}).
 *
 * <p>Every column has the same skeleton: a styled header row, one blank spacer row, the
 * roster, then blank padding until the column fills a multiple of {@link #ROWS_PER_COLUMN}
 * (the vanilla client wraps into a new column every 20 entries). A 1v1 merges both fighters
 * into a single "Players in Combat" column; team fights render one column per team and
 * append the "Players in Spectating" column last.</p>
 */
public final class TabFightLayout {

    /** Vanilla wraps the tab list into a new column every 20 entries. */
    public static final int ROWS_PER_COLUMN = 20;
    /** Header row + spacer row at the top of every column. */
    public static final int HEADER_ROWS = 2;

    private TabFightLayout() {
    }

    public enum Scheme {
        /** Every team has at most one alive fighter: merge into one "Players in Combat" column. */
        DUEL,
        /** One column per team, one extra column for spectators. */
        TEAMS
    }

    /** One planned column: its roster size (real player entries) and blank pad count. */
    public static final class Column {
        private final int rosterSize;
        private final int padCount;

        public Column(int rosterSize, int padCount) {
            this.rosterSize = rosterSize;
            this.padCount = padCount;
        }

        public int rosterSize() {
            return rosterSize;
        }

        public int padCount() {
            return padCount;
        }

        /** Total rows this column occupies on the client (header + spacer + roster + pads). */
        public int rows() {
            return HEADER_ROWS + rosterSize + padCount;
        }
    }

    /** Duel layout when no team currently holds more than one alive fighter. */
    public static Scheme schemeFor(List<Integer> rosterSizes) {
        for (int size : rosterSizes) {
            if (size > 1) {
                return Scheme.TEAMS;
            }
        }
        return Scheme.DUEL;
    }

    /**
     * Blank pads after header (2) + roster so the column ends exactly on a 20-row boundary.
     * Returns 0 when the column already fills whole client columns.
     */
    public static int padCount(int rosterSize) {
        if (rosterSize < 0) {
            throw new IllegalArgumentException("rosterSize must be >= 0");
        }
        return (ROWS_PER_COLUMN - (HEADER_ROWS + rosterSize) % ROWS_PER_COLUMN) % ROWS_PER_COLUMN;
    }

    /**
     * Plans all columns for one match. {@code teamRosterSizes} must be in display order
     * (canonical team order); {@code spectators} adds the trailing spectating column.
     *
     * <p>The returned list count matches the number of priority bands the service must
     * assign: duel = 1 combat column (+ spectating), team fight = one per team (+ spectating).</p>
     */
    public static List<Column> plan(List<Integer> teamRosterSizes, int spectators) {
        List<Column> columns = new ArrayList<>();
        if (schemeFor(teamRosterSizes) == Scheme.DUEL) {
            int duelSize = 0;
            for (int size : teamRosterSizes) {
                duelSize += size;
            }
            if (duelSize > 0) {
                columns.add(new Column(duelSize, padCount(duelSize)));
            }
        } else {
            for (int size : teamRosterSizes) {
                if (size > 0) {
                    columns.add(new Column(size, padCount(size)));
                }
            }
        }
        if (spectators > 0) {
            columns.add(new Column(spectators, padCount(spectators)));
        }
        return columns;
    }
}
