package com.rumilance.practice.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Same-tick totem pickups land in the inventory after damage is processed. If the totem
 * would occupy the selected hotbar slot or empty offhand, mark a 1-tick pending hold so
 * {@link PracticeDeath#isHoldingTotem} still sees it.
 */
public final class TotemPickupListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!PracticeDeath.isTotem(stack)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int empty = inventory.firstEmpty();
        int held = inventory.getHeldItemSlot();
        ItemStack off = inventory.getItemInOffHand();
        boolean toSelected = empty == held;
        boolean toOffhand = (off == null || off.getType().isAir()) && empty < 0;
        if (toSelected || toOffhand) {
            PracticeDeath.markPendingHandTotem(player);
        }
    }
}
