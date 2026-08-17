package com.rumilance.practice.gui;

import com.rumilance.practice.originalkit.OriginalKitService;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central GUI click/drag guard. Identifies inventories via PracticeGuiHolder + session id.
 * GUIs implementing {@link BottomInventoryClickHandler} also receive clicks on the player's
 * own inventory (bottom section).
 */
public final class GuiListener implements Listener {

    private final GuiSessionRegistry registry;
    private final PlayerStateManager stateManager;
    private final OriginalKitService originalKitService;
    private final Map<GuiType, AbstractGui> handlers = new EnumMap<>(GuiType.class);
    /** Opens the Game Menu; wired from bootstrap (null = feature disabled). */
    private java.util.function.Consumer<Player> menuReturn;

    public GuiListener(GuiSessionRegistry registry, PlayerStateManager stateManager,
                       OriginalKitService originalKitService) {
        this.registry = registry;
        this.stateManager = stateManager;
        this.originalKitService = originalKitService;
    }

    public void register(AbstractGui gui) {
        handlers.put(gui.type(), gui);
    }

    public void setMenuReturn(java.util.function.Consumer<Player> menuReturn) {
        this.menuReturn = menuReturn;
    }

    /** True when the player may be bounced back to the Game Menu (lobby-ish states only). */
    private boolean canReturnToMenu(Player player) {
        PlayerState state = stateManager.getState(player.getUniqueId());
        return state == PlayerState.LOBBY || state == PlayerState.OPENING_GUI;
    }

    private void openMenuLater(Player player) {
        org.bukkit.plugin.Plugin plugin =
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(GuiListener.class);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && menuReturn != null && canReturnToMenu(player)) {
                menuReturn.accept(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            player.closeInventory();
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        // The event is already cancelled above, so no item can actually move. Shift-clicks on
        // the TOP (GUI) inventory are legitimate button gestures (e.g. team disband confirm,
        // shift-click kick) and must reach the handler. Everything else that smells like an
        // item-management gesture is ignored.
        boolean topShiftClick = clicked == top && click.isShiftClick();
        if (!topShiftClick
                && (click == ClickType.NUMBER_KEY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click.isShiftClick()
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR)) {
            return;
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        AbstractGui handler = handlers.get(holder.type());
        if (handler == null) {
            return;
        }
        if (clicked == top) {
            ItemStack current = event.getCurrentItem();
            String guiAction = GuiDecorator.actionOf(current);
            if (guiAction == null || "decorate".equals(guiAction)) {
                return;
            }
            // Central "Close" interception: ONLY screens opened from the Game Menu navigate
            // back to it (session.fromGameMenu). Screens opened via /setfunc items, commands
            // or other flows simply close (Esc behaves the same below).
            if ("close".equals(guiAction) && menuReturn != null
                    && session.fromGameMenu() && canReturnToMenu(player)) {
                openMenuLater(player);
                return;
            }
            handler.handleClick(player, session, top, event.getSlot(), guiAction, event.getClick());
        } else if (handler instanceof BottomInventoryClickHandler bottom) {
            bottom.handleBottomClick(player, session, event);
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
        boolean openedFromGameMenu = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::fromGameMenu)
                .orElse(false);
        registry.get(player.getUniqueId()).ifPresent(session -> {
            if (session.sessionId().equals(holder.sessionId())) {
                registry.close(player.getUniqueId());
            }
        });
        // Esc returns to the Game Menu ONLY for screens opened from it (reason PLAYER only,
        // so programmatic OPEN_NEW/PLUGIN closes never loop). /setfunc-opened screens just close.
        if (openedFromGameMenu
                && event.getReason() == InventoryCloseEvent.Reason.PLAYER
                && menuReturn != null
                && canReturnToMenu(player)) {
            openMenuLater(player);
        }
        if ((holder.type() == GuiType.EDIT_KIT || holder.type() == GuiType.EKIT_EDIT)
                && stateManager.getState(player.getUniqueId()) == PlayerState.EDITING_KIT) {
            stateManager.resetToLobby(player.getUniqueId());
        }
        if (originalKitService != null) {
            if (holder.type() == GuiType.EKIT_EDIT) {
                originalKitService.onEditGuiClosed(player.getUniqueId());
            } else if (originalKitService.isStashed(player.getUniqueId())
                    && !originalKitService.consumeNavigating(player.getUniqueId())) {
                originalKitService.abortFlow(player.getUniqueId());
            }
        }
    }
}
