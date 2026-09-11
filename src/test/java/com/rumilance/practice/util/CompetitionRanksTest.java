package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CompetitionRanksTest {

    @Test
    void distinctValuesRankOneToN() {
        assertArrayEquals(new int[]{1, 2, 3, 4},
                CompetitionRanks.ranks(new long[]{10, 8, 5, 1}));
    }

    @Test
    void tiesShareRankAndNextValueJumps() {
        assertArrayEquals(new int[]{1, 2, 2, 4},
                CompetitionRanks.ranks(new long[]{10, 8, 8, 5}));
    }

    @Test
    void threeWayTieSharesRank() {
        assertArrayEquals(new int[]{1, 2, 2, 2, 5},
                CompetitionRanks.ranks(new long[]{10, 7, 7, 7, 5}));
    }

    @Test
    void allEqualValuesShareFirstPlace() {
        assertArrayEquals(new int[]{1, 1, 1},
                CompetitionRanks.ranks(new long[]{0, 0, 0}));
    }

    @Test
    void leadingTieThenDistinct() {
        assertArrayEquals(new int[]{1, 1, 3},
                CompetitionRanks.ranks(new long[]{9, 9, 8}));
    }

    @Test
    void emptyInputProducesEmptyOutput() {
        assertArrayEquals(new int[0], CompetitionRanks.ranks(new long[0]));
    }
}
