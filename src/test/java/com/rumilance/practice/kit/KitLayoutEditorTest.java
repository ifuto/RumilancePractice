package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KitLayoutEditorTest {

    @ParameterizedTest
    @CsvSource({
            "36, 0",
            "44, 8",
            "9, 9",
            "35, 35",
            "1, 36",
            "2, 37",
            "3, 38",
            "4, 39",
            "6, 40",
    })
    void layoutIndexRoundTrip(int guiSlot, int layoutIndex) {
        assertEquals(layoutIndex, KitLayoutEditor.layoutIndexForGuiSlot(guiSlot));
        assertEquals(guiSlot, KitLayoutEditor.guiSlotForLayoutIndex(layoutIndex));
    }

    @ParameterizedTest
    @CsvSource({"0", "5", "7", "8"})
    void chromeSlotsAreNotKitSlots(int guiSlot) {
        assertEquals(-1, KitLayoutEditor.layoutIndexForGuiSlot(guiSlot));
    }

    @Test
    void hotbarMapsToRowFour() {
        for (int hot = 0; hot < 9; hot++) {
            assertEquals(hot, KitLayoutEditor.layoutIndexForGuiSlot(36 + hot));
        }
    }
}
