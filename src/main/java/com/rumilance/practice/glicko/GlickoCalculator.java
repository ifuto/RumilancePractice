package com.rumilance.practice.glicko;

/**
 * Pure, framework-agnostic Glicko-2 rating calculator used for ranked kit statistics.
 * Implements the algorithm from Mark Glickman's paper
 * ("Example of the Glicko-2 system", glicko.net/glicko/glicko2.pdf) exactly, with one game
 * per rating period, which is the standard way live servers apply it per match:
 *
 * <ul>
 *   <li>Every player starts unrated at the Glicko-2 defaults: rating
 *       {@value #DEFAULT_RATING_INT}, deviation {@value #DEFAULT_DEVIATION_INT} and
 *       volatility {@link #DEFAULT_VOLATILITY}.</li>
 *   <li>ratings/deviations are converted to the internal Glicko-2 scale with the
 *       {@value #SCALE} conversion factor, updated with the Illinois volatility iteration
 *       (system constant τ, default {@value #DEFAULT_TAU}), and converted back.</li>
 *   <li>Deviation shrinks as games are played, so new players' ratings move fast at first
 *       and settle over time — no provisional/K-factor rules needed.</li>
 *   <li>Deviation is clamped to [{@value #MIN_DEVIATION}, {@value #MAX_DEVIATION}] and the
 *       resulting display rating is floored at 0 so the public PT never goes negative.</li>
 * </ul>
 *
 * <p>This class has no dependency on Bukkit/Paper so it can be exercised by plain JUnit
 * tests.</p>
 */
public final class GlickoCalculator {

    // --- Glicko-2 standard defaults ("初期値はGlickoの標準値そのまま採用") ---
    public static final double DEFAULT_RATING = 1500.0d;
    public static final double DEFAULT_DEVIATION = 350.0d;
    public static final double DEFAULT_VOLATILITY = 0.06d;
    public static final double DEFAULT_TAU = 0.5d;

    public static final int DEFAULT_RATING_INT = 1500;
    public static final int DEFAULT_DEVIATION_INT = 350;

    /** Glicko-2 scale conversion factor. RDS(μ,φ) = 173.7178 × glicko-2 internals. */
    private static final double SCALE = 173.7178d;

    /** Practical deviation bounds: never fully certain, never more uncertain than unrated. */
    public static final double MIN_DEVIATION = 30.0d;
    public static final double MAX_DEVIATION = DEFAULT_DEVIATION;

    /** Volatility sanity clamp: avoids degenerate σ values on pathological streaks. */
    private static final double MIN_VOLATILITY = 0.03d;
    private static final double MAX_VOLATILITY = 1.0d;

    private static final double PI_SQUARED = Math.PI * Math.PI;
    private static final double EPSILON = 0.000001d;

    private final double tau;

    public GlickoCalculator() {
        this(DEFAULT_TAU);
    }

    /**
     * @param tau system constant τ constraining volatility change over time (reasonably
     *            0.3–1.2 per the paper; {@value #DEFAULT_TAU} is the standard default).
     */
    public GlickoCalculator(double tau) {
        if (tau <= 0.0d || !Double.isFinite(tau)) {
            throw new IllegalArgumentException("tau must be a positive finite number");
        }
        this.tau = tau;
    }

    public double tau() {
        return tau;
    }

    /** g(φ) from the paper (step 3): the opponent-deviation damping factor. */
    private static double g(double phi) {
        return 1.0d / Math.sqrt(1.0d + 3.0d * phi * phi / PI_SQUARED);
    }

    /** E(μ, μⱼ, φⱼ) from the paper: expected score against one opponent. */
    private static double e(double mu, double muOpponent, double phiOpponent) {
        return 1.0d / (1.0d + Math.exp(-g(phiOpponent) * (mu - muOpponent)));
    }

    /**
     * @param self     rating state of the player whose win probability is wanted.
     * @param opponent rating state of the opponent.
     * @return the expected score (win probability in [0, 1]) of {@code self} vs {@code opponent}.
     */
    public static double expectedScore(GlickoRating self, GlickoRating opponent) {
        double result = e(mu(self), mu(opponent), phi(opponent));
        // Numerical paranoia: E is a logistic and must stay inside [0, 1].
        return Math.min(1.0d, Math.max(0.0d, result));
    }

