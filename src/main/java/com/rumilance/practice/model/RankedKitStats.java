package com.rumilance.practice.model;

import com.rumilance.practice.elo.EloCalculator;

import java.util.Objects;
import java.util.UUID;

/**
 * Persistent ranked ELO record for a single (player, kit) pair.
 */
public record RankedKitStats(UUID id, UUID uuid, String kit, int elo, int wins, int losses, int winStreak, int bestElo) {

    public RankedKitStats {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(kit, "kit");
        if (elo < 0 || wins < 0 || losses < 0 || winStreak < 0 || bestElo < 0) {
            throw new IllegalArgumentException("RankedKitStats numeric fields must not be negative");
        }
    }

    public static RankedKitStats starting(UUID uuid, String kit) {
        return new RankedKitStats(UUID.randomUUID(), uuid, kit, EloCalculator.DEFAULT_STARTING_RATING, 0, 0, 0, EloCalculator.DEFAULT_STARTING_RATING);
    }

    public int gamesPlayed() {
        return wins + losses;
    }

    public RankedKitStats withWin(int newElo) {
        return new RankedKitStats(id, uuid, kit, newElo, wins + 1, losses, winStreak + 1, Math.max(bestElo, newElo));
    }

    public RankedKitStats withLoss(int newElo) {
        return new RankedKitStats(id, uuid, kit, newElo, wins, losses + 1, 0, Math.max(bestElo, newElo));
    }

    public RankedKitStats withDraw(int newElo, boolean countAsLoss) {
        if (countAsLoss) {
            return new RankedKitStats(id, uuid, kit, newElo, wins, losses + 1, 0, Math.max(bestElo, newElo));
        }
        return new RankedKitStats(id, uuid, kit, newElo, wins, losses, 0, Math.max(bestElo, newElo));
    }

    public double winRate() {
        int total = gamesPlayed();
        return total == 0 ? 0.0d : (double) wins / total;
    }
}
