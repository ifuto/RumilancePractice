package com.rumilance.practice.model;

import com.rumilance.practice.state.ArenaInstanceState;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime, mutable handle to a single arena "slot" backed by an {@link ArenaTemplate}.
 * Multiple {@code ArenaInstance}s may reference the same template so several matches can
 * run concurrently on independently regenerated copies.
 */
public final class ArenaInstance {

    private final UUID id;
    private final ArenaTemplate template;
    private volatile ArenaInstanceState state;
    private volatile UUID currentMatchId;

    public ArenaInstance(UUID id, ArenaTemplate template) {
        this.id = Objects.requireNonNull(id, "id");
        this.template = Objects.requireNonNull(template, "template");
        this.state = ArenaInstanceState.AVAILABLE;
        this.currentMatchId = null;
    }

    public UUID id() {
        return id;
    }

    public ArenaTemplate template() {
        return template;
    }

    public ArenaInstanceState state() {
        return state;
    }

    public void setState(ArenaInstanceState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
    }

    public UUID currentMatchId() {
        return currentMatchId;
    }

    public void assignMatch(UUID matchId) {
        this.currentMatchId = matchId;
        this.state = ArenaInstanceState.IN_USE;
    }

    public void release() {
        this.currentMatchId = null;
        this.state = ArenaInstanceState.AVAILABLE;
    }

    public boolean isAvailable() {
        return state == ArenaInstanceState.AVAILABLE;
    }
}
