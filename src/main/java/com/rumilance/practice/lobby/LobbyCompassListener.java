package com.rumilance.practice.lobby;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Consumer;

/**
 * Gives every lobby player a "Game Menu" compass in their hotbar that opens a fast-jump menu
 * (ranked / unranked / FFA / kit editor / spectate). Right-clicking the compass (in either
 * hand) opens the supplied menu opener; if a lobby inventory has been saved with
 * {@code /setlobbyitem}, the compass is only added when it is not already present so admin-set
 * inventories are never overwritten.
 */
public final class LobbyCompassListener implements Listener {

    /** Hotbar slot the compass occupies when auto-given (centre of the hotbar). */
    public static final int COMPASS_SLOT = 4;

    private final PlayerStateManager stateManager;
    private final SoundService soundService;
    private final Consumer<Player> openMenu;

    public LobbyCompassListener(PlayerStateManager stateManager, SoundService soundService, Consumer<Player> openMenu) {
        this.stateManager = stateManager;
        this.soundService = soundService;
        this.openMenu = openMenu;
    }

    /** Builds the compass item. Exposed so other code (admin tools, /setfunc) can reuse the same icon. */
    public static ItemStack compassItem() {
        ItemStack stack = new ItemStack(Material.COMPASS);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Game Menu", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Right-click to open the practice menu.",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        meta.getPersistentDataContainer().set(ItemKeys.functionType(), PersistentDataType.STRING, "menu");
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Defer one tick so /setlobbyitem inventories and other join handlers finish first.
        org.bukkit.plugin.Plugin plugin = org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(LobbyCompassListener.class);
        if (plugin != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> giveIfMissing(player));
        }
    }

    /**
     * Adds the compass to the player's hotbar if they are in the lobby state and do not already
     * have one. Safe to call repeatedly (e.g. on lobby return).
     */
    public void giveIfMissing(Player player) {
        if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
            return;
        }
        if (hasCompass(player)) {
            return;
        }
        // Avoid overwriting a meaningful item: prefer an empty slot, fall back to the centre slot.
        int target = player.getInventory().firstEmpty();
        if (target < 0 || target > 8) {
            target = COMPASS_SLOT;
        }
        player.getInventory().setItem(target, compassItem());
    }

    private boolean hasCompass(Player player) {
        NamespacedKey key = ItemKeys.functionType();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if ("menu".equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * LOWEST priority + DENY results so WorldEdit/FAWE never treats the menu compass as its
     * navigation wand (which caused "No free spot ahead of you found" spam on every click).
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String function = item.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.functionType(), PersistentDataType.STRING);
        if (!"menu".equals(function)) {
            return;
        }
        event.setCancelled(true);
        // DENY both results: WorldEdit's interact listener checks these and skips its
        // navigation-wand (/thru, /jumpto) handling entirely.
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Player player = event.getPlayer();
        if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
            return;
        }
        soundService.play(player, "gui-open");
        openMenu.accept(player);
    }
}
