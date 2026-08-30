package com.rumilance.practice.state;

/**
 * Lifecycle of a single duel/FFA match session (spec MatchState).
 */
public enum MatchState {
    CREATED,
    RESERVING_ARENA,
    PASTING_ARENA,
    WAITING_FOR_PLAYERS,
    COUNTDOWN,
    ACTIVE,
    ENDING,
    CLEANING,
    CLOSED,
    FAILED
}
