package com.rumilance.practice.lobby;

import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Consumer;

/**
 * Handles interaction with the "Game Menu" compass. Right-clicking the compass (in either hand)
 * while in the lobby opens the fast-jump menu (ranked / unranked / FFA / kit editor / spectate).
 *
 * <p>This listener <strong>never hands out the compass</strong>. It is no longer given on join
 * nor restored on lobby return. It only responds to a compass a player already holds — e.g. one
 * an admin placed in the lobby via {@code /setfunc menu} / {@code /setlobbyitem}.</p>
 */
public final class LobbyCompassListener implements Listener {

    private final PlayerStateManager stateManager;
    private final SoundService soundService;
    private final Consumer<org.bukkit.entity.Player> openMenu;

    public LobbyCompassListener(PlayerStateManager stateManager, SoundService soundService,
                                Consumer<org.bukkit.entity.Player> openMenu) {
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
        org.bukkit.entity.Player player = event.getPlayer();
        if (stateManager.getState(player.getUniqueId()) != PlayerState.LOBBY) {
            return;
        }
        soundService.play(player, "gui-open");
        openMenu.accept(player);
    }
}
