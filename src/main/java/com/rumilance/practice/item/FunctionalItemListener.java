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
    private Consumer<Player> openBattle = p -> {
    };
    private Consumer<Player> openTitles = p -> {
    };
    private Consumer<Player> openParty = p -> {
    };
    private Consumer<Player> openPartyInvite = p -> {
    };
    private Consumer<Player> openPartyStart = p -> {
    };
    private Consumer<Player> openPartyMap = p -> {
    };
    private Consumer<Player> partyLeave = p -> {
    };
    private Consumer<Player> partyTogglePublic = p -> {
    };
    private Consumer<Player> partyToggleFf = p -> {
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

    public void setOpenBattle(Consumer<Player> openBattle) {
        this.openBattle = openBattle == null ? p -> { } : openBattle;
    }

    public void setOpenTitles(Consumer<Player> openTitles) {
        this.openTitles = openTitles;
    }

    public void setOpenParty(Consumer<Player> openParty) {
        this.openParty = openParty;
    }

    public void setOpenPartyInvite(Consumer<Player> openPartyInvite) {
        this.openPartyInvite = openPartyInvite == null ? p -> { } : openPartyInvite;
    }

    public void setOpenPartyStart(Consumer<Player> openPartyStart) {
        this.openPartyStart = openPartyStart == null ? p -> { } : openPartyStart;
    }

    public void setOpenPartyMap(Consumer<Player> openPartyMap) {
        this.openPartyMap = openPartyMap == null ? p -> { } : openPartyMap;
    }

    public void setPartyLeave(Consumer<Player> partyLeave) {
        this.partyLeave = partyLeave == null ? p -> { } : partyLeave;
    }

    public void setPartyTogglePublic(Consumer<Player> partyTogglePublic) {
        this.partyTogglePublic = partyTogglePublic == null ? p -> { } : partyTogglePublic;
    }

    public void setPartyToggleFf(Consumer<Player> partyToggleFf) {
        this.partyToggleFf = partyToggleFf == null ? p -> { } : partyToggleFf;
    }

    public static ItemStack create(String functionType, Material material, Component name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        Component safeName = stripVariationSelectors(name);
        meta.displayName(safeName.decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ItemKeys.functionType(), PersistentDataType.STRING, functionType);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Strips VS16/VS18 variation selectors that render as tofu on some clients. */
    public static Component stripVariationSelectors(Component component) {
        if (component == null) {
            return Component.empty();
        }
        return component.replaceText(builder -> builder
                .match("[\uFE0E\uFE0F]")
                .replacement(""));
    }

    public static String stripVariationSelectors(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\uFE0E", "").replace("\uFE0F", "");
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
        // The "menu" compass is handled exclusively by LobbyCompassListener (LOWEST priority,
        // WorldEdit-safe). Handling it here too caused the Game Menu to open twice per click.
        if ("menu".equalsIgnoreCase(function)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Player player = event.getPlayer();
        soundService.play(player, "gui-open", 1.4f);
        switch (function.toLowerCase(Locale.ROOT)) {
            case "ranked" -> rankedGui.open(player);
            case "unranked" -> unrankedGui.open(player);
            case "ffa" -> openFfa.accept(player);
            case "ekit" -> openEkit.accept(player);
            case "settings" -> openSettings.accept(player);
            case "spectate" -> openSpectate.accept(player);
            case "titles" -> openTitles.accept(player);
            case "party" -> openParty.accept(player);
            case "party_hub" -> openParty.accept(player);
            case "party_invite" -> openPartyInvite.accept(player);
            case "party_start" -> openPartyStart.accept(player);
            case "party_map" -> openPartyMap.accept(player);
            case "party_ff" -> partyToggleFf.accept(player);
            case "party_leave" -> partyLeave.accept(player);
            case "party_public" -> partyTogglePublic.accept(player);
            case "battle" -> openBattle.accept(player);
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
