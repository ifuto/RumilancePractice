package com.rumilance.practice.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * GUIs with dedicated drop zones: players drag-and-drop items from their inventory onto a
 * top slot and the GUI consumes the DROP (e.g. registers the block type for a kit's
 * canbreak list). The dragged item itself stays with the player — the drop is a signal,
 * not a transfer. Used together with {@link BottomInventoryClickHandler} so a block can
 * also be registered with a plain click inside the player's own inventory.
 */
public interface InventoryDropHandler {

    /** Whether the top raw slot accepts dropped items. */
    boolean isDropSlot(GuiSession session, int rawSlot);

    /**
     * Called with the dragged item after a valid drop onto {@code rawSlot}. The item is
     * NOT removed from the player — implementations only inspect it.
     */
    void handleDrop(Player player, GuiSession session, Inventory top, int rawSlot, ItemStack dropped);
}
