package com.rumilance.practice.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Keeps totem-of-undying swaps reliable regardless of latency (MC-257634). On a high-ping link
 * the "switch to totem" packet can arrive at the server in the same tick the lethal damage is
 * resolved, so the totem is not yet seen in-hand and the player dies even though they swapped
 * in time. Vanilla applies a small switch window anyway (which is what makes the totem a
 * reaction-save rather than a free invincibility), so we mirror that window server-side:
 *
 * <ul>
 *   <li>a same-tick totem pickup into the selected/offhand slot, or</li>
 *   <li>scrolling the hotbar onto a totem, or pressing F to move a totem into the offhand/main</li>
 * </ul>
 *
 * marks a short pending-hold that {@link PracticeDeath#isHoldingTotem} honours. This is only a
 * latency-fairness window — it is short and only ever grants a totem the player genuinely
 * swapped to, never extra invincibility. All handlers only act on active combatants.
 */
public final class TotemPickupListener implements Listener {

    private final Predicate<UUID> combatantTest;

    public TotemPickupListener(Predicate<UUID> combatantTest) {
        this.combatantTest = combatantTest;
    }

    private boolean combatant(Player player) {
        return player != null && combatantTest.test(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !combatant(player)) {
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

    /** Hotbar scroll onto a totem: the destination slot holds (or is about to hold) a totem. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!combatant(player)) {
            return;
        }
        ItemStack destination = player.getInventory().getItem(event.getNewSlot());
        if (PracticeDeath.isTotem(destination)) {
            PracticeDeath.markPendingHandTotem(player);
        }
    }

    /** F-key swap: if either resulting hand will contain a totem, treat it as held. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!combatant(player)) {
            return;
        }
        if (PracticeDeath.isTotem(event.getMainHandItem())
                || PracticeDeath.isTotem(event.getOffHandItem())) {
            PracticeDeath.markPendingHandTotem(player);
        }
    }
}
