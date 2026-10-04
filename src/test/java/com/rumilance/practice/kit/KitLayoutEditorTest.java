package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitLayoutEditorTest {

    @ParameterizedTest
    @CsvSource({
            "45, 0",   // hotbar row 5 (mockup KIT EDIT GUI)
            "53, 8",
            "18, 9",   // main inventory rows 2-4
            "44, 35",
            "0, 36",   // armor row 0: cols 0-3
            "1, 37",
            "2, 38",
            "3, 39",
            "5, 40",   // off-hand shield slot at col 5
    })
    void layoutIndexRoundTrip(int guiSlot, int layoutIndex) {
        assertEquals(layoutIndex, KitLayoutEditor.layoutIndexForGuiSlot(guiSlot));
        assertEquals(guiSlot, KitLayoutEditor.guiSlotForLayoutIndex(layoutIndex));
    }

    @ParameterizedTest
    @CsvSource({"4", "6", "7", "8", "13", "17"})
    void chromeSlotsAreNotKitSlots(int guiSlot) {
        assertEquals(-1, KitLayoutEditor.layoutIndexForGuiSlot(guiSlot));
    }

    @Test
    void hotbarMapsToRowFive() {
        for (int hot = 0; hot < 9; hot++) {
            assertEquals(hot, KitLayoutEditor.layoutIndexForGuiSlot(45 + hot));
        }
    }
}
