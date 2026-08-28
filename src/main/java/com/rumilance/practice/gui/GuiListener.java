package com.rumilance.practice.gui;

import com.rumilance.practice.kit.KitLayoutEditor;
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
    /** Opens the Battle Menu for screens marked {@link GuiSession#fromBattleMenu()}. */
    private java.util.function.Consumer<Player> battleMenuReturn;
    /** Re-opens the original-kit editor after a nested confirm/enchant flow. */
    private java.util.function.Consumer<Player> reopenOriginalEditor;

    public GuiListener(GuiSessionRegistry registry, PlayerStateManager stateManager,
                       OriginalKitService originalKitService) {
        this.registry = registry;
        this.stateManager = stateManager;
        this.originalKitService = originalKitService;
    }

    public void register(AbstractGui gui) {
        handlers.put(gui.type(), gui);
        gui.setStateManager(stateManager);
    }

    public void setMenuReturn(java.util.function.Consumer<Player> menuReturn) {
        this.menuReturn = menuReturn;
    }

    public void setBattleMenuReturn(java.util.function.Consumer<Player> battleMenuReturn) {
        this.battleMenuReturn = battleMenuReturn;
    }

    public void setReopenOriginalEditor(java.util.function.Consumer<Player> reopenOriginalEditor) {
        this.reopenOriginalEditor = reopenOriginalEditor;
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

    private void openBattleMenuLater(Player player) {
        org.bukkit.plugin.Plugin plugin =
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(GuiListener.class);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && battleMenuReturn != null && canReturnToMenu(player)) {
                battleMenuReturn.accept(player);
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
            // Central "Close" interception: battle-menu children return to Battle Menu;
            // game-menu children return to Game Menu. Other flows just close.
            if ("close".equals(guiAction) && canReturnToMenu(player)) {
                if (session.fromBattleMenu() && battleMenuReturn != null) {
                    openBattleMenuLater(player);
                    return;
                }
                if (session.fromGameMenu() && menuReturn != null) {
                    openMenuLater(player);
                    return;
                }
            }
            handler.handleClick(player, session, top, event.getSlot(), guiAction, event.getClick());
        } else if (handler instanceof BottomInventoryClickHandler bottom) {
            bottom.handleBottomClick(player, session, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        if (holder.type() != GuiType.EDIT_KIT) {
            event.setCancelled(true);
            return;
        }
        if (!registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                if (!KitLayoutEditor.isKitSlot(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            } else {
                // Rearrange-only: no pulling items from the player's survival inventory.
                event.setCancelled(true);
                return;
            }
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            event.setCancelled(true);
            return;
        }
        org.bukkit.plugin.Plugin plugin =
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(GuiListener.class);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            GuiSession live = registry.get(player.getUniqueId()).orElse(null);
            if (live == null) {
                return;
            }
            ItemStack[] layout = live.get("layout", ItemStack[].class);
            if (layout == null) {
                layout = new ItemStack[41];
            }
            KitLayoutEditor.syncLayoutFromTopInventory(player.getOpenInventory().getTopInventory(), layout);
            live.put("layout", layout);
            AbstractGui handler = handlers.get(GuiType.EDIT_KIT);
            if (handler != null) {
                handler.renderPublic(player, live, player.getOpenInventory().getTopInventory());
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        boolean openedFromBattleMenu = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::fromBattleMenu)
                .orElse(false);
        boolean openedFromGameMenu = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::fromGameMenu)
                .orElse(false);
        registry.get(player.getUniqueId()).ifPresent(session -> {
            if (session.sessionId().equals(holder.sessionId())) {
                registry.close(player.getUniqueId());
            }
        });
        // Esc: battle children → Battle Menu; game-menu children → Game Menu.
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER && canReturnToMenu(player)) {
            if (openedFromBattleMenu && battleMenuReturn != null) {
                openBattleMenuLater(player);
            } else if (openedFromGameMenu && menuReturn != null) {
                openMenuLater(player);
            }
        }
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.OPENING_GUI) {
            stateManager.resetToLobby(player.getUniqueId());
        } else if ((holder.type() == GuiType.EDIT_KIT || holder.type() == GuiType.EKIT_EDIT)
                && state == PlayerState.EDITING_KIT) {
            stateManager.resetToLobby(player.getUniqueId());
        }
        if (originalKitService != null) {
            if (holder.type() == GuiType.EKIT_EDIT) {
                originalKitService.onEditGuiClosed(player.getUniqueId());
            } else if (holder.type() == GuiType.CONFIRM) {
                boolean navigating = originalKitService.consumeNavigating(player.getUniqueId());
                if (!navigating
                        && event.getReason() == InventoryCloseEvent.Reason.PLAYER
                        && reopenOriginalEditor != null
                        && originalKitService.context(player.getUniqueId()) != null) {
                    org.bukkit.plugin.Plugin plugin =
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(GuiListener.class);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && originalKitService.context(player.getUniqueId()) != null) {
                            reopenOriginalEditor.accept(player);
                        }
                    });
                } else if (!navigating && originalKitService.isStashed(player.getUniqueId())) {
                    originalKitService.abortFlow(player.getUniqueId());
                }
            } else if (originalKitService.isStashed(player.getUniqueId())
                    && !originalKitService.consumeNavigating(player.getUniqueId())) {
                originalKitService.abortFlow(player.getUniqueId());
            }
        }
    }
}