    /**
     * Applies a rated match between two players (one game per rating period) and returns
     * both players' updated rating states.
     *
     * @param playerA     rating state of player A before the match.
     * @param playerB     rating state of player B before the match.
     * @param outcomeForA match outcome from player A's perspective.
     */
    public GlickoUpdateResult applyMatch(GlickoRating playerA, GlickoRating playerB, MatchOutcome outcomeForA) {
        if (playerA == null || playerB == null || outcomeForA == null) {
            throw new IllegalArgumentException("playerA, playerB and outcomeForA must not be null");
        }
        GlickoRating newA = update(playerA, playerB, outcomeForA.scoreForA());
        GlickoRating newB = update(playerB, playerA, outcomeForA.scoreForB());
        return new GlickoUpdateResult(newA, newB);
    }

    private GlickoRating update(GlickoRating self, GlickoRating opponent, double score) {
        // Step 2: to the Glicko-2 scale.
        double muSelf = mu(self);
        double phiSelf = phi(self);
        double muOpp = mu(opponent);
        double phiOpp = phi(opponent);
        double sigma = self.volatility();

        // Steps 3-4: estimated variance (v) and improvement over expectation (delta).
        double gOpp = g(phiOpp);
        double expected = e(muSelf, muOpp, phiOpp);
        double v = 1.0d / (gOpp * gOpp * expected * (1.0d - expected));
        double delta = v * gOpp * (score - expected);

        // Step 5: new volatility via the Illinois iteration.
        double sigmaPrime = newVolatility(sigma, phiSelf, v, delta);

        // Step 6: pre-period deviation with volatility regression.
        double phiStar = Math.sqrt(phiSelf * phiSelf + sigmaPrime * sigmaPrime);

        // Steps 7-8: updated deviation and rating on the Glicko-2 scale, then back to display scale.
        double phiPrime = 1.0d / Math.sqrt(1.0d / (phiStar * phiStar) + 1.0d / v);
        double muPrime = muSelf + phiPrime * phiPrime * gOpp * (score - expected);

        double newRating = Math.max(0.0d, muPrime * SCALE + DEFAULT_RATING);
        double newDeviation = Math.min(MAX_DEVIATION, Math.max(MIN_DEVIATION, phiPrime * SCALE));
        double newVolatility = Math.min(MAX_VOLATILITY, Math.max(MIN_VOLATILITY, sigmaPrime));
        return new GlickoRating(newRating, newDeviation, newVolatility);
    }

    /** Step 5 of the paper: Illinois-method iteration for the new volatility σ′. */
    private double newVolatility(double sigma, double phi, double v, double delta) {
        double a = Math.log(sigma * sigma);
        double tauSquared = tau * tau;
        double phiSquared = phi * phi;
        double deltaSquared = delta * delta;

        java.util.function.DoubleUnaryOperator f = x -> {
            double expX = Math.exp(x);
            double numerator = expX * (deltaSquared - phiSquared - v - expX);
            double denominator = 2.0d * (phiSquared + v + expX) * (phiSquared + v + expX);
            return numerator / denominator - (x - a) / tauSquared;
        };

        // Initial bracket per the paper's step 5.3: A = a; B = ln(Δ²−φ²−v) when
        // Δ² > φ²+v (positive by the branch condition), otherwise B = a−kτ for the
        // smallest k with f(a−kτ) < 0.
        double upper = a;
        double lower;
        if (deltaSquared > phiSquared + v) {
            lower = Math.log(deltaSquared - phiSquared - v);
        } else {
            int k = 1;
            while (f.applyAsDouble(a - k * tau) < 0.0d) {
                k++;
            }
            lower = a - k * tau;
        }
        double fUpper = f.applyAsDouble(upper);
        double fLower = f.applyAsDouble(lower);

        // Step 5.4: iterate until the bracket closes within ε, then σ′ = e^(A/2).
        while (Math.abs(lower - upper) > EPSILON) {
            double next = upper + (upper - lower) * fUpper / (fLower - fUpper);
            double fNext = f.applyAsDouble(next);
            if (fNext * fLower < 0.0d) {
                upper = lower;
                fUpper = fLower;
            } else {
                fUpper /= 2.0d;
            }
            lower = next;
            fLower = fNext;
        }
        return Math.exp(upper / 2.0d);
    }

    private static double mu(GlickoRating rating) {
        return (rating.rating() - DEFAULT_RATING) / SCALE;
    }

    private static double phi(GlickoRating rating) {
        return rating.deviation() / SCALE;
    }

    /**
     * @return {@code true} when this rating is confident enough to be listed on a leaderboard:
     * its deviation is at most {@code maxDeviation}. Unrated/uncertain players must not
     * enter PT rankings until they have played enough to be trusted.
     */
    public static boolean isLeaderboardEligible(double deviation, double maxDeviation) {
        return deviation <= maxDeviation;
    }
}
