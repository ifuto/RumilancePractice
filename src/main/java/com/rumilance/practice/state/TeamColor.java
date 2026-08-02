package com.rumilance.practice.state;

/**
 * Team color assigned to each participant of a duel.
 *
 * <p>The first participant (index 0) is always {@link #RED} and the second (index 1) is
 * always {@link #BLUE}, so the two fighters can never share a color ("重複禁止"). The
 * assignment is stable across a rematch chain because rematches reuse the same participant
 * order (player A/B), which keeps each player's color constant for the whole series.</p>
 */
public enum TeamColor {
    RED,
    BLUE;

    public TeamColor opposite() {
        return this == RED ? BLUE : RED;
    }
}
