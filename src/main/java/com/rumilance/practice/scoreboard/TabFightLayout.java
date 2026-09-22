package com.rumilance.practice.scoreboard;

import java.util.List;

/**
 * Pure layout arithmetic for the fight TAB grid — kept free of Bukkit/Adventure types so the
 * column maths can be unit tested directly (see {@code TabFightLayoutTest}).
 *
 * <p><b>Why every column is padded to 20 rows.</b> The client owns the player-list layout: it
 * sorts the entries by the server-provided list order (highest first) and starts a new column
 * on the 21st entry — that is the client's own column width, the server cannot place a row
 * anywhere else. A three-player team therefore shares one column with the next team unless the
 * remaining rows are filled with throw-away entries. TAB's layout feature works the same way
 * ("the first column is 1-20", 80 slots = 4 columns), and its slot numbering is copied here:
 * {@code slot = column * 20 + row}.</p>
 */
public final class TabFightLayout {

    /** The vanilla client wraps the player list into a new column every 20 entries. */
    public static final int SLOTS_PER_COLUMN = 20;
    /** Header row + blank spacer row at the top of every column. */
    public static final int HEADER_ROWS = 2;
    /**
     * Member rows that fit under the header/spacer inside one client column. A team roster of
     * 20 (the side cap) exceeds this by 2, so oversized columns are shown as a rotating window
     * of exactly this many rows — an auto-looping "infinite scroll" of the team list.
     */
    public static final int ROSTER_ROWS_PER_COLUMN = SLOTS_PER_COLUMN - HEADER_ROWS;
    /** One member-row advance of the auto-loop every N ticks (60 = 3 seconds). */
    public static final int ROTATE_EVERY_TICKS = 60;
    /** Hard cap on the client columns one match may occupy (7 team columns + spectators + slack). */
    public static final int MAX_COLUMNS = 16;

    private TabFightLayout() {
    }

    /** Which column layout a match uses. */
    public enum Scheme {
        /** 1v1: both fighters share the merged "In-Game Players" column. */
        DUEL,
        /** Party fight: one column per team. */
        TEAMS
    }

    /**
     * {@link Scheme#DUEL} only for a real 1v1 — a match that is not a team match and whose
     * teams each hold at most one participant. Everything else renders one column per team.
     */
    public static Scheme schemeFor(boolean teamMatch, List<Integer> rosterSizes) {
        if (teamMatch) {
            return Scheme.TEAMS;
        }
        for (int size : rosterSizes) {
            if (size > 1) {
                return Scheme.TEAMS;
            }
        }
        return Scheme.DUEL;
    }

    /**
     * Start index (0-based) of the auto-loop window for a roster of {@code size} rows at
     * {@code tick}. Returns 0 while the roster fits in one column; otherwise the window slides
     * one row every {@link #ROTATE_EVERY_TICKS} ticks and wraps around — exactly one client
     * column tall, reading like an infinite button-less scroll. Pure arithmetic so it can be
     * unit tested without a server.
     */
    public static int rotateStart(int size, long tick) {
        int fit = ROSTER_ROWS_PER_COLUMN;
        if (size <= fit) {
            return 0;
        }
        int windowCount = Math.max(1, size - (fit - 1));
        return (int) Math.floorMod(tick / Math.max(1L, (long) ROTATE_EVERY_TICKS), (long) windowCount);
    }

    /** Blank rows after {@code contentRows} so the column ends exactly on a 20-row boundary. */
    public static int padCount(int contentRows) {
        if (contentRows < 0) {
            throw new IllegalArgumentException("contentRows must be >= 0");
        }
        return (SLOTS_PER_COLUMN - (HEADER_ROWS + contentRows) % SLOTS_PER_COLUMN) % SLOTS_PER_COLUMN;
    }

    /**
     * Rows one column occupies: header + spacer + content + blanks — always a multiple of 20,
     * so the next column always starts at the top of its own client column.
     */
    public static int columnRows(int contentRows) {
        return HEADER_ROWS + contentRows + padCount(contentRows);
    }
}
