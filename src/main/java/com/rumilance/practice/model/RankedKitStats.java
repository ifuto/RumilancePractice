package com.rumilance.practice.model;

import com.rumilance.practice.glicko.GlickoCalculator;

import java.util.Objects;
import java.util.UUID;

/**
 * Persistent ranked Glicko-2 record for a single (player, kit) pair. {@code pt} is the
 * public display rating; {@code deviation}/{@code volatility} are the hidden confidence
 * state that drives the Glicko-2 updates. Players whose deviation is still high (little
 * confidence in their PT) are excluded from leaderboards.
 */
public record RankedKitStats(UUID id, UUID uuid, String kit, int pt, double deviation,
                             double volatility, int wins, int losses, int winStreak, int bestPt) {

    public RankedKitStats {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(kit, "kit");
        if (pt < 0 || wins < 0 || losses < 0 || winStreak < 0 || bestPt < 0) {
            throw new IllegalArgumentException("RankedKitStats numeric fields must not be negative");
        }
        if (deviation <= 0.0d || volatility <= 0.0d) {
            throw new IllegalArgumentException("RankedKitStats deviation/volatility must be positive");
        }
    }

    /** Unrated record at the given Glicko-2 starting values (configured, 1500/350/0.06 by default). */
    public static RankedKitStats starting(UUID uuid, String kit, int startingRating,
                                          double startingDeviation, double startingVolatility) {
        return new RankedKitStats(UUID.randomUUID(), uuid, kit, startingRating, startingDeviation,
                startingVolatility, 0, 0, 0, startingRating);
    }

    /** Unrated record at the Glicko-2 standard defaults. */
    public static RankedKitStats starting(UUID uuid, String kit) {
        return starting(uuid, kit, GlickoCalculator.DEFAULT_RATING_INT,
                GlickoCalculator.DEFAULT_DEVIATION, GlickoCalculator.DEFAULT_VOLATILITY);
    }

    public int gamesPlayed() {
        return wins + losses;
    }

    public RankedKitStats withWin(int newPt, double newDeviation, double newVolatility) {
        return new RankedKitStats(id, uuid, kit, newPt, newDeviation, newVolatility,
                wins + 1, losses, winStreak + 1, Math.max(bestPt, newPt));
    }

    public RankedKitStats withLoss(int newPt, double newDeviation, double newVolatility) {
        return new RankedKitStats(id, uuid, kit, newPt, newDeviation, newVolatility,
                wins, losses + 1, 0, Math.max(bestPt, newPt));
    }

    public RankedKitStats withDraw(int newPt, double newDeviation, double newVolatility, boolean countAsLoss) {
        if (countAsLoss) {
            return new RankedKitStats(id, uuid, kit, newPt, newDeviation, newVolatility,
                    wins, losses + 1, 0, Math.max(bestPt, newPt));
        }
        return new RankedKitStats(id, uuid, kit, newPt, newDeviation, newVolatility,
                wins, losses, 0, Math.max(bestPt, newPt));
    }

    /** Confidence gate for public PT rankings (see leaderboard-max-deviation setting). */
    public boolean isLeaderboardEligible(double maxDeviation) {
        return GlickoCalculator.isLeaderboardEligible(deviation, maxDeviation);
    }

    public double winRate() {
        int total = gamesPlayed();
        return total == 0 ? 0.0d : (double) wins / total;
    }
}
