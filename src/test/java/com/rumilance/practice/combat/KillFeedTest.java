package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillFeedTest {

    @Test
    void hpFormatsWholeAndFractional() {
        assertEquals("20", KillFeed.formatHp(20.0d));
        assertEquals("0", KillFeed.formatHp(0.0d));
        assertEquals("18.5", KillFeed.formatHp(18.5d));
    }

    @Test
    void remainingHealthScalesToTenHearts() {
        assertEquals("10", KillFeed.formatHp(KillFeed.scaledToTen(20.0d, 20.0d)));
        assertEquals("8", KillFeed.formatHp(KillFeed.scaledToTen(16.0d, 20.0d)));
        assertEquals("5", KillFeed.formatHp(KillFeed.scaledToTen(20.0d, 40.0d)));
        assertEquals("0", KillFeed.formatHp(KillFeed.scaledToTen(0.0d, 20.0d)));
    }
}
