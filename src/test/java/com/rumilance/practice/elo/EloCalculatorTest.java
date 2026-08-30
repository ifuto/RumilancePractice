package com.rumilance.practice.elo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EloCalculatorTest {

    private final EloCalculator calculator = new EloCalculator();

    @Test
    void first20RankedMatchesUseKFactor64() {
        assertEquals(64, calculator.kFactorFor(0, 1000, false));
        assertEquals(64, calculator.kFactorFor(19, 1000, false));
    }

    @Test
    void from21stMatchOnwardUsesKFactor32() {
        assertEquals(32, calculator.kFactorFor(20, 1000, false));
        assertEquals(32, calculator.kFactorFor(500, 2099, false));
    }

    @Test
    void top10PercentUsesKFactor26EvenDuringProvisionalGames() {
        assertEquals(26, calculator.kFactorFor(0, 1000, true));
        assertEquals(26, calculator.kFactorFor(19, 3000, true));
        assertEquals(26, calculator.kFactorFor(1000, 3000, true));
    }

    @Test
    void expectedScoreMatchesSpecFormula() {
        double expected = 1.0d / (1.0d + Math.pow(10.0d, (1200.0d - 1000.0d) / 400.0d));
        assertEquals(expected, EloCalculator.expectedScore(1000, 1200), 1e-12);
    }

    @Test
    void expectedScoreIsSymmetricAroundOneHalf() {
        double expectedForEqualRatings = EloCalculator.expectedScore(1000, 1000);
        assertEquals(0.5d, expectedForEqualRatings, 1e-9);

        double higherRatedExpectation = EloCalculator.expectedScore(1400, 1000);
        assertTrue(higherRatedExpectation > 0.5d);
    }

    @Test
    void equalRatingWinnerGainsHalfOfKFactor() {
        // Two brand new (provisional, K=64) players at equal rating: expected score is 0.5 each,
        // so the winner should gain exactly K * (1 - 0.5) = 32 and the loser lose exactly 32.
        EloRating a = new EloRating(1000, 0);
        EloRating b = new EloRating(1000, 0);

        EloUpdateResult result = calculator.applyMatch(a, b, MatchOutcome.WIN);

        assertEquals(1032, result.newRatingA());
        assertEquals(968, result.newRatingB());
        assertEquals(32, result.deltaA());
        assertEquals(-32, result.deltaB());
        assertEquals(64, result.kFactorA());
        assertEquals(64, result.kFactorB());
    }

    @Test
    void drawBetweenEqualRatedStandardPlayersProducesNoChange() {
        EloRating a = new EloRating(1500, 50);
        EloRating b = new EloRating(1500, 50);

        EloUpdateResult result = calculator.applyMatch(a, b, MatchOutcome.DRAW);

        assertEquals(1500, result.newRatingA());
        assertEquals(1500, result.newRatingB());
        assertEquals(0, result.deltaA());
        assertEquals(0, result.deltaB());
    }

    @Test
    void topPercentPlayerUsesReducedKFactorEvenWhenWinning() {
        EloRating topPercentPlayer = new EloRating(1000, 0);
        EloRating regularOpponent = new EloRating(1000, 0);

        EloUpdateResult result = calculator.applyMatch(topPercentPlayer, true, regularOpponent, false, MatchOutcome.WIN);

        assertEquals(26, result.kFactorA());
        assertEquals(64, result.kFactorB());
        assertEquals(1013, result.newRatingA());
        assertEquals(968, result.newRatingB());
    }

    @Test
    void ratingIsFlooredAtZeroAndNeverGoesNegative() {
        // Two provisional (K=64) players at an equal, very low rating: a loss for A carries an
        // expected delta of K * (0 - 0.5) = -32, which would push a rating of 20 down to -12.
        // The result must be clamped to 0 instead of going negative.
        EloRating lowRatedA = new EloRating(20, 0);
        EloRating lowRatedB = new EloRating(20, 0);

        EloUpdateResult result = calculator.applyMatch(lowRatedA, lowRatedB, MatchOutcome.LOSS);

        assertEquals(0, result.newRatingA());
        assertTrue(result.newRatingA() >= 0);
    }

    @Test
    void higherKFactorProducesLargerSwingForSamePrediction() {
        EloRating provisionalWinner = new EloRating(1000, 0);
        EloRating provisionalLoser = new EloRating(1000, 0);
        EloUpdateResult provisionalResult = calculator.applyMatch(provisionalWinner, provisionalLoser, MatchOutcome.WIN);

        EloRating standardWinner = new EloRating(1000, 25);
        EloRating standardLoser = new EloRating(1000, 25);
        EloUpdateResult standardResult = calculator.applyMatch(standardWinner, standardLoser, MatchOutcome.WIN);

        assertTrue(Math.abs(provisionalResult.deltaA()) > Math.abs(standardResult.deltaA()));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1000, false, 64",
            "19, 1000, false, 64",
            "20, 1000, false, 32",
            "5000, 1000, false, 32",
            "0, 1000, true, 26",
            "5000, 3000, true, 26"
    })
    void kFactorSelectionMatrix(int gamesPlayed, int elo, boolean topPercent, int expectedKFactor) {
        assertEquals(expectedKFactor, calculator.kFactorFor(gamesPlayed, elo, topPercent));
    }

    @Test
    void isWithinTopPercentComputesCeilingCutoff() {
        // 100 ranked players, top 10% -> cutoff rank 10.
        assertTrue(EloCalculator.isWithinTopPercent(1, 100, 0.10d));
        assertTrue(EloCalculator.isWithinTopPercent(10, 100, 0.10d));
        assertFalse(EloCalculator.isWithinTopPercent(11, 100, 0.10d));

        // With very few players, at least the single top player still counts.
        assertTrue(EloCalculator.isWithinTopPercent(1, 3, 0.10d));
        assertFalse(EloCalculator.isWithinTopPercent(2, 3, 0.10d));

        assertFalse(EloCalculator.isWithinTopPercent(1, 0, 0.10d));
    }

    @Test
    void rejectsInvalidConstructorArguments() {
        assertThrows(IllegalArgumentException.class, () -> new EloCalculator(-1, 64, 32, 26));
        assertThrows(IllegalArgumentException.class, () -> new EloCalculator(20, 0, 32, 26));
    }

    @Test
    void rejectsNegativeEloRatingComponents() {
        assertThrows(IllegalArgumentException.class, () -> new EloRating(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EloRating(1000, -5));
    }
}
