package com.rumilance.practice.ffa;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules of the FreeHit 対策, without a server: {@link FfaFreeHitGuard} takes the clock as an
 * argument, so the 10s provisional window and the 30s idle timeout are just arithmetic here.
 *
 * <p>What each verdict means for the player is enforced one layer up, in {@code FfaListener}
 * (cancel the event, replay the hurt sound); what it means for the fight is decided here.</p>
 */
final class FfaFreeHitGuardTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long T0 = 1_000_000L;

    @Test
    void aCheapShotIsAFreeHitThatStartsNothing() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();

        assertEquals(FfaFreeHitGuard.Verdict.FREE_HIT, guard.evaluate(A, B, T0));
        assertFalse(guard.registerFreeHit(A, B, T0), "no combat until the other player hits back");
        assertNull(guard.partnerOf(A, T0), "the provisional combat is not a pairing");

        assertEquals(FfaFreeHitGuard.Verdict.FREE_HIT, guard.evaluate(A, B, T0 + 1_000L),
                "a second cheap shot is still a free hit");
    }

    @Test
    void hittingBackInsideTheTenSecondWindowStartsCombat() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);

        long hitBack = T0 + 9_000L;
        assertTrue(guard.registerFreeHit(B, A, hitBack), "B answers inside the window");

        assertEquals(B, guard.partnerOf(A, hitBack));
        assertEquals(A, guard.partnerOf(B, hitBack));
        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(A, B, hitBack));
        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(B, A, hitBack));
    }

    @Test
    void hittingBackAfterTheWindowDoesNothing() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);

        long tooLate = T0 + 10_001L;
        assertFalse(guard.registerFreeHit(B, A, tooLate), "the provisional combat has lapsed");
        assertNull(guard.partnerOf(A, tooLate));
        assertNull(guard.partnerOf(B, tooLate));
    }

    @Test
    void combatLocksThePairToEachOtherOnly() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);
        guard.registerFreeHit(B, A, T0);
        long t = T0;

        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(A, B, t));
        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(B, A, t));
        assertEquals(FfaFreeHitGuard.Verdict.BLOCKED, guard.evaluate(A, C, t), "A may only hit B");
        assertEquals(FfaFreeHitGuard.Verdict.BLOCKED, guard.evaluate(B, C, t), "B may only hit A");
        assertEquals(FfaFreeHitGuard.Verdict.BLOCKED, guard.evaluate(C, A, t), "nobody else may hit A");
        assertEquals(FfaFreeHitGuard.Verdict.BLOCKED, guard.evaluate(C, B, t), "nobody else may hit B");
    }

    @Test
    void combatEndsThirtySecondsAfterTheLastLandedHit() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);
        guard.registerFreeHit(B, A, T0);

        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(A, B, T0 + 29_000L),
                "still locked together before the idle timeout");
        assertNull(guard.partnerOf(A, T0 + 30_001L), "30s of nothing releases A");
        assertNull(guard.partnerOf(B, T0 + 30_001L), "and releases B with it");
        assertEquals(FfaFreeHitGuard.Verdict.FREE_HIT, guard.evaluate(A, B, T0 + 30_001L),
                "afterwards they are just two players again");
    }

    @Test
    void aLandedHitPushesTheTimeoutOut() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);
        guard.registerFreeHit(B, A, T0);

        long later = T0 + 20_000L;
        guard.noteLandedHit(A, B, later);

        assertEquals(FfaFreeHitGuard.Verdict.ALLOW, guard.evaluate(A, B, later + 29_000L));
        assertNull(guard.partnerOf(A, later + 30_001L));
    }

    @Test
    void dyingOrLeavingReleasesTheSurvivor() {
        FfaFreeHitGuard guard = new FfaFreeHitGuard();
        guard.registerFreeHit(A, B, T0);
        guard.registerFreeHit(B, A, T0);

        guard.clear(A);

        assertNull(guard.partnerOf(B, T0), "B is free the moment A is gone");
        assertEquals(FfaFreeHitGuard.Verdict.FREE_HIT, guard.evaluate(B, C, T0),
                "and can start over against someone else");
    }
}
