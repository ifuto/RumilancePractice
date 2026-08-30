package com.rumilance.practice.item;

import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Factory for the "Golden Head" item used by practice kits. The item is a golden apple tagged
 * with the {@link ItemKeys#goldenHead()} PDC key so {@code GoldenHeadListener} can recognise it
 * without relying on display name strings.
 */
public final class GoldenHeadItems {

    private GoldenHeadItems() {
    }

    public static ItemStack create() {
        return create(1);
    }

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Golden Head", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Consume for extra absorption", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("and regeneration.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        meta.getPersistentDataContainer().set(ItemKeys.goldenHead(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isGoldenHead(ItemStack item) {
        return com.rumilance.practice.match.GoldenHeadListener.isGoldenHead(item);
    }
}
