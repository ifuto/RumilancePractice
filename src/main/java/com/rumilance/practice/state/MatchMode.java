package com.rumilance.practice.state;

/**
 * Matchmaking / statistics mode. Unranked must never mutate Elo or public ranked stats.
 */
public enum MatchMode {
    RANKED,
    UNRANKED,
    FFA;

    public boolean isRanked() {
        return this == RANKED;
    }

    public boolean isDuel() {
        return this == RANKED || this == UNRANKED;
    }
}
