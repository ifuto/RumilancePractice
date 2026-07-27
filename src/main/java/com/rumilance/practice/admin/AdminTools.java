package com.rumilance.practice.admin;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin region selector + setup menu item helpers.
 */
public final class AdminTools {

    private static final Map<UUID, Location> POS1 = new ConcurrentHashMap<>();
    private static final Map<UUID, Location> POS2 = new ConcurrentHashMap<>();

    private AdminTools() {
    }

    public static void give(Player player) {
        ItemStack selector = new ItemStack(Material.BLAZE_ROD);
        ItemMeta selectorMeta = selector.getItemMeta();
        selectorMeta.displayName(Component.text("Region Selector", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        selectorMeta.getPersistentDataContainer().set(ItemKeys.adminTool(), PersistentDataType.STRING, "region");
        selector.setItemMeta(selectorMeta);

        ItemStack menu = new ItemStack(Material.NETHER_STAR);
        ItemMeta menuMeta = menu.getItemMeta();
        menuMeta.displayName(Component.text("Practice Setup Menu", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        menuMeta.getPersistentDataContainer().set(ItemKeys.adminTool(), PersistentDataType.STRING, "menu");
        menu.setItemMeta(menuMeta);

        player.getInventory().addItem(selector, menu);
    }

    public static void setPos1(Player player, Location location) {
        POS1.put(player.getUniqueId(), location.clone());
    }

    public static void setPos2(Player player, Location location) {
        POS2.put(player.getUniqueId(), location.clone());
    }

    public static Location pos1(Player player) {
        return POS1.get(player.getUniqueId());
    }

    public static Location pos2(Player player) {
        return POS2.get(player.getUniqueId());
    }
}
