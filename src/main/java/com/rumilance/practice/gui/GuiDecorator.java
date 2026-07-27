package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fills border panes for ranked (purple) / unranked (blue) GUIs.
 */
public final class GuiDecorator {

    private GuiDecorator() {
    }

    public static void decorateBorder(Inventory inventory, boolean ranked) {
        Material pane = ranked ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
        ItemStack border = decorative(pane, " ");
        for (int slot : GuiSlots.border(inventory.getSize())) {
            inventory.setItem(slot, border.clone());
        }
    }

    public static ItemStack decorative(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, "decorate");
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack button(Material material, Component name, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    public static String actionOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ItemKeys.guiAction(), PersistentDataType.STRING);
    }
}
