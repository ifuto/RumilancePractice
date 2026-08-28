package com.rumilance.practice.gui;

import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

    public record PendingRename(String kitId, String preset, int layoutSlot, ItemStack original) {
    }

    private final Plugin plugin;
    private final RankService rankService;
    private final Map<UUID, PendingRename> pending = new ConcurrentHashMap<>();
    private volatile EditKitGui editKitGui;

    public KitAnvilRenameService(Plugin plugin, RankService rankService) {
        this.plugin = plugin;
        this.rankService = rankService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public boolean isRenaming(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public boolean tryOpenRename(Player player, ItemStack item, int layoutSlot,
                                 String kitId, String preset) {
        if (!rankService.isVipPlusOrAbove(player)) {
            return false;
        }
        if (item == null || item.getType().isAir() || !isRenameableTool(item.getType())) {
            return false;
        }
        pending.put(player.getUniqueId(),
                new PendingRename(kitId, preset == null ? "" : preset, layoutSlot, item.clone()));
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                pending.remove(player.getUniqueId());
                return;
            }
            InventoryView view = player.openAnvil(player.getLocation(), true);
            if (view == null) {
                pending.remove(player.getUniqueId());
                player.sendMessage(Component.text("Could not open anvil.", NamedTextColor.RED));
                reopenEditor(player, kitId, preset);
                return;
            }
            AnvilInventory anvil = (AnvilInventory) view.getTopInventory();
            anvil.setItem(0, item.clone());
            anvil.setRepairCost(0);
            player.sendMessage(Component.text(
                    "Rename the item, then take the result (or close to cancel).", NamedTextColor.YELLOW));
        });
        return true;
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
        PendingRename rename = pending.remove(player.getUniqueId());
        if (rename == null) {
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory anvil)) {
            reopenEditor(player, rename.kitId(), rename.preset());
            return;
        }
        ItemStack result = anvil.getResult();
        ItemStack first = anvil.getItem(0);
        ItemStack renamed = null;
        if (result != null && !result.getType().isAir()) {
            renamed = result.clone();
        } else if (first != null && !first.getType().isAir()) {
            // Keep custom name typed into the anvil even if result wasn't taken.
            ItemMeta meta = first.getItemMeta();
            String renameText = anvil.getRenameText();
            if (meta != null && renameText != null && !renameText.isBlank()) {
                meta.displayName(Component.text(renameText)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                first.setItemMeta(meta);
            }
            renamed = first.clone();
        }
        anvil.clear();
        final ItemStack toApply = renamed;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (toApply != null && editKitGui != null) {
                editKitGui.applyRenamedItem(player, rename.kitId(), rename.preset(),
                        rename.layoutSlot(), toApply);
            } else {
                reopenEditor(player, rename.kitId(), rename.preset());
            }
        });
    }

    private void reopenEditor(Player player, String kitId, String preset) {
        if (editKitGui != null && player.isOnline()) {
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
