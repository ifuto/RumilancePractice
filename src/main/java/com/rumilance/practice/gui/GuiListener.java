package com.rumilance.practice.gui;

import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.locale.MessageService;
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
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central GUI click/drag guard. Identifies inventories via PracticeGuiHolder + session id.
 * GUIs implementing {@link BottomInventoryClickHandler} also receive clicks on the player's
 * own inventory (bottom section). {@link FreeInventoryEdit} menus allow vanilla pick/place
 * outside control slots.
 */
public final class GuiListener implements Listener {

    private final GuiSessionRegistry registry;
    private final PlayerStateManager stateManager;
    private final OriginalKitService originalKitService;
    private final MessageService messages;
    private final Map<GuiType, AbstractGui> handlers = new EnumMap<>(GuiType.class);
    /** Opens the Game Menu; wired from bootstrap (null = feature disabled). */
    private java.util.function.Consumer<Player> menuReturn;
    /** Opens the Battle Menu for screens marked {@link GuiSession#fromBattleMenu()}. */
    private java.util.function.Consumer<Player> battleMenuReturn;
    /** Re-opens the original-kit editor after a nested confirm/enchant flow. */
    private java.util.function.Consumer<Player> reopenOriginalEditor;

    private static final long CLICK_DEBOUNCE_MS = 350L;
    private final ConcurrentHashMap<UUID, Long> lastClickAt = new ConcurrentHashMap<>();

    public GuiListener(GuiSessionRegistry registry, PlayerStateManager stateManager,
                       OriginalKitService originalKitService) {
        this(registry, stateManager, originalKitService, null);
    }

    public GuiListener(GuiSessionRegistry registry, PlayerStateManager stateManager,
                       OriginalKitService originalKitService, MessageService messages) {
        this.registry = registry;
        this.stateManager = stateManager;
        this.originalKitService = originalKitService;
        this.messages = messages;
    }

    public void register(AbstractGui gui) {
        handlers.put(gui.type(), gui);
        gui.setStateManager(stateManager);
        if (messages != null) {
            gui.setMessages(messages);
        }
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

    private static boolean isOverlay(GuiType type) {
        return type == GuiType.SMITHING_TRIM
                || type == GuiType.ENCHANT
                || type == GuiType.POTION
                || type == GuiType.CONFIRM;
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

        if (!registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            event.setCancelled(true);
            return;
        }
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        AbstractGui handler = handlers.get(holder.type());

        boolean freeEdit = handler instanceof FreeInventoryEdit free
                && free.isFreeEditActive(session);

        if ((click == ClickType.DROP || click == ClickType.CONTROL_DROP) && clicked == top
                && holder.type() == GuiType.EDIT_KIT) {
            event.setCancelled(true);
            AbstractGui editHandler = handlers.get(GuiType.EDIT_KIT);
            if (editHandler instanceof EditKitGui editKit && editKit.isPresetEdit(session)) {
                if (KitLayoutEditor.isKitSlot(event.getSlot())) {
                    editKit.handleKitSlotDrop(player, session, top, event.getSlot());
                }
            }
            return;
        }

        if (freeEdit && handler instanceof FreeInventoryEdit free) {
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
                if (clicked == top && !free.isControlSlot(session, event.getSlot())) {
                    clicked.setItem(event.getSlot(), null);
                } else if (clicked != top) {
                    clicked.setItem(event.getSlot(), null);
                }
                player.setItemOnCursor(null);
                return;
            }
            if (clicked == top && free.isControlSlot(session, event.getSlot())) {
                event.setCancelled(true);
                dispatchTopClick(player, session, top, handler, event);
                return;
            }
            return;
        }

        event.setCancelled(true);

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
        if (handler == null) {
            return;
        }
        if (clicked == top) {
            dispatchTopClick(player, session, top, handler, event);
        } else if (handler instanceof BottomInventoryClickHandler bottom) {
            bottom.handleBottomClick(player, session, event);
        }
    }

