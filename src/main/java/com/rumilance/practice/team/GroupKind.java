package com.rumilance.practice.team;

/**
 * What a player-created group is for. A {@link #PARTY} organises around fighting other
 * parties and running tournaments over its color teams; a {@link #TEAM} is a group that
 * splits into color sides and battles internally. Both share the same member/roster
 * machinery ({@link Team}); the kind only changes the entry points and wording.
 */
public enum GroupKind {
    TEAM,
    PARTY
}
