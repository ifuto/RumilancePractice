package com.rumilance.practice.guard;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;

/**
 * Closes the item-flow loopholes the per-area listeners do not cover:
 *
 * <ul>
 *   <li><b>Vanilla containers in lobby / countdown / spectating</b> — without this a
 *       player could open a chest and move kit/functional items out of their inventory
 *       (or pull stored items in), smuggling items between a match and the hub.</li>
 *   <li><b>Loose-item drop/pickup outside combat</b> — belt-and-suspenders over
 *       {@code LobbyListener} (which is skipped while a GUI session is not registered)
 *       and over FFA windows where the player state has already flipped to lobby.</li>
 * </ul>
 *
 * <p>The branching matrix lives in {@link PracticeGuards} so JUnit can exercise it
 * without a running server.</p>
 */
public final class ItemFlowGuardListener implements Listener {

    private final PlayerStateManager stateManager;
    private final FfaService ffaService;

    public ItemFlowGuardListener(PlayerStateManager stateManager, FfaService ffaService) {
        this.stateManager = stateManager;
        this.ffaService = ffaService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!PracticeGuards.vanillaContainerItemMoveBlocked(state(player))) {
            return;
        }
        // Plugin GUIs (queue select, kit editor, ...) are guarded by GuiListener and use
        // their own holders — only police plain vanilla container views here.
        if (isPluginGui(event.getView().getTopInventory())) {
            return;
        }
        InventoryType type = event.getView().getTopInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            event.setCancelled(true);
            return;
        }
        // Any click that crosses the container/player boundary (shift, number-key swap,
        // hotbar swap, collect-to-cursor...) or happens inside the container is blocked.
        Inventory top = event.getView().getTopInventory();
        InventoryAction action = event.getAction();
        boolean interactsWithContainer = clicked.equals(top)
                || event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR;
        if (interactsWithContainer) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!PracticeGuards.vanillaContainerItemMoveBlocked(state(player))) {
            return;
        }
        if (isPluginGui(event.getView().getTopInventory())) {
            return;
        }
        InventoryType type = event.getView().getTopInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.PLAYER) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMove(InventoryMoveItemEvent event) {
        // Hopper/dropper style transport can never touch a practice-managed inventory:
        // the destination/source holders are either plugin GUIs or player inventories
        // that must stay exactly as kit/lobby apply left them.
        if (isPluginGui(event.getDestination()) || isPluginGui(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isPluginGui(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (ffaService != null && ffaService.isInFfa(player.getUniqueId())) {
            return;
        }
        if (PracticeGuards.looseItemMoveBlocked(state(player))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (ffaService != null && ffaService.isInFfa(player.getUniqueId())) {
            // FFA listeners own pickup policy (kit drops must be collectable).
            return;
        }
        if (PracticeGuards.looseItemMoveBlocked(state(player))) {
            event.setCancelled(true);
        }
    }

    private PlayerState state(Player player) {
        return stateManager.getState(player.getUniqueId());
    }

    private static boolean isPluginGui(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof com.rumilance.practice.gui.PracticeGuiHolder;
    }
}
