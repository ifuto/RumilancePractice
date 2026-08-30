package com.rumilance.practice.session;

import com.rumilance.practice.state.PlayerState;

import java.util.UUID;

/**
 * Thrown when {@link PlayerStateManager} rejects a requested {@link PlayerState} transition
 * because it is not reachable from the player's current state.
 */
public final class IllegalStateTransitionException extends RuntimeException {

    private final UUID playerId;
    private final PlayerState from;
    private final PlayerState to;

    public IllegalStateTransitionException(UUID playerId, PlayerState from, PlayerState to) {
        super("Illegal player state transition for " + playerId + ": " + from + " -> " + to);
        this.playerId = playerId;
        this.from = from;
        this.to = to;
    }

    public UUID playerId() {
        return playerId;
    }

    public PlayerState from() {
        return from;
    }

    public PlayerState to() {
        return to;
    }
}
