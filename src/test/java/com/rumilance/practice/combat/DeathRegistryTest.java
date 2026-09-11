package com.rumilance.practice.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death-catch marker store: exactly-once consumption is what makes a ruling unable to
 * double-fire, and disconnecting players must never leak a stale plan into the next life.
 */
class DeathRegistryTest {

    @Test
    void markThenConsumeIsExactlyOnce() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        UUID id = UUID.randomUUID();
        registry.mark(id, "plan-A");
        assertTrue(registry.isMarked(id));
        assertEquals("plan-A", registry.consume(id));
        assertNull(registry.consume(id), "second consume must find nothing");
        assertFalse(registry.isMarked(id));
    }

    @Test
    void peekDoesNotConsume() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        UUID id = UUID.randomUUID();
        registry.mark(id, "plan-B");
        assertSame("plan-B", registry.peek(id));
        assertSame("plan-B", registry.peek(id));
        assertTrue(registry.isMarked(id));
    }

    @Test
    void remarkOverwritesThePreviousPlan() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        UUID id = UUID.randomUUID();
        registry.mark(id, "old");
        registry.mark(id, "new");
        assertEquals("new", registry.consume(id));
        assertNull(registry.consume(id));
    }

    @Test
    void clearDropsWithoutRunning() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        UUID id = UUID.randomUUID();
        registry.mark(id, "plan");
        registry.clear(id);
        assertFalse(registry.isMarked(id));
        assertNull(registry.consume(id));
        registry.clear(id); // idempotent
    }

    @Test
    void nullKeyAndPayloadAreSilentNoOps() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        registry.mark(null, "x");
        registry.mark(UUID.randomUUID(), null);
        assertEquals(0, registry.size(), "broken callers must not poison the store");
        assertFalse(registry.isMarked(null));
        assertNull(registry.peek(null));
        assertNull(registry.consume(null));
        registry.clear(null);
    }

    @Test
    void playersAreIsolated() {
        DeathRegistry<String> registry = new DeathRegistry<>();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        registry.mark(a, "A");
        registry.mark(b, "B");
        assertEquals("A", registry.consume(a));
        assertTrue(registry.isMarked(b));
        assertEquals("B", registry.consume(b));
    }
}
