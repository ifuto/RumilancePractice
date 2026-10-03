package com.rumilance.practice.gui;

/**
 * Which GUI screens belong to the original-kit flow and may therefore abort a stashed edit
 * session when closed. Kept pure (no Bukkit types) so the policy is unit-testable.
 *
 * <p>Policy (the room-edit cheat fix): ONLY these screens may trigger
 * {@code OriginalKitService#abortFlow} on close. Any other GUI the player opens meanwhile —
 * including the slot-menu's room teleport closing it mid-flow, or /menu opened from inside
 * the kit room — must never restore the stashed lobby inventory over the kit being edited.</p>
 */
public final class OriginalKitFlow {

    private OriginalKitFlow() {
    }

    /** True when closing this screen may abort a stashed original-kit edit. */
    public static boolean isFlowGui(GuiType type) {
        return type == GuiType.ORIGINAL_KIT
                || type == GuiType.ORIGINAL_KIT_SLOT_MENU
                || type == GuiType.ORIGINAL_KIT_SETTINGS
                || type == GuiType.CONFIRM;
    }
}
