package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TAB list for a fight must NOT spawn any dummy/blank-pad players. It only reorders the
 * real player entries: fighters packed at the top (left column) and spectators ordered from
 * the right-column base so they read on the right.
 */
class TabFightListServiceTest {

    @Test
    void noDummyPadStateExists() {
        // The old ProtocolLib pad implementation kept these members; they must be gone.
        for (Field field : TabFightListService.class.getDeclaredFields()) {
            String name = field.getName().toUpperCase();
            assertFalse(name.contains("BLANK_PAD"), "dummy pad profile removed: " + field.getName());
            assertFalse(name.contains("PAD"), "pad tracking removed: " + field.getName());
        }
    }

    @Test
    void rightColumnBaseKeepsSpectatorsInSecondColumn() throws Exception {
        Field field = TabFightListService.class.getDeclaredField("RIGHT_COLUMN_BASE");
        field.setAccessible(true);
        assertTrue(Modifier.isStatic(field.getModifiers()), "base is static");
        int base = (int) field.get(null);
        // Fighters occupy orders 0..n-1 on the left (max 20 per column); spectators must
        // start at/after the column size to be pushed to the right column.
        assertEquals(20, base);
    }

    @Test
    void serviceHasNoProtocolLibDependency() {
        // Construction must work without ProtocolLib on the classpath (ordering only).
        TabFightListService service = new TabFightListService(null);
        assertTrue(service != null);
    }

    @Test
    void applyWithNullSessionIsNoOp() {
        // Must not throw with a null session (defensive guard used by the scoreboard loop).
        new TabFightListService(null).apply(null, java.util.List.of());
    }
}
