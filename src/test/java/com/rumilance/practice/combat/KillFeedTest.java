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
}
