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
        player.getInventory().clear();
        player.getInventory().setItem(0, tagged(HUB, Material.NETHER_STAR,
                UiTheme.menuTitle("Party Hub"), UiTheme.hint("設定・メンバー管理")));
        player.getInventory().setItem(1, tagged(INVITE, Material.PLAYER_HEAD,
                Component.text("Invite", UiTheme.SUCCESS), UiTheme.hint("オンライン招待")));
        if (owner) {
            player.getInventory().setItem(2, tagged(START, Material.DIAMOND_SWORD,
                    Component.text("Start Battle", UiTheme.WARNING), UiTheme.hint("キット選択へ")));
            player.getInventory().setItem(3, tagged(PUBLIC, Material.ENDER_EYE,
                    Component.text("Visibility", UiTheme.SECONDARY), UiTheme.hint("公開/非公開切替")));
            player.getInventory().setItem(4, tagged(FF, friendlyFire ? Material.TNT : Material.SHIELD,
                    Component.text("Friendly Fire", friendlyFire ? UiTheme.DANGER : UiTheme.SUCCESS),
                    UiTheme.line(friendlyFire ? "ON — 味方に当たる" : "OFF — 味方に当たらない")));
            if (hasPartyMaps) {
                player.getInventory().setItem(5, tagged(MAP, Material.FILLED_MAP,
                        Component.text("Map", UiTheme.PRIMARY), UiTheme.hint("パーティーマップ選択")));
            }
        }
        player.getInventory().setItem(7, tagged("ekit", Material.CHEST,
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
