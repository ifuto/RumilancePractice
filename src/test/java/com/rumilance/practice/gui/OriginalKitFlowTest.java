package com.rumilance.practice.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the room-edit cheat fix policy: only the original-kit flow screens may abort a stashed
 * edit on close. The regression here was ANY gui close aborting the stash — the slot-menu that
 * launches the room teleport closed mid-flow and dumped the lobby inventory back over the kit
 * while the editor stayed in creative (a free cheat).
 */
class OriginalKitFlowTest {

    @Test
    void flowScreensMayAbortStashedEdit() {
        assertTrue(OriginalKitFlow.isFlowGui(GuiType.ORIGINAL_KIT));
        assertTrue(OriginalKitFlow.isFlowGui(GuiType.ORIGINAL_KIT_SLOT_MENU));
        assertTrue(OriginalKitFlow.isFlowGui(GuiType.ORIGINAL_KIT_SETTINGS));
        assertTrue(OriginalKitFlow.isFlowGui(GuiType.CONFIRM));
    }

    @Test
    void everyOtherScreenMustNeverAbortTheStash() {
        // The room teleport closes the launching slot-menu — but /menu, the kit picker,
        // queue GUIs etc. opened later from the room must be equally inert on close.
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.EDIT_KIT));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.EKIT_SELECT));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.EKIT_EDIT));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.SETTINGS));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.RANKED_QUEUE));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.UNRANKED_QUEUE));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.SPECTATE_LIST));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.ADMIN_MENU));
        assertFalse(OriginalKitFlow.isFlowGui(GuiType.PROFILE));
    }
}
