package com.rumilance.practice.skill;

/**
 * OpenSkill-style Gaussian rating. {@code mu} is playing strength; {@code sigma} is uncertainty.
 */
public record SkillRating(double mu, double sigma, int gamesPlayed, int winStreak) {

    public SkillRating {
        if (mu < 0.0d || sigma < 0.0d) {
            throw new IllegalArgumentException("mu/sigma must not be negative");
        }
        if (gamesPlayed < 0 || winStreak < 0) {
            throw new IllegalArgumentException("games/streak must not be negative");
        }
    }

    public int displayPoints() {
        return SkillCalculator.displayPoints(mu, sigma);
    }

    public SkillTier tier() {
        return SkillTier.of(this);
    }
}
