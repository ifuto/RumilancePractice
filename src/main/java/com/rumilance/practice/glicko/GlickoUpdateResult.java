package com.rumilance.practice.glicko;

/**
 * Result of applying a single rated match between two players.
 *
 * @param newA player A's rating state after the match.
 * @param newB player B's rating state after the match.
 */
public record GlickoUpdateResult(GlickoRating newA, GlickoRating newB) {

    public GlickoUpdateResult {
        if (newA == null || newB == null) {
            throw new IllegalArgumentException("newA and newB must not be null");
        }
    }
}
