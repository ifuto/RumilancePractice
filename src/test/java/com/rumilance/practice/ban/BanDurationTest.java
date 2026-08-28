package com.rumilance.practice.ban;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanDurationTest {

    @Test
    void autoLadderIsTwoWeeksThenTwoMonthsThenThreeThenPermanent() {
        assertEquals(Duration.ofDays(14), BanDuration.forOffenseNumber(1));
        assertEquals(Duration.ofDays(60), BanDuration.forOffenseNumber(2));
        assertEquals(Duration.ofDays(90), BanDuration.forOffenseNumber(3));
        assertNull(BanDuration.forOffenseNumber(4));
        assertTrue(BanDuration.looksLike("auto"));
        assertEquals("2 weeks", BanDuration.labelFromToken(BanDuration.autoToken(1), BanDuration.forOffenseNumber(1)));
        assertEquals("Permanent", BanDuration.labelFromToken(BanDuration.autoToken(4), BanDuration.forOffenseNumber(4)));
    }
}
