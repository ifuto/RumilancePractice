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

    @Test
    void totemChecksAreNullSafe() {
        assertFalse(PracticeDeath.isTotem(null));
        assertFalse(PracticeDeath.hasTotemInHand(null));
        assertFalse(PracticeDeath.isHoldingTotem(null));
        assertFalse(PracticeDeath.canPopTotem(null, null));
        assertFalse(PracticeDeath.tryPopTotem(null, null));
        assertFalse(PracticeDeath.tryPopTotem(null, null, null));
        assertFalse(PracticeDeath.consumeTotemFromHand(null));
        assertFalse(PracticeDeath.isInResurrectGrace(null));
        // Grace / pending-window bookkeeping must tolerate a null player without throwing.
        PracticeDeath.markResurrected(null);
        PracticeDeath.clearResurrectGrace(null);
        PracticeDeath.markPendingHandTotem(null);
        PracticeDeath.clearPendingHandTotem(null);
    }

    @Test
    void deprecatedVanillaDeferralAliasesPopTheTotemThemselves() {
        // "Died with a totem in hand" came from deferring the pop to vanilla: these entry points
        // now route to tryPopTotem and must stay harmless for a null victim.
        assertFalse(PracticeDeath.shouldDeferTotemToVanilla(null, null, null));
        assertFalse(PracticeDeath.letVanillaTotemPop(null, null, null));
    }

    @Test
    void lethalIsInclusiveOfZeroRemainingHealth() {
        // Exactly 0 remaining is dead in vanilla, so the guard must pop the totem there too.
        assertTrue(PracticeDeath.wouldDie(10.0d, 0.0d, 10.0d));
        assertEquals(0.0d, PracticeDeath.remainingAfter(10.0d, 0.0d, 10.0d), 0.0001d);
        assertFalse(PracticeDeath.wouldDie(10.0d, 0.5d, 10.0d));
    }
}
