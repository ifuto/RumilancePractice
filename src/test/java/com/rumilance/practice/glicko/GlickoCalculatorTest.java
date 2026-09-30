package com.rumilance.practice.glicko;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioural tests for the Glicko-2 implementation. The reference numbers below were
 * cross-checked against the public `glicko2` Python module (py.glicko2, the de-facto
 * reference port of the paper) with one game per rating period.
 */
class GlickoCalculatorTest {

    private final GlickoCalculator calculator = new GlickoCalculator();
    private final GlickoCalculator paperTauCalculator = new GlickoCalculator(0.5);

    @Test
    void standardDefaultsMatchTheGlicko2Paper() {
        assertEquals(1500.0, GlickoCalculator.DEFAULT_RATING);
        assertEquals(350.0, GlickoCalculator.DEFAULT_DEVIATION);
        assertEquals(0.06, GlickoCalculator.DEFAULT_VOLATILITY);
        assertEquals(0.5, GlickoCalculator.DEFAULT_TAU);
        GlickoRating unrated = GlickoRating.standard();
        assertEquals(1500.0, unrated.rating());
        assertEquals(350.0, unrated.deviation());
        assertEquals(0.06, unrated.volatility());
    }

    @Test
    void winAgainstEqualOpponentMatchesReferenceImplementation() {
        // Reference (py.glicko2): 1500/350/0.06 beats 1500/350 -> 1662.31 / 290.32.
        GlickoUpdateResult result = calculator.applyMatch(
                GlickoRating.standard(), GlickoRating.standard(), MatchOutcome.WIN);
        assertEquals(1662.31, result.newA().rating(), 0.3);
        assertEquals(290.32, result.newA().deviation(), 0.3);
        // Loser mirrors: 1337.69 / 290.32.
        assertEquals(1337.69, result.newB().rating(), 0.3);
        assertEquals(290.32, result.newB().deviation(), 0.3);
    }

    @Test
    void drawAgainstEqualOpponentKeepsRatingAndLowersDeviation() {
        // Reference: draw -> rating stays 1500, deviation drops to 290.32.
        GlickoUpdateResult result = calculator.applyMatch(
                GlickoRating.standard(), GlickoRating.standard(), MatchOutcome.DRAW);
        assertEquals(1500.0, result.newA().rating(), 0.01);
        assertEquals(1500.0, result.newB().rating(), 0.01);
        assertEquals(290.32, result.newA().deviation(), 0.3);
    }

    @Test
    void deviationBecomesTrustedAfterEnoughGames() {
        // Alternating W/L against equal opponents; deviation must fall below the default
        // leaderboard gate (115) near ~20 games and below 100 near 30 — the trust ramp that
        // keeps provisional players off the PT leaderboards.
        GlickoRating rating = GlickoRating.standard();
        for (int i = 0; i < 40; i++) {
            MatchOutcome outcome = i % 2 == 0 ? MatchOutcome.WIN : MatchOutcome.LOSS;
            rating = calculator.applyMatch(rating, GlickoRating.standard(), outcome).newA();
            if (i == 19) {
                assertTrue(rating.deviation() < 115.0,
                        "deviation after 20 games should pass the default gate, was " + rating.deviation());
            }
        }
        assertTrue(rating.deviation() < 90.0, "deviation after 40 games, was " + rating.deviation());
        assertTrue(rating.deviation() >= GlickoCalculator.MIN_DEVIATION,
                "deviation clamped at the floor");
        assertTrue(rating.deviation() <= GlickoCalculator.MAX_DEVIATION,
                "deviation clamped at the cap");
    }

    @Test
    void upsetAgainstMuchHigherRatedOpponentGainsMore() {
        // Beating a strong, certain opponent must pay off more than beating an equal one.
        GlickoRating self = GlickoRating.standard();
        GlickoUpdateResult beatEqual = calculator.applyMatch(self, GlickoRating.standard(), MatchOutcome.WIN);
        GlickoUpdateResult beatChampion = calculator.applyMatch(
                self, new GlickoRating(2000.0, 60.0, 0.05), MatchOutcome.WIN);
        assertTrue(beatChampion.newA().rating() > beatEqual.newA().rating());
        // And the loser side loses less when it was expected to lose.
        assertTrue(beatChampion.newB().rating() > 2000.0 - 20.0,
                "expected-loss champion should shed only a little rating");
    }

