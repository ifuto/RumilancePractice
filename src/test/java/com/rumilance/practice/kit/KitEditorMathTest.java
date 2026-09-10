package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slot picking for cursor-borne items absorbed into a kit layout (the "saving with an item
 * on the cursor must not delete it" rule): armor lands in its dedicated slot regardless of
 * occupancy, everything else fills storage (9-35) then hotbar (0-8), or fails when full.
 */
class KitEditorMathTest {

    private static String[] emptyLayout() {
        return new String[41];
    }

    @Test
    void armorAlwaysGoesToItsDedicatedSlot() {
        assertEquals(36, KitEditorMath.armorSlot("NETHERITE_HELMET"));
        assertEquals(36, KitEditorMath.armorSlot("PLAYER_HEAD"));
        assertEquals(37, KitEditorMath.armorSlot("ELYTRA"));
        assertEquals(37, KitEditorMath.armorSlot("NETHERITE_CHESTPLATE"));
        assertEquals(38, KitEditorMath.armorSlot("NETHERITE_LEGGINGS"));
        assertEquals(39, KitEditorMath.armorSlot("NETHERITE_BOOTS"));
        assertEquals(40, KitEditorMath.armorSlot("SHIELD"));
        assertEquals(-1, KitEditorMath.armorSlot("NETHERITE_SWORD"));
    }

    @Test
    void armorSlotWinsEvenWhenSomethingOccupiesIt() {
        String[] layout = emptyLayout();
        layout[36] = "CHAINMAIL_HELMET"; // occupied: cursor armor replaces it on absorb
        assertEquals(36, KitEditorMath.cursorTarget("NETHERITE_HELMET", layout));
    }

    @Test
    void generalItemsFillStorageThenHotbar() {
        String[] layout = emptyLayout();
        assertEquals(9, KitEditorMath.cursorTarget("NETHERITE_SWORD", layout));
        for (int i = 9; i <= 35; i++) {
            layout[i] = "STONE";
        }
        assertEquals(0, KitEditorMath.cursorTarget("NETHERITE_SWORD", layout));
        for (int i = 0; i <= 8; i++) {
            layout[i] = "STONE";
        }
        assertEquals(-1, KitEditorMath.cursorTarget("NETHERITE_SWORD", layout),
                "a full layout reports no home so the caller can decline the absorb");
    }

    @Test
    void blankCursorIsHarmless() {
        assertEquals(-1, KitEditorMath.cursorTarget("AIR", emptyLayout()));
        assertEquals(-1, KitEditorMath.cursorTarget(null, emptyLayout()));
    }
}
