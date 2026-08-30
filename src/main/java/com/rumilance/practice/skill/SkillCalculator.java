package com.rumilance.practice.skill;

/**
 * OpenSkill / Weng-Lin Bradley-Terry 1v1 with anti-farm and conservative display.
 *
 * <p>Improvements over vanilla OpenSkill:</p>
 * <ul>
 *   <li>Heavy favourite wins barely move {@code mu} (farm dampening + streak decay).</li>
 *   <li>Farm matches also slow {@code sigma} collapse so certainty cannot be farmed.</li>
 *   <li>Upsets still boost the underdog so genuine skill climbs.</li>
 *   <li>Per-match {@code mu} shift is capped.</li>
 *   <li>Public points / tiers use ordinal {@code μ ∁E3σ} (TrueSkill-style), not raw {@code μ}.</li>
 * </ul>
 */
public final class SkillCalculator {

    public static final double DEFAULT_MU = 1000.0d;
    public static final double DEFAULT_SIGMA = 250.0d;
    public static final double BETA = DEFAULT_SIGMA / 2.0d;
    public static final double TAU = DEFAULT_SIGMA / 100.0d;
    public static final double SIGMA_MIN = 55.0d;
    public static final double KAPPA = 0.0001d;
    /** Favourites above this pre-match win probability get farm-dampened. */
    public static final double FARM_P = 0.68d;
    public static final double MIN_FARM = 0.04d;
    /** Absolute |Δμ| ceiling per rated match (before flooring at 0). */
    public static final double MAX_MU_DELTA = 48.0d;
    /** Ordinal coefficient: display / tier uses μ ∁EZ·ρE */
    public static final double ORDINAL_Z = 3.0d;

    private SkillCalculator() {
    }

    public static SkillRating starting() {
        return new SkillRating(DEFAULT_MU, DEFAULT_SIGMA, 0, 0);
    }

    /** Conservative skill points for leaderboards and {@link SkillTier}. */
    public static int ordinalPoints(double mu, double sigma) {
        return (int) Math.max(0L, Math.round(mu - ORDINAL_Z * Math.max(0.0d, sigma)));
    }

    public static int ordinalPoints(SkillRating rating) {
        return ordinalPoints(rating.mu(), rating.sigma());
    }

    /**
     * Public display rating. Uses ordinal so high-ρEsmurfs and farmed μ do not look elite.
     */
    public static int displayPoints(double mu) {
        // Legacy callers without sigma: treat as settled (ρE≁ESIGMA_MIN).
        return ordinalPoints(mu, SIGMA_MIN);
    }

    public static int displayPoints(double mu, double sigma) {
        return ordinalPoints(mu, sigma);
    }

    public static SkillUpdate apply(SkillRating playerA, SkillRating playerB, boolean aWon, boolean draw) {
        if (playerA == null || playerB == null) {
            throw new IllegalArgumentException("ratings must not be null");
        }
        double sigmaA = Math.hypot(playerA.sigma(), TAU);
        double sigmaB = Math.hypot(playerB.sigma(), TAU);
        double c = Math.sqrt(sigmaA * sigmaA + sigmaB * sigmaB + 2.0d * BETA * BETA);
        double pA = 1.0d / (1.0d + Math.exp((playerB.mu() - playerA.mu()) / c));
        double pB = 1.0d - pA;
        double sA = draw ? 0.5d : (aWon ? 1.0d : 0.0d);
        double sB = 1.0d - sA;

        double omegaA = (sigmaA * sigmaA / c) * (sA - pA);
        double omegaB = (sigmaB * sigmaB / c) * (sB - pB);
        double gammaA = sigmaA / c;
        double gammaB = sigmaB / c;
        double deltaA = gammaA * (sigmaA * sigmaA) / (c * c) * pA * pB;
        double deltaB = gammaB * (sigmaB * sigmaB) / (c * c) * pA * pB;

        double farm = 1.0d;
        if (!draw) {
            double favouriteP = aWon ? pA : pB;
            int streak = aWon ? playerA.winStreak() : playerB.winStreak();
            farm = farmFactor(favouriteP, streak);
            if (favouriteP < 0.40d) {
                // Upset: underdog climbs faster so true skill still surfaces.
                double boost = 1.0d + (0.40d - favouriteP) * 1.35d;
                if (aWon) {
                    omegaA *= boost;
                } else {
                    omegaB *= boost;
                }
            }
            // Expected blowout: also shrink the favourite's sigma collapse (farm certainty).
            deltaA *= Math.max(farm, 0.15d);
            deltaB *= Math.max(farm, 0.15d);
        }
        omegaA = clampDelta(omegaA * farm);
        omegaB = clampDelta(omegaB * farm);

        double muA = Math.max(0.0d, playerA.mu() + omegaA);
        double muB = Math.max(0.0d, playerB.mu() + omegaB);
        double nextSigmaA = nextSigma(sigmaA, deltaA);
        double nextSigmaB = nextSigma(sigmaB, deltaB);
        return new SkillUpdate(
                new SkillRating(muA, nextSigmaA, playerA.gamesPlayed() + 1, draw || !aWon ? 0 : playerA.winStreak() + 1),
                new SkillRating(muB, nextSigmaB, playerB.gamesPlayed() + 1, draw || aWon ? 0 : playerB.winStreak() + 1),
                farm,
                pA
        );
    }

    /**
     * @param favouriteP pre-match P(winner beats loser); ignored for draws
     * @param winnerStreak consecutive wins the favourite already has
     */
    public static double farmFactor(double favouriteP, int winnerStreak) {
        if (favouriteP <= FARM_P) {
            return 1.0d;
        }
        double span = Math.max(1.0e-6d, 1.0d - FARM_P);
        double factor = Math.pow((1.0d - favouriteP) / span, 1.55d);
        factor = Math.max(MIN_FARM, Math.min(1.0d, factor));
        if (winnerStreak >= 2 && favouriteP > 0.62d) {
            factor *= Math.pow(0.68d, winnerStreak - 1);
            factor = Math.max(MIN_FARM, factor);
        }
        return factor;
    }

    static double clampDelta(double omega) {
        if (omega > MAX_MU_DELTA) {
            return MAX_MU_DELTA;
        }
        if (omega < -MAX_MU_DELTA) {
            return -MAX_MU_DELTA;
        }
        return omega;
    }

    static double nextSigma(double sigma, double delta) {
        double variance = sigma * sigma * Math.max(KAPPA, 1.0d - delta);
        return Math.max(SIGMA_MIN, Math.sqrt(Math.max(KAPPA, variance)));
    }
}