    private void dispatchTopClick(Player player, GuiSession session, Inventory top,
                                  AbstractGui handler, InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        String guiAction = GuiDecorator.actionOf(current);
        if (guiAction == null || "decorate".equals(guiAction)) {
            if (handler instanceof EditKitGui editKit && editKit.isEditorMode(session)) {
                int layoutIndex = KitLayoutEditor.layoutIndexForGuiSlot(event.getSlot());
                if (layoutIndex >= 0) {
                    handler.handleClick(player, session, top, event.getSlot(),
                            "slot:" + layoutIndex, event.getClick());
                }
            } else if (handler instanceof com.rumilance.practice.gui.menus.OriginalKitEditGui) {
                int layoutIndex = com.rumilance.practice.gui.menus.OriginalKitEditGui.layoutIndexForGuiSlot(event.getSlot());
                if (layoutIndex >= 0) {
                    handler.handleClick(player, session, top, event.getSlot(),
                            "slot:" + layoutIndex, event.getClick());
                }
            }
            return;
        }
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
        long now = System.currentTimeMillis();
        Long last = lastClickAt.get(player.getUniqueId());
        if (last != null && now - last < CLICK_DEBOUNCE_MS) {
            return;
        }
        lastClickAt.put(player.getUniqueId(), now);
        handler.handleClick(player, session, top, event.getSlot(), guiAction, event.getClick());
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
        if (!registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            event.setCancelled(true);
            return;
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        AbstractGui handler = handlers.get(holder.type());
        if (handler instanceof FreeInventoryEdit free
                && session != null
                && free.isFreeEditActive(session)) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize() && free.isControlSlot(session, rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        if (holder.type() != GuiType.EDIT_KIT) {
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
                GuiSession dragSession = registry.get(player.getUniqueId()).orElse(null);
                AbstractGui editHandler = handlers.get(GuiType.EDIT_KIT);
                boolean presetEdit = dragSession != null && editHandler instanceof EditKitGui editKit
                        && editKit.isPresetEdit(dragSession);
                if (!presetEdit) {
                    event.setCancelled(true);
                    return;
                }
                int bottomSlot = rawSlot - top.getSize();
                if (bottomSlot != 5 && bottomSlot != 6) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        AbstractGui editHandler = handlers.get(GuiType.EDIT_KIT);
        if (session != null && editHandler instanceof EditKitGui editKit && editKit.isPresetEdit(session)) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= top.getSize()) {
                    event.setCancelled(true);
                    editKit.deleteCursorItem(player);
                    return;
                }
            }
        }
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
            AbstractGui liveHandler = handlers.get(GuiType.EDIT_KIT);
            if (liveHandler instanceof EditKitGui editKit) {
                editKit.stashCurrentLayout(player, live);
            }
            if (liveHandler != null) {
                liveHandler.renderPublic(player, live, player.getOpenInventory().getTopInventory());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof PracticeGuiHolder holder)) {
            return;
        }
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session == null || !registry.isCurrent(player.getUniqueId(), holder.sessionId())) {
            return;
        }
        AbstractGui handler = handlers.get(holder.type());
        if (handler instanceof FreeInventoryEdit free && free.isFreeEditActive(session)) {
            event.setCancelled(true);
            return;
        }
        if (holder.type() == GuiType.EDIT_KIT
                && handler instanceof EditKitGui editKit
                && editKit.isPresetEdit(session)) {
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
        boolean openedFromBattleMenu = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::fromBattleMenu)
                .orElse(false);
        boolean openedFromGameMenu = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::fromGameMenu)
                .orElse(false);
        boolean navigatingAway = registry.get(player.getUniqueId())
                .filter(session -> session.sessionId().equals(holder.sessionId()))
                .map(GuiSession::consumeNavigatingAway)
                .orElse(false);
        registry.get(player.getUniqueId()).ifPresent(session -> {
            if (session.sessionId().equals(holder.sessionId())) {
                AbstractGui handler = handlers.get(holder.type());
                if (holder.type() == GuiType.EDIT_KIT && handler instanceof EditKitGui editKit) {
                    editKit.onEditorClosed(player, session);
                }
                if (handler instanceof GuiCloseHandler closeHandler) {
                    closeHandler.onGuiClose(player, session, event.getInventory(), event.getReason());
                }
                registry.close(player.getUniqueId());
            }
        });
        if (!navigatingAway
                && !isOverlay(holder.type())
                && event.getReason() == InventoryCloseEvent.Reason.PLAYER
                && canReturnToMenu(player)) {
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
