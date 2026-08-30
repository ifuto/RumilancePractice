package com.rumilance.practice.admin;

import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public final class AdminToolListener implements Listener {

    private final SoundService soundService;
    private java.util.function.Consumer<org.bukkit.entity.Player> openAdminMenu;

    public AdminToolListener(SoundService soundService) {
        this.soundService = soundService;
    }

    public void setOpenAdminMenu(java.util.function.Consumer<org.bukkit.entity.Player> openAdminMenu) {
        this.openAdminMenu = openAdminMenu;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }
        String tool = event.getItem().getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.adminTool(), PersistentDataType.STRING);
        if (tool == null) {
            return;
        }
        event.setCancelled(true);
        if ("menu".equals(tool) && (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            soundService.play(event.getPlayer(), "gui-open");
            if (openAdminMenu != null) {
                openAdminMenu.accept(event.getPlayer());
            } else {
                event.getPlayer().sendMessage(Component.text(
                        "Setup: /slobby, /arena, /kit, /setfunc, /practiceadmin status", NamedTextColor.LIGHT_PURPLE));
            }
            return;
        }
        // Selection tool ONLY records pos1/pos2. It must NEVER write the lobby region as a
        // side effect: doing so silently turned every /arena・/ffa selection into the lobby
        // ("selecting anything becomes the lobby" bug). The lobby region is set exclusively
        // by /slobby pos1 + /slobby pos2.
        if ("region".equals(tool) && event.getClickedBlock() != null) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                AdminTools.setPos1(event.getPlayer(), event.getClickedBlock().getLocation());
                event.getPlayer().sendActionBar(Component.text("pos1 set", NamedTextColor.GREEN));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                AdminTools.setPos2(event.getPlayer(), event.getClickedBlock().getLocation());
                event.getPlayer().sendActionBar(Component.text("pos2 set", NamedTextColor.GREEN));
            }
        }
    }
}
