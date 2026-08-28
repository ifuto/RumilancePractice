package com.rumilance.practice.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/** Optional close hook invoked before the session is cleared from the registry. */
public interface GuiCloseHandler {

    void onGuiClose(Player player, GuiSession session, Inventory top, InventoryCloseEvent.Reason reason);
}
