package com.rumilance.practice.state;

/**
 * High-level activity state of a player while interacting with RumilancePractice.
 * Mirrors the full state list required by the plugin specification. Transitions between
 * states are validated by {@code com.rumilance.practice.session.PlayerStateManager}.
 */
public enum PlayerState {
    /** Sentinel used only for players with no active session (e.g. offline/not yet joined). */
    IDLE,
    /** Standing in the practice lobby, able to queue, duel, spectate or edit kits. */
    LOBBY,
    /** A menu-driven GUI (queue select, players list, spectate list, etc.) is open. */
    OPENING_GUI,
    /** Waiting in a ranked matchmaking queue for a specific kit. */
    QUEUED_RANKED,
    /** Waiting in an unranked matchmaking queue for a specific kit. */
    QUEUED_UNRANKED,
    /** A duel request has been sent and is awaiting the opponent's response. */
    REQUESTING_DUEL,
    /** Both participants have been matched and the arena is being reserved/prepared. */
    PREPARING_MATCH,
    /** The pre-match countdown (5-4-3-2-1-FIGHT) is running. */
    COUNTDOWN,
    /** Combat is active. */
    FIGHTING,
    /** A result has been determined and post-match effects/rewards are being applied. */
    ENDING,
    /** Watching an ongoing match without participating. */
    SPECTATING,
    /** Participating in a free-for-all arena. */
    FFA,
    /** Editing the slot layout of an official kit inside the Edit Kit GUI. */
    EDITING_KIT,
    /** Waiting in a practice room (ANKER wait hotbar / countdown). */
    PRACTICE_WAIT,
    /** Actively practicing (ANKER round or MACE session). */
    PRACTICE_ACTIVE
}
