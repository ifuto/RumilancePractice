package com.rumilance.practice.gui;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central GUI click/drag guard. Identifies inventories via PracticeGuiHolder + session id.
 */
public final class GuiListener implements Listener {

    private final GuiSessionRegistry registry;
    private final PlayerStateManager stateManager;
    private final Map<GuiType, AbstractGui> handlers = new EnumMap<>(GuiType.class);

    public GuiListener(GuiSessionRegistry registry, PlayerStateManager stateManager) {
        this.registry = registry;
        this.stateManager = stateManager;
    }

    public void register(AbstractGui gui) {
        handlers.put(gui.type(), gui);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            player.closeInventory();
            return;
        }

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        if (click == ClickType.NUMBER_KEY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click.isShiftClick()
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        String guiAction = GuiDecorator.actionOf(current);
        if (guiAction == null || "decorate".equals(guiAction)) {
            return;
        }

        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        AbstractGui handler = handlers.get(holder.type());
        if (handler != null) {
            handler.handleClick(player, session, event.getInventory(), event.getSlot(), guiAction);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PracticeGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        registry.get(player.getUniqueId()).ifPresent(session -> {
            if (session.sessionId().equals(holder.sessionId())) {
                registry.close(player.getUniqueId());
            }
        });
        if (holder.type() == GuiType.EDIT_KIT
                && stateManager.getState(player.getUniqueId()) == PlayerState.EDITING_KIT) {
            stateManager.resetToLobby(player.getUniqueId());
        }
    }
}
