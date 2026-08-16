package com.rumilance.practice.state;

/**
 * Matchmaking / statistics mode. Unranked must never mutate Elo or public ranked stats.
 */
public enum MatchMode {
    RANKED,
    UNRANKED,
    FFA,
    /** RED-vs-BLUE team battle (up to 15 per side, uneven ratios allowed). No Elo changes. */
    TEAM;

    public boolean isRanked() {
        return this == RANKED;
    }

    public boolean isDuel() {
        return this == RANKED || this == UNRANKED;
    }

    public boolean isTeam() {
        return this == TEAM;
    }
}
