package com.rumilance.practice.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GuiSession is backed by a ConcurrentHashMap, which rejects null values. The
 * {@code put(key, null)} call sites (e.g. EnchantGui's "pick", EditKitGui's
 * "selected_slot") use null to mean "clear this selection", so {@code put} must
 * remove the key instead of storing null. Regression test for the NPE seen as
 * "Could not pass event InventoryClickEvent ... ConcurrentHashMap.putVal".
 */
class GuiSessionTest {

    private final GuiSession session = new GuiSession(UUID.randomUUID(), UUID.randomUUID(), GuiType.ENCHANT, 6);

    @Test
    void putNullRemovesKeyInsteadOfStoring() {
        session.put("pick", "sharpness");
        assertEquals("sharpness", session.get("pick", String.class));

        // Must not throw (regression: ConcurrentHashMap rejects null values).
        session.put("pick", null);
        assertNull(session.get("pick", String.class));
    }

    @Test
    void putRegularValueRoundTrips() {
        session.put("applied", new java.util.HashMap<String, Integer>());
        assertTrue(session.get("applied", java.util.Map.class) instanceof java.util.Map);
    }

    @Test
    void getWithWrongTypeReturnsNull() {
        session.put("bestOf", 3);
        assertNull(session.get("bestOf", String.class));
        assertEquals(3, session.get("bestOf", Integer.class));
    }

    @Test
    void getMissingKeyReturnsNull() {
        assertNull(session.get("nope", String.class));
    }

    @Test
    void putNullOnMissingKeyIsNoOp() {
        session.put("never-set", null);
        assertNull(session.get("never-set", String.class));
        assertFalse(session.attributesContain("never-set"));
    }

    @Test
    void pageClampsAtZero() {
        session.setPage(-5);
        assertEquals(0, session.page());
        session.setPage(3);
        assertEquals(3, session.page());
    }
}
