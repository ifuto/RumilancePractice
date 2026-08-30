package com.rumilance.practice.state;

/**
 * Runtime availability state of a concrete {@code ArenaInstance}.
 */
public enum ArenaInstanceState {
    /** Free to be assigned to a new match. */
    AVAILABLE,
    /** Currently hosting an in-progress match. */
    IN_USE,
    /** Being reset/pasted back to its template state (typically via FAWE). */
    REGENERATING,
    /** Administratively disabled and excluded from matchmaking. */
    DISABLED
}
