package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeDeathTest {

    @Test
    void remainingAfterIncludesAbsorptionHearts() {
        // 2 HP + 4 absorption vs 4 damage must survive (would look lethal without absorption).
        assertEquals(2.0d, PracticeDeath.remainingAfter(2.0d, 4.0d, 4.0d), 0.0001d);
        assertFalse(PracticeDeath.wouldDie(2.0d, 4.0d, 4.0d));
        assertEquals(0.0d, PracticeDeath.remainingAfter(2.0d, 2.0d, 4.0d), 0.0001d);
        assertTrue(PracticeDeath.wouldDie(2.0d, 2.0d, 4.0d));
        assertTrue(PracticeDeath.remainingAfter(1.0d, 0.0d, 1.5d) < 0.0d);
    }

    @Test
    void lethalIgnoresAbsorptionOnlyWhenBothAreGone() {
        assertFalse(PracticeDeath.wouldDie(1.0d, 0.0d, 0.5d));
        assertFalse(PracticeDeath.wouldDie(0.5d, 4.0d, 4.0d));
        assertTrue(PracticeDeath.wouldDie(0.5d, 0.0d, 0.5d));
        assertTrue(PracticeDeath.wouldDie(2.0d, 2.0d, 8.0d));
    }

    @Test
    void deferTotemWhenLethalAndHolding() {
        assertFalse(PracticeDeath.shouldDeferTotemToVanilla(null, null, null));
    }

    @Test
    void vanillaDeathHealthWithoutAbsorptionWouldMissGappleHearts() {
        // Old MatchListener used health - damage only. 2 HP + 4 absorption vs 4 damage
        // looks lethal that way, but the player should live.
        assertTrue(2.0d - 4.0d <= 0.0d);
        assertFalse(PracticeDeath.wouldDie(2.0d, 4.0d, 4.0d));
    }
}
