package com.rumilance.practice.elo;

/**
 * Result of applying a single rated match between two players.
 *
 * @param newRatingA rating of player A after the match, floored at 0.
 * @param newRatingB rating of player B after the match, floored at 0.
 * @param deltaA     signed change applied to player A (may differ from a naive
 *                   {@code newRatingA - oldRatingA} only when the floor at 0 clipped the change).
 * @param deltaB     signed change applied to player B.
 * @param kFactorA   the K-factor that was used for player A.
 * @param kFactorB   the K-factor that was used for player B.
 */
public record EloUpdateResult(int newRatingA, int newRatingB, int deltaA, int deltaB, int kFactorA, int kFactorB) {
}
