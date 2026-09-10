package com.rumilance.practice.queue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Queue spam stamping: a sign/menu hammer feeds one accepted pulse per 800ms window, and
 * the "slow down" nag itself is also damped (once per 1500ms) so the guard cannot flood
 * the player with messages either.
 */
class QueueClickGuardTest {

    private final QueueClickGuard guard = new QueueClickGuard();
    private final UUID id = UUID.randomUUID();

    @Test
    void firstClickAlwaysPasses() {
        QueueClickGuard.Decision d = guard.evaluate(id, 1_000L);
        assertTrue(d.allowed());
        assertFalse(d.warning());
    }

    @Test
    void rapidFireIsDroppedWithOneNagOnly() {
        assertTrue(guard.evaluate(id, 0L).allowed());
        QueueClickGuard.Decision d1 = guard.evaluate(id, 100L);
        assertFalse(d1.allowed());
        assertTrue(d1.warning(), "first violation -> warn once");
        QueueClickGuard.Decision d2 = guard.evaluate(id, 200L);
        assertFalse(d2.allowed());
        assertFalse(d2.warning(), "spam loop stays silent after the first warning");
        // Clicks become legal again once the interaction window elapses.
        QueueClickGuard.Decision d3 = guard.evaluate(id, 100L + QueueClickGuard.FEEDBACK_INTERVAL_MS);
        assertTrue(d3.allowed(), "the only punishment is dropping the pulse, never the player");
        assertFalse(d3.warning());
        // ...and a fresh burst after that earns a fresh warning (nag re-armed).
        QueueClickGuard.Decision d4 = guard.evaluate(id, 100L + QueueClickGuard.FEEDBACK_INTERVAL_MS + 1);
        assertFalse(d4.allowed());
        assertTrue(d4.warning(), "nag re-arms once the feedback window has passed");
    }

    @Test
    void windowEdgesAreExclusiveOpen() {
        assertTrue(guard.evaluate(id, 0L).allowed());
        assertFalse(guard.evaluate(id, QueueClickGuard.INTERVAL_MS - 1).allowed());
        QueueClickGuard.Decision at = guard.evaluate(id, QueueClickGuard.INTERVAL_MS);
        assertTrue(at.allowed(), "exactly at the boundary a new click is accepted");
    }

    @Test
    void playersDoNotShareBudgets() {
        UUID other = UUID.randomUUID();
        assertTrue(guard.evaluate(id, 0L).allowed());
        assertTrue(guard.evaluate(other, 1L).allowed());
        assertFalse(guard.evaluate(id, 2L).allowed());
    }

    @Test
    void forgetResetsCadence() {
        assertTrue(guard.evaluate(id, 0L).allowed());
        guard.forget(id);
        assertTrue(guard.evaluate(id, 1L).allowed(), "post-forget clicks pass again");
    }

    @Test
    void nullPlayerIsUnblocked() {
        assertTrue(guard.evaluate(null, 0L).allowed());
    }
}
