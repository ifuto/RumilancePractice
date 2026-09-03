package com.rumilance.practice.team;

import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.item.FunctionalItemListener;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Party hotbar while in a team — styled lobby items that open the party GUIs.
 */
public final class PartyHotbar {

    public static final String HUB = "party_hub";
    public static final String INVITE = "party_invite";
    public static final String LEAVE = "party_leave";
    public static final String START = "party_start";
    public static final String PUBLIC = "party_public";
    public static final String MAP = "party_map";
    public static final String FF = "party_ff";

    private final LobbyService lobbyService;

    public PartyHotbar(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    public void give(Player player, boolean owner, boolean hasPartyMaps, boolean friendlyFire) {
        if (player == null) {
            return;
        }
        // Deliberately minimal: every party control (invite, visibility, friendly fire,
        // map select, start battle, side assignment...) lives inside the Party Hub GUI, so
        // the hotbar only keeps things the hub cannot do — open the hub, edit your kit,
        // and leave. (The extra party_* functions stay supported by the item listener for
        // legacy / custom lobby items.)
        player.getInventory().clear();
        player.getInventory().setItem(0, tagged(HUB, Material.NETHER_STAR,
                UiTheme.menuTitle("Party Hub"), UiTheme.hint("メンバー・設定・対戦開始")));
        player.getInventory().setItem(1, tagged("ekit", Material.CHEST,
                Component.text("Kit Edit", UiTheme.PRIMARY), UiTheme.hint("Edit your kit layouts")));
        player.getInventory().setItem(8, tagged(LEAVE, Material.OAK_DOOR,
                Component.text("Leave Party", UiTheme.DANGER), UiTheme.hint("パーティー退出")));
    }

    public void restoreLobby(Player player) {
        if (player == null) {
            return;
        }
        lobbyService.applyLobbyInventory(player);
    }

    private static ItemStack tagged(String function, Material material, Component name, Component hint) {
        ItemStack stack = FunctionalItemListener.create(function, material, name);
        ItemMeta meta = stack.getItemMeta();
        meta.lore(java.util.List.of(hint));
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isPartyFunction(String function) {
        if (function == null) {
            return false;
        }
        return switch (function.toLowerCase()) {
            case HUB, INVITE, LEAVE, START, PUBLIC, MAP, FF -> true;
            default -> false;
        };
    }

    public static String readFunction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().get(ItemKeys.functionType(), PersistentDataType.STRING);
    }
}
