package com.rumilance.practice.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule behind the Arena Paste Queue: two copies may be pasted at once, the third and later
 * ones of a burst wait and are released in order. {@link ArenaPasteQueue} keeps no reference to
 * Bukkit, so the queueing can be checked without a server.
 */
final class ArenaPasteQueueTest {

    @Test
    void theFirstTwoPastesStartImmediately() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(2);

        assertTrue(queue.tryStart("a"));
        assertTrue(queue.tryStart("b"));
        assertEquals(2, queue.active());
        assertEquals(0, queue.queued());
    }

    @Test
    void theThirdPasteOfABurstWaits() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(2);
        queue.tryStart("a");
        queue.tryStart("b");

        assertFalse(queue.tryStart("c"), "the third copy must not start alongside the first two");
        assertEquals(1, queue.queued());
        assertEquals(2, queue.active(), "queuing must not raise the number of running pastes");
    }

    @Test
    void aQueuedPasteTakesTheSlotThatJustFreed() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(2);
        queue.tryStart("a");
        queue.tryStart("b");
        queue.tryStart("c");

        assertSame("c", queue.onFinished(), "c starts the moment a or b is done");
        assertEquals(2, queue.active());
        assertEquals(0, queue.queued());
    }

    @Test
    void waitingPastesAreReleasedInOrder() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(2);
        queue.tryStart("a");
        queue.tryStart("b");
        queue.tryStart("c");
        queue.tryStart("d");
        queue.tryStart("e");
        assertEquals(3, queue.queued());

        assertSame("c", queue.onFinished());
        assertSame("d", queue.onFinished());
        assertSame("e", queue.onFinished());
        assertNull(queue.onFinished(), "nothing is waiting any more");
        assertNull(queue.onFinished(), "the last running paste closes out");
        assertEquals(0, queue.active());
    }

    @Test
    void theLimitIsNeverExceeded() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(2);
        for (int i = 0; i < 10; i++) {
            queue.tryStart("paste-" + i);
            if (queue.active() > queue.maxConcurrent()) {
                throw new AssertionError("active pastes exceeded the limit: " + queue.active());
            }
        }
        assertEquals(2, queue.active());
        assertEquals(8, queue.queued());
    }

    @Test
    void aSillyLimitStillMakesProgress() {
        ArenaPasteQueue<String> queue = new ArenaPasteQueue<>(0);

        assertEquals(1, queue.maxConcurrent(), "clamped so the queue can never deadlock");
        assertTrue(queue.tryStart("a"));
        assertFalse(queue.tryStart("b"));
        assertSame("b", queue.onFinished());
    }
}
