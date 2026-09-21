package com.rumilance.practice.match;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The FT button's stepping cycle and the series-decided test. */
class FirstToTest {

    @Test
    void plainClicksWalkUpOneAtATimeFromInfinity() {
        int ft = FirstTo.UNLIMITED;
        assertEquals(1, ft = FirstTo.step(ft, FirstTo.STEP_SMALL));
        assertEquals(2, ft = FirstTo.step(ft, FirstTo.STEP_SMALL));
        assertEquals(3, ft = FirstTo.step(ft, FirstTo.STEP_SMALL));
    }

    @Test
    void shiftClicksWalkUpFiveAtATime() {
        int ft = FirstTo.UNLIMITED;
        assertEquals(5, ft = FirstTo.step(ft, FirstTo.STEP_LARGE));
        assertEquals(10, ft = FirstTo.step(ft, FirstTo.STEP_LARGE));
        assertEquals(15, FirstTo.step(ft, FirstTo.STEP_LARGE));
    }

    @Test
    void fortyWrapsToInfinityAndInfinityBackToOne() {
        assertEquals(FirstTo.UNLIMITED, FirstTo.step(FirstTo.MAX, FirstTo.STEP_SMALL));
        assertEquals(1, FirstTo.step(FirstTo.UNLIMITED, FirstTo.STEP_SMALL));
        // overshooting the cap with a shift click also lands on ∞, never on 41+
        assertEquals(FirstTo.UNLIMITED, FirstTo.step(38, FirstTo.STEP_LARGE));
        assertEquals(FirstTo.UNLIMITED, FirstTo.step(40, FirstTo.STEP_LARGE));
    }

    @Test
    void aFullCycleFromOneReturnsToInfinity() {
        int ft = 1;
        for (int i = 0; i < 39; i++) {
            ft = FirstTo.step(ft, FirstTo.STEP_SMALL);
        }
        assertEquals(40, ft);
        assertEquals(FirstTo.UNLIMITED, FirstTo.step(ft, FirstTo.STEP_SMALL));
    }

    @ParameterizedTest
    @CsvSource({"0,0,false", "0,40,false", "3,2,false", "3,3,true", "3,4,true", "1,1,true", "40,39,false",
            "40,40,true"})
    void seriesIsDecidedOnlyWhenTheLimitIsReached(int firstTo, int wins, boolean expected) {
        assertEquals(expected, FirstTo.isComplete(firstTo, wins));
    }

    @Test
    void unlimitedNeverCompletesAndLabelsAsInfinity() {
        assertFalse(FirstTo.isComplete(FirstTo.UNLIMITED, 999));
        assertEquals("∞", FirstTo.label(FirstTo.UNLIMITED));
        assertEquals("7", FirstTo.label(7));
    }

    @Test
    void normaliseClampsStoredValues() {
        assertEquals(FirstTo.UNLIMITED, FirstTo.normalise(-4));
        assertEquals(FirstTo.UNLIMITED, FirstTo.normalise(0));
        assertEquals(FirstTo.MAX, FirstTo.normalise(99));
        assertEquals(12, FirstTo.normalise(12));
    }

    @Test
    void queueStyleUnlimitedIsTheDefaultShape() {
        // Queue matches never set FT: they must stay unlimited no matter how many wins stack up.
        assertTrue(FirstTo.isComplete(FirstTo.UNLIMITED, 0) == false);
        assertTrue(FirstTo.isComplete(FirstTo.UNLIMITED, 12) == false);
        assertTrue(FirstTo.isComplete(FirstTo.UNLIMITED, 400) == false);
    }
}
