package com.rumilance.practice.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Implemented by GUIs that want to intercept clicks on the player's own inventory
 * (the bottom section of an inventory view), e.g. the OrPlusGUI and the /ekitadmin chest.
 */
public interface BottomInventoryClickHandler {

    void handleBottomClick(Player player, GuiSession session, InventoryClickEvent event);
}
