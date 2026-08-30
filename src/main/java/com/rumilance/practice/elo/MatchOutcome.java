package com.rumilance.practice.elo;

/**
 * Result of a rated match from the perspective of "player A" in a two-player comparison.
 * Deliberately framework-agnostic (no Bukkit types) so it can be unit tested in isolation.
 */
public enum MatchOutcome {
    WIN(1.0d),
    DRAW(0.5d),
    LOSS(0.0d);

    private final double score;

    MatchOutcome(double score) {
        this.score = score;
    }

    /**
     * @return the "actual score" term used by the Elo formula (1.0 for a win, 0.5 for a draw, 0.0 for a loss).
     */
    public double scoreForA() {
        return score;
    }

    public double scoreForB() {
        return 1.0d - score;
    }
}
