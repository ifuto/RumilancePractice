package com.rumilance.practice.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * GUIs that allow vanilla pick/place in the top content area (and the player's inventory),
 * while keeping designated control slots as cancelled buttons.
 */
public interface FreeInventoryEdit {

    boolean isFreeEditActive(GuiSession session);

    /** Top-inventory slots that stay as cancelled GUI buttons (e.g. back / save / close). */
    boolean isControlSlot(GuiSession session, int topSlot);

    /** Persist free-edit contents (typically on close or save). */
    void persistFreeEdit(Player player, GuiSession session, Inventory top);
}
