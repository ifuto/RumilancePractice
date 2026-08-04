package com.rumilance.practice.admin;

import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.Cuboid;
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

    private final LobbyService lobbyService;
    private final SoundService soundService;

    public AdminToolListener(LobbyService lobbyService, SoundService soundService) {
        this.lobbyService = lobbyService;
        this.soundService = soundService;
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
            event.getPlayer().sendMessage(Component.text(
                    "Setup: /slobby, /arena, /kit, /setfunc, /practiceadmin status", NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if ("region".equals(tool) && event.getClickedBlock() != null) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                AdminTools.setPos1(event.getPlayer(), event.getClickedBlock().getLocation());
                event.getPlayer().sendActionBar(Component.text("pos1 set", NamedTextColor.GREEN));
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                AdminTools.setPos2(event.getPlayer(), event.getClickedBlock().getLocation());
                var p1 = AdminTools.pos1(event.getPlayer());
                var p2 = AdminTools.pos2(event.getPlayer());
                if (p1 != null && p2 != null) {
                    try {
                        lobbyService.setRegion(Cuboid.of(p1, p2));
                    } catch (IllegalArgumentException e) {
                        // pos1 and pos2 in different worlds (or an unloaded world)
                        event.getPlayer().sendMessage(Component.text(
                                "pos1 と pos2 は同じワールドである必要があります。", NamedTextColor.RED));
                    }
                }
                event.getPlayer().sendActionBar(Component.text("pos2 set", NamedTextColor.GREEN));
            }
        }
    }
}
