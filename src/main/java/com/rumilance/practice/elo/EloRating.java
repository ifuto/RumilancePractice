package com.rumilance.practice.elo;

/**
 * Immutable snapshot of a player's rating and experience for a single ranked kit,
 * sufficient to compute the next Elo update.
 *
 * @param rating      current Elo rating, never negative.
 * @param gamesPlayed total number of rated games played with this kit, never negative.
 */
public record EloRating(int rating, int gamesPlayed) {

    public EloRating {
        if (rating < 0) {
            throw new IllegalArgumentException("rating must not be negative: " + rating);
        }
        if (gamesPlayed < 0) {
            throw new IllegalArgumentException("gamesPlayed must not be negative: " + gamesPlayed);
        }
    }

    public static EloRating starting(int startingRating) {
        return new EloRating(startingRating, 0);
    }
}
