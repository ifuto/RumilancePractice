package com.rumilance.practice.gui;

import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VIP+ anvil rename for tools inside {@link EditKitGui}. Persists the renamed item back into the
 * edit-kit layout and reopens the editor.
 */
public final class KitAnvilRenameService implements Listener {

    public record PendingRename(
            String kitId,
            String preset,
            int layoutSlot,
            ItemStack original,
            ItemStack[] layoutSnapshot
    ) {
    }

    private final Plugin plugin;
    private final RankService rankService;
    private final MessageService messages;
    private final Map<UUID, PendingRename> pending = new ConcurrentHashMap<>();
    private volatile EditKitGui editKitGui;

    public KitAnvilRenameService(Plugin plugin, RankService rankService) {
        this(plugin, rankService, null);
    }

    public KitAnvilRenameService(Plugin plugin, RankService rankService, MessageService messages) {
        this.plugin = plugin;
        this.rankService = rankService;
        this.messages = messages;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public boolean isRenaming(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public boolean tryOpenRename(Player player, ItemStack item, int layoutSlot,
                                 String kitId, String preset, ItemStack[] layoutSnapshot) {
        if (!rankService.isVipPlusOrAbove(player)) {
            return false;
        }
        if (item == null || item.getType().isAir() || !isRenameableTool(item.getType())) {
            return false;
        }
        ItemStack[] snapshot = layoutSnapshot == null ? null : layoutSnapshot.clone();
        pending.put(player.getUniqueId(),
                new PendingRename(kitId, preset == null ? "" : preset, layoutSlot, item.clone(), snapshot));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                pending.remove(player.getUniqueId());
                return;
            }
            InventoryView view = player.openAnvil(player.getLocation(), true);
            if (view == null) {
                pending.remove(player.getUniqueId());
                if (messages != null) {
                    player.sendMessage(messages.render(player, "gui.anvil-fail"));
                } else {
                    player.sendMessage(Component.text("Could not open anvil.", NamedTextColor.RED));
                }
                reopenEditor(player, kitId, preset, snapshot);
                return;
            }
            AnvilInventory anvil = (AnvilInventory) view.getTopInventory();
            anvil.setItem(0, item.clone());
            anvil.setRepairCost(0);
            if (messages != null) {
                player.sendMessage(messages.render(player, "gui.anvil-hint"));
            } else {
                player.sendMessage(Component.text(
                        "Rename the item, then take the result from the right slot.", NamedTextColor.YELLOW));
            }
        });
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PendingRename rename = pending.get(player.getUniqueId());
        if (rename == null || !(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        if (event.getRawSlot() != 2 || event.getClickedInventory() != anvil) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        event.setCurrentItem(null);
        player.setItemOnCursor(null);
        pending.remove(player.getUniqueId());
        anvil.clear();
        ItemStack renamed = KitLayoutEditor.stripEditorTags(result.clone());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.closeInventory();
            if (editKitGui != null) {
                editKitGui.applyRenamedItem(player, rename.kitId(), rename.preset(),
                        rename.layoutSlot(), renamed, rename.layoutSnapshot());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        if (!pending.containsKey(player.getUniqueId())) {
            return;
        }
        event.getInventory().setRepairCost(0);
        ItemStack result = event.getResult();
        if (result != null && !result.getType().isAir()) {
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            return;
        }
        PendingRename rename = pending.remove(player.getUniqueId());
        if (rename == null) {
            return;
        }
        anvil.clear();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                reopenEditor(player, rename.kitId(), rename.preset(), rename.layoutSnapshot());
            }
        });
    }

    private void reopenEditor(Player player, String kitId, String preset, ItemStack[] layout) {
        if (editKitGui == null || !player.isOnline()) {
            return;
        }
        if (layout != null) {
            editKitGui.reopenWithLayout(player, kitId, layout);
        } else {
            editKitGui.openKitEditor(player, kitId, preset);
        }
    }

    public static boolean isRenameableTool(Material type) {
        if (type == null || type.isAir()) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || type == Material.MACE
                || type == Material.SHIELD
                || type == Material.TRIDENT;
    }
}
