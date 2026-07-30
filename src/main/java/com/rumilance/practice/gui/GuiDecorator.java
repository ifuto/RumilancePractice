package com.rumilance.practice.gui;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds tagged GUI items (buttons / decorative placeholders). The old coloured-glass-pane border
 * frame has been removed in favour of a clean, borderless inventory look.
 */
public final class GuiDecorator {

    private GuiDecorator() {
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
