package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure (Bukkit-free) sanity checks for the menu layout arithmetic. The {@link MenuScaffold}
 * methods that touch an {@link org.bukkit.inventory.Inventory} cannot run without a server,
 * but the content-grid mapping is plain integer math and is cheap to lock down.
 */
class MenuScaffoldTest {

    @Test
    void gridPageSizeIsTwentyEight() {
        assertEquals(28, MenuScaffold.gridPageSize());
    }

    @Test
    void firstGridSlotIsRowOneColumnOne() {
        assertEquals(GuiSlots.slot(1, 1), MenuScaffold.gridSlot(0));
    }

    @Test
    void gridSlotWrapsAtSevenColumnsAcrossFourRows() {
        // Row 1 spans indices 0..6, row 2 spans 7..13, etc.
        assertEquals(GuiSlots.slot(1, 7), MenuScaffold.gridSlot(6));
        assertEquals(GuiSlots.slot(2, 1), MenuScaffold.gridSlot(7));
        assertEquals(GuiSlots.slot(4, 7), MenuScaffold.gridSlot(27));
    }

    @Test
    void allGridSlotsLieWithinTheContentFrame() {
        for (int i = 0; i < MenuScaffold.gridPageSize(); i++) {
            int slot = MenuScaffold.gridSlot(i);
            int row = GuiSlots.row(slot);
            int col = GuiSlots.column(slot);
            assertTrue(row >= 1 && row <= 4, "row out of range for index " + i);
            assertTrue(col >= 1 && col <= 7, "column out of range for index " + i);
        }
    }

    @Test
    void gridSlotsAreUnique() {
        long distinct = java.util.stream.IntStream.range(0, MenuScaffold.gridPageSize())
                .map(MenuScaffold::gridSlot)
                .distinct()
                .count();
        assertEquals(MenuScaffold.gridPageSize(), distinct);
    }
}
