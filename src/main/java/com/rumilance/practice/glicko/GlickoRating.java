package com.rumilance.practice.glicko;

/**
 * Immutable snapshot of a player's Glicko-2 rating state for a single ranked kit.
 *
 * @param rating     display rating (the player's "PT"), never negative. Glicko-2's standard
 *                   starting value is {@value GlickoCalculator#DEFAULT_RATING_INT}.
 * @param deviation  rating deviation (RD) — how uncertain the rating is. Starts at
 *                   {@value GlickoCalculator#DEFAULT_DEVIATION} and shrinks as games are
 *                   played. High-RD players must not appear on leaderboards: their PT is
 *                   not yet trusted.
 * @param volatility rating volatility (σ) — expected fluctuation of the rating over time.
 */
public record GlickoRating(double rating, double deviation, double volatility) {

    public GlickoRating {
        if (rating < 0.0d) {
            throw new IllegalArgumentException("rating must not be negative: " + rating);
        }
        if (deviation <= 0.0d) {
            throw new IllegalArgumentException("deviation must be positive: " + deviation);
        }
        if (volatility <= 0.0d) {
            throw new IllegalArgumentException("volatility must be positive: " + volatility);
        }
    }

    /** Unrated player at the Glicko-2 standard defaults (1500 / 350 / 0.06). */
    public static GlickoRating standard() {
        return new GlickoRating(
                GlickoCalculator.DEFAULT_RATING,
                GlickoCalculator.DEFAULT_DEVIATION,
                GlickoCalculator.DEFAULT_VOLATILITY);
    }
}
