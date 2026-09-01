package com.rumilance.practice.originalkit;

import com.rumilance.practice.item.SaveSignItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enforces the original-kit room rules:
 * <ul>
 *   <li>Editors cannot break room blocks (the room — anvils, grindstone, save button — is
 *       pre-built by an admin) and cannot place blocks into the room.</li>
 *   <li>Pressing the special oak SAVE button ({@link SaveSignItem}) triggers a save, after the
 *       strict inventory validation.</li>
 * </ul>
 * Editing happens in creative with the player's live inventory acting as the kit contents, so a
 * save snapshots the player's own inventory.
 */
public final class OriginalKitRoomListener implements Listener {

    private final OriginalKitRoomService roomService;
    private final OriginalKitService originalKitService;

    public OriginalKitRoomListener(OriginalKitRoomService roomService,
                                   OriginalKitService originalKitService) {
        this.roomService = roomService;
        this.originalKitService = originalKitService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!roomService.isEditing(player.getUniqueId())) {
            return;
        }
        // The room is permanent; an editor can never break its blocks.
        if (roomService.inRoom(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You cannot break blocks in the kit room.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!roomService.isEditing(player.getUniqueId())) {
            return;
        }
        Block against = event.getBlockPlaced();
        // Placing the save button is an admin action (admins are not in an edit session).
        // Editors cannot place any block in the room.
        if (roomService.inRoom(against.getLocation())) {
            event.setCancelled(true);
            // Creative placement would otherwise consume the client-side ghost; resync.
            player.updateInventory();
            player.sendActionBar(Component.text("You cannot place blocks in the kit room.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!roomService.isEditing(player.getUniqueId())) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        // The save triggers only on the room's registered SAVE button block (an oak button).
        if (clicked != null && clicked.getType().name().equals("OAK_BUTTON")
                && roomService.inRoom(clicked.getLocation())
                && roomService.isSaveButton(clicked.getLocation())) {
            event.setCancelled(true);
            save(player);
            return;
        }
        // Holding the save-sign item does not save — it must be placed/registered in the room.
        if (SaveSignItem.isSaveButton(item) && roomService.inRoom(clicked.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "The save button must be placed and registered by an admin.",
                    NamedTextColor.YELLOW));
        }
    }

    private void save(Player player) {
        if (!roomService.hasSaveButton()) {
            player.sendActionBar(Component.text(
                    "No save button in this room — ask an admin to place one.",
                    NamedTextColor.RED));
            return;
        }
        // Snapshot the player's current inventory (the kit being built).
        ItemStack[] contents = player.getInventory().getContents();
        OriginalKitSaveValidator.Result result =
                OriginalKitSaveValidator.validate(java.util.Arrays.asList(contents));
        switch (result.severity()) {
            case BAN -> {
                player.kick(Component.text("Illegal admin item in kit: " + result.reason(),
                        NamedTextColor.DARK_RED));
                return;
            }
            case KICK -> {
                player.kick(Component.text("Illegal item in kit: " + result.reason(),
                        NamedTextColor.RED));
                return;
            }
            case REJECT -> {
                player.sendActionBar(Component.text("Save failed: " + result.reason(),
                        NamedTextColor.RED));
                player.sendMessage(OriginalKitSaveValidator.feedback(result));
                return;
            }
            case OK -> {
                // Fall through.
            }
        }
        // Residual potion effects block the save.
        if (OriginalKitSaveValidator.hasResidualEffects(player)) {
            player.sendActionBar(Component.text(
                    "Clear all potion effects before saving.", NamedTextColor.RED));
            return;
        }
        OriginalKitService.EditContext ctx = originalKitService.context(player.getUniqueId());
        int slot = ctx != null ? ctx.slot : 0;
        originalKitService.saveLayout(player, slot, contents);
        player.setGameMode(GameMode.SURVIVAL);
        player.sendActionBar(Component.text("Kit saved!", NamedTextColor.GREEN));
        roomService.exit(player);
    }
}
