package com.rumilance.practice.item;

import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Right-click functional lobby items identified via PDC function_type.
 */
public final class FunctionalItemListener implements Listener {

    private final SoundService soundService;
    private final QueueCoordinator queueCoordinator;
    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private Consumer<Player> openSettings = p -> {
    };
    private Consumer<Player> openFfa = p -> {
    };
    private Consumer<Player> openEkit = p -> {
    };
    private Consumer<Player> openSpectate = p -> {
    };
    private Consumer<Player> openMenu = p -> {
    };
    private Consumer<Player> openTitles = p -> {
    };
    private Consumer<Player> openParty = p -> {
    };

    public FunctionalItemListener(
            SoundService soundService,
            QueueCoordinator queueCoordinator,
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui
    ) {
        this.soundService = soundService;
        this.queueCoordinator = queueCoordinator;
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
    }

    public void setOpenSettings(Consumer<Player> openSettings) {
        this.openSettings = openSettings;
    }

    public void setOpenFfa(Consumer<Player> openFfa) {
        this.openFfa = openFfa;
    }

    public void setOpenEkit(Consumer<Player> openEkit) {
        this.openEkit = openEkit;
    }

    public void setOpenSpectate(Consumer<Player> openSpectate) {
        this.openSpectate = openSpectate;
    }

    public void setOpenMenu(Consumer<Player> openMenu) {
        this.openMenu = openMenu;
    }

    public void setOpenTitles(Consumer<Player> openTitles) {
        this.openTitles = openTitles;
    }

    public void setOpenParty(Consumer<Player> openParty) {
        this.openParty = openParty;
    }

    public static ItemStack create(String functionType, Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.functionType(), PersistentDataType.STRING, functionType);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
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
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ItemKeys.leaveQueue(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
            queueCoordinator.leave(event.getPlayer());
            return;
        }
        String function = pdc.get(ItemKeys.functionType(), PersistentDataType.STRING);
        if (function == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        soundService.play(player, "gui-open", 1.4f);
        switch (function.toLowerCase(Locale.ROOT)) {
            case "ranked" -> rankedGui.open(player);
            case "unranked" -> unrankedGui.open(player);
            case "ffa" -> openFfa.accept(player);
            case "ekit" -> openEkit.accept(player);
            case "settings" -> openSettings.accept(player);
            case "spectate" -> openSpectate.accept(player);
            case "menu" -> {
                if (openMenu == null) {
                    soundService.play(player, "error");
                } else {
                    openMenu.accept(player);
                }
            }
            case "titles" -> openTitles.accept(player);
            case "party" -> openParty.accept(player);
            case "leavequeue" -> queueCoordinator.leave(player);
            default -> soundService.play(player, "error");
        }
    }

    public static Component rankedName() {
        return Component.text("⚔ Ranked Queue ⚔", NamedTextColor.RED);
    }

    public static Component unrankedName() {
        return Component.text("⚔ Unranked Queue ⚔", NamedTextColor.BLUE);
    }
}
