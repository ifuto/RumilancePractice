package com.rumilance.practice.ffa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfaResetTimesTest {

    @Test
    void parsesOffAndUnits() {
        assertEquals(0, FfaResetTimes.parse("off").orElseThrow());
        assertEquals(30, FfaResetTimes.parse("30s").orElseThrow());
        assertEquals(300, FfaResetTimes.parse("5min").orElseThrow());
        assertEquals(7200, FfaResetTimes.parse("2hour").orElseThrow());
        assertTrue(FfaResetTimes.parse("nope").isEmpty());
        assertEquals("5 minutes", FfaResetTimes.format(300));
        assertEquals("off", FfaResetTimes.format(0));
    }
}
