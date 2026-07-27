package com.rumilance.practice.model;

/**
 * Immutable snapshot of a player's unranked win/loss record. Every "mutation" returns a
 * brand new instance, leaving the original snapshot untouched - this makes the type trivially
 * safe to share/cache and easy to unit test for immutability via record equality.
 */
public record UnrankedStats(int wins, int losses, int winStreak, int lossStreak) {

    public UnrankedStats {
        if (wins < 0 || losses < 0 || winStreak < 0 || lossStreak < 0) {
            throw new IllegalArgumentException("UnrankedStats fields must not be negative");
        }
    }

    public static UnrankedStats empty() {
        return new UnrankedStats(0, 0, 0, 0);
    }

    public UnrankedStats withWin() {
        return new UnrankedStats(wins + 1, losses, winStreak + 1, 0);
    }

    public UnrankedStats withLoss() {
        return new UnrankedStats(wins, losses + 1, 0, lossStreak + 1);
    }

    public int totalMatches() {
        return wins + losses;
    }

    public double winRate() {
        int total = totalMatches();
        return total == 0 ? 0.0d : (double) wins / total;
    }
}
