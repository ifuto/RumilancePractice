package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplashPotionDurationsTest {

    @Test
    void speedAmp0IsSplashDefault() {
        assertEquals(135 * 20, SplashPotionDurations.ticks("speed", 0));
        assertEquals(67 * 20, SplashPotionDurations.ticks("SPEED", 1));
    }

    @Test
    void regenerationAndPoison() {
        assertEquals(33 * 20, SplashPotionDurations.ticks("regeneration", 0));
        assertEquals(16 * 20, SplashPotionDurations.ticks("poison", 1));
    }

    @Test
    void slownessStrongUses20Seconds() {
        assertEquals(90 * 20, SplashPotionDurations.ticks("slowness", 0));
        assertEquals(20 * 20, SplashPotionDurations.ticks("slowness", 1));
    }

    @Test
    void instantEffectsAreOneTick() {
        assertEquals(1, SplashPotionDurations.ticks("instant_health", 0));
        assertEquals(1, SplashPotionDurations.ticks("heal", 1));
    }

    @Test
    void turtleMasterAndFallback() {
        assertEquals(15 * 20, SplashPotionDurations.ticks("turtle_master", 0));
        assertEquals(7 * 20, SplashPotionDurations.ticks("turtle_master", 1));
        assertEquals(45 * 20, SplashPotionDurations.ticks("resistance", 0));
    }

    @Test
    void jumpAlias() {
        assertEquals(135 * 20, SplashPotionDurations.ticks("jump", 0));
        assertEquals(67 * 20, SplashPotionDurations.ticks("jump_boost", 1));
    }
}
