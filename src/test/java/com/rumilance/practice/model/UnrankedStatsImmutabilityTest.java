package com.rumilance.practice.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Confirms {@link UnrankedStats} behaves as a pure, immutable value: every "mutating" call
 * returns a distinct snapshot with the expected values, and the original instance is left
 * completely untouched.
 */
class UnrankedStatsImmutabilityTest {

    @Test
    void emptySnapshotHasZeroedFields() {
        UnrankedStats empty = UnrankedStats.empty();
        assertEquals(new UnrankedStats(0, 0, 0, 0), empty);
    }

    @Test
    void withWinReturnsNewInstanceAndLeavesOriginalUntouched() {
        UnrankedStats original = new UnrankedStats(3, 2, 0, 1);

        UnrankedStats afterWin = original.withWin();

        assertNotSame(original, afterWin);
        assertEquals(new UnrankedStats(3, 2, 0, 1), original, "original snapshot must not mutate");
        assertEquals(new UnrankedStats(4, 2, 1, 0), afterWin);
    }

    @Test
    void withLossReturnsNewInstanceAndLeavesOriginalUntouched() {
        UnrankedStats original = new UnrankedStats(5, 1, 3, 0);

        UnrankedStats afterLoss = original.withLoss();

        assertNotSame(original, afterLoss);
        assertEquals(new UnrankedStats(5, 1, 3, 0), original, "original snapshot must not mutate");
        assertEquals(new UnrankedStats(5, 2, 0, 1), afterLoss);
    }

    @Test
    void equalValueSnapshotsAreEqualAndShareHashCode() {
        UnrankedStats a = new UnrankedStats(10, 4, 2, 0);
        UnrankedStats b = new UnrankedStats(10, 4, 2, 0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void chainedTransitionsProduceExpectedFinalSnapshotWithoutAffectingIntermediates() {
        UnrankedStats zero = UnrankedStats.empty();
        UnrankedStats afterOneWin = zero.withWin();
        UnrankedStats afterTwoWins = afterOneWin.withWin();
        UnrankedStats afterLoss = afterTwoWins.withLoss();

        assertEquals(new UnrankedStats(0, 0, 0, 0), zero);
        assertEquals(new UnrankedStats(1, 0, 1, 0), afterOneWin);
        assertEquals(new UnrankedStats(2, 0, 2, 0), afterTwoWins);
        assertEquals(new UnrankedStats(2, 1, 0, 1), afterLoss);
    }

    @Test
    void winRateAndTotalsAreDerivedCorrectly() {
        UnrankedStats stats = new UnrankedStats(3, 1, 0, 0);
        assertEquals(4, stats.totalMatches());
        assertEquals(0.75d, stats.winRate(), 1e-9);
    }

    @Test
    void negativeFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UnrankedStats(-1, 0, 0, 0));
    }
}
