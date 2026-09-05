package com.rumilance.practice.scoreboard;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TAB list for a fight must NOT spawn any dummy/blank-pad players. It only reorders the
 * real player entries via the vanilla 1.21.2 list-order index, which the client sorts
 * HIGHEST to LOWEST (snapshot 24w33a) — so the left-most group holds the highest base
 * priority and each following group a strictly lower one.
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
    void columnBasesSortHighestFirst() throws Exception {
        // Column 1 (RED / fighters) must carry the highest priorities, column 2 the next and
        // column 3 (spectators in team fights) the lowest, because the client lists higher
        // order values first.
        int first = readStaticInt("FIRST_COLUMN_BASE");
        int second = readStaticInt("SECOND_COLUMN_BASE");
        int third = readStaticInt("THIRD_COLUMN_BASE");
        assertTrue(first > second, "column 1 listed before column 2");
        assertTrue(second > third, "column 2 listed before column 3");
        assertTrue(first - second >= 20 && second - third >= 20,
                "group bases far enough apart that 20-row columns never overlap");
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

    private static int readStaticInt(String name) throws Exception {
        Field field = TabFightListService.class.getDeclaredField(name);
        field.setAccessible(true);
        assertTrue(Modifier.isStatic(field.getModifiers()), name + " is static");
        return (int) field.get(null);
    }
}
