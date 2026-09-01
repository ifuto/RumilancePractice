package com.rumilance.practice.originalkit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

/**
 * Holder for the virtual shulker box opened while editing an original kit. It remembers which
 * source shulker item (in the player's live kit inventory) it backs, so when the player closes
 * the box the edited contents are written back into that item's BlockStateMeta.
 *
 * <p>We never place a real shulker block (the room forbids placing blocks and virtual block
 * desync is bug-prone). Instead a normal 27-slot inventory backed by the item's own stored
 * contents is opened — functionally identical for filling a shulker, with no fake packets.</p>
 */
public final class RoomShulkerHolder implements InventoryHolder {

    private final int sourceSlot;
    private final ItemStack sourceItem;
    private Inventory inventory;

    public RoomShulkerHolder(int sourceSlot, ItemStack sourceItem) {
        this.sourceSlot = sourceSlot;
        this.sourceItem = sourceItem;
    }

    public int sourceSlot() {
        return sourceSlot;
    }

    public ItemStack sourceItem() {
        return sourceItem;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
