package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiSlotsTest {

    @Test
    void slotComputesRowMajorIndex() {
        assertEquals(0, GuiSlots.slot(0, 0));
        assertEquals(8, GuiSlots.slot(0, 8));
        assertEquals(9, GuiSlots.slot(1, 0));
        assertEquals(22, GuiSlots.slot(2, 4));
    }

    @Test
    void slotRejectsOutOfRangeColumn() {
        assertThrows(IllegalArgumentException.class, () -> GuiSlots.slot(0, 9));
        assertThrows(IllegalArgumentException.class, () -> GuiSlots.slot(0, -1));
        assertThrows(IllegalArgumentException.class, () -> GuiSlots.slot(-1, 0));
    }

    @Test
    void rowAndColumnInvertSlot() {
        assertEquals(2, GuiSlots.row(22));
        assertEquals(4, GuiSlots.column(22));
    }

    @Test
    void isValidSlotRespectsInventoryBounds() {
        assertTrue(GuiSlots.isValidSlot(0, 27));
        assertTrue(GuiSlots.isValidSlot(26, 27));
        assertFalse(GuiSlots.isValidSlot(27, 27));
        assertFalse(GuiSlots.isValidSlot(-1, 27));
    }

    @Test
    void rangeIsInclusiveAscending() {
        assertArrayEquals(new int[]{10, 11, 12, 13}, GuiSlots.range(10, 13));
        assertArrayEquals(new int[]{5}, GuiSlots.range(5, 5));
    }

    @Test
    void rangeRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> GuiSlots.range(5, 4));
    }

    @Test
    void borderOfTwentySevenSlotInventoryHasExpectedCount() {
        // 27-slot inventory: 3 rows x 9 columns; interior is a single 1x7 strip (row 1, columns 1-7),
        // so the border covers every slot except those 7 interior ones: 27 - 7 = 20.
        int[] border = GuiSlots.border(27);
        assertEquals(20, border.length);
        assertTrue(containsAll(border, 0, 8, 9, 17, 18, 26));
        assertFalse(contains(border, 13)); // dead center, interior
    }

    @Test
    void borderRejectsNonMultipleOfRowSize() {
        assertThrows(IllegalArgumentException.class, () -> GuiSlots.border(10));
    }

    @Test
    void centerSlotOfTwentySevenIsRowOneColumnFour() {
        assertEquals(GuiSlots.slot(1, 4), GuiSlots.centerSlot(27));
    }

    @Test
    void centerSlotOfFiftyFourBiasesTopLeftOfTrueCenter() {
        // 54 slots = 6 rows; centerRow = (6-1)/2 = 2 (integer division), centerColumn = 4.
        assertEquals(GuiSlots.slot(2, 4), GuiSlots.centerSlot(54));
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAll(int[] values, int... targets) {
        for (int target : targets) {
            if (!contains(values, target)) {
                return false;
            }
        }
        return true;
    }
}