    @Test
    void expectedScoreIsBoundedAndConsistent() {
        double selfVsEqual = GlickoCalculator.expectedScore(GlickoRating.standard(), GlickoRating.standard());
        assertEquals(0.5, selfVsEqual, 1e-9);

        GlickoRating strong = new GlickoRating(2000.0, 60.0, 0.05);
        double strongVsWeak = GlickoCalculator.expectedScore(strong, GlickoRating.standard());
        double weakVsStrong = GlickoCalculator.expectedScore(GlickoRating.standard(), strong);
        assertTrue(strongVsWeak > 0.5 && strongVsWeak < 1.0);
        assertTrue(weakVsStrong < 0.5 && weakVsStrong > 0.0);

        // Glicko-2's E is damped by the *opponent's* deviation, so E_A+E_B only sums to 1
        // when both players share a deviation (equal damping). That complementary case
        // must hold exactly.
        GlickoRating a = new GlickoRating(1800.0, 80.0, 0.06);
        GlickoRating b = new GlickoRating(1500.0, 80.0, 0.06);
        assertEquals(1.0,
                GlickoCalculator.expectedScore(a, b) + GlickoCalculator.expectedScore(b, a),
                1e-9);
    }

    @Test
    void ratingCannotGoNegativeEvenAfterLongLosingStreak() {
        GlickoRating rating = GlickoRating.standard();
        GlickoRating strong = new GlickoRating(2800.0, 40.0, 0.05);
        for (int i = 0; i < 500; i++) {
            rating = calculator.applyMatch(rating, strong, MatchOutcome.LOSS).newA();
        }
        assertTrue(rating.rating() >= 0.0, "rating floored at 0, was " + rating.rating());
        assertTrue(rating.deviation() >= GlickoCalculator.MIN_DEVIATION);
    }

    @Test
    void deviationNeverExceedsTheUnratedCap() {
        GlickoRating rating = GlickoRating.standard();
        for (int i = 0; i < 100; i++) {
            rating = calculator.applyMatch(rating, GlickoRating.standard(),
                    i % 2 == 0 ? MatchOutcome.WIN : MatchOutcome.LOSS).newA();
            assertTrue(rating.deviation() <= GlickoCalculator.MAX_DEVIATION + 1e-6);
        }
    }

    @Test
    void volatilityStaysPositiveAfterWildResults() {
        // A seesaw of decisive wins/losses is the volatility-stressing scenario.
        GlickoRating rating = GlickoRating.standard();
        for (int i = 0; i < 50; i++) {
            rating = calculator.applyMatch(rating, new GlickoRating(1500.0, 30.0, 0.05),
                    i % 2 == 0 ? MatchOutcome.WIN : MatchOutcome.LOSS).newA();
            assertTrue(Double.isFinite(rating.volatility()) && rating.volatility() > 0.0);
        }
    }

    @Test
    void paperTauVariantProducesSameWinnerDirection() {
        GlickoUpdateResult result = paperTauCalculator.applyMatch(
                new GlickoRating(1400.0, 30.0, 0.06),
                new GlickoRating(1550.0, 100.0, 0.06), MatchOutcome.WIN);
        assertTrue(result.newA().rating() > 1400.0);
        assertTrue(result.newB().rating() < 1550.0);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GlickoCalculator(0.0));
        assertThrows(IllegalArgumentException.class, () -> new GlickoCalculator(-0.5));
        assertThrows(IllegalArgumentException.class,
                () -> new GlickoRating(-1.0, 350.0, 0.06));
        assertThrows(IllegalArgumentException.class,
                () -> new GlickoRating(1500.0, 0.0, 0.06));
        assertThrows(IllegalArgumentException.class,
                () -> new GlickoRating(1500.0, 350.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.applyMatch(null, GlickoRating.standard(), MatchOutcome.WIN));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.applyMatch(GlickoRating.standard(), GlickoRating.standard(), null));
    }

    @Test
    void leaderboardEligibilityIsDeviationGated() {
        assertTrue(GlickoCalculator.isLeaderboardEligible(100.0, 115.0));
        assertTrue(GlickoCalculator.isLeaderboardEligible(115.0, 115.0));
        assertFalse(GlickoCalculator.isLeaderboardEligible(115.01, 115.0));
        assertFalse(GlickoCalculator.isLeaderboardEligible(350.0, 115.0));
    }
}
