package com.rumilance.practice.practice;

import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Hotbar / practice ItemStacks tagged with PDC action keys.
 */
public final class PracticeItems {

    public static final String ACTION_DURATION = "prac_duration";
    public static final String ACTION_LAYOUT = "prac_layout";
    public static final String ACTION_START = "prac_start";
    public static final String ACTION_MACE_SETTINGS = "prac_mace_settings";
    public static final String ACTION_BOT_SETTINGS = "prac_bot_settings";

    public static final String LAYOUT_ANCHOR_FIRST = "anchor_first";
    public static final String LAYOUT_GLOW_FIRST = "glow_first";

    private PracticeItems() {
    }

    public static ItemStack durationClock(int seconds) {
        return ItemBuilder.of(Material.CLOCK)
                .name(Component.text("Duration: " + seconds + "s", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Right-click to cycle 5/10/15/30", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(ACTION_DURATION)
                .build();
    }

    public static ItemStack layoutSword() {
        return ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text("Edit Layout", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Anchor 1st / Glowstone 1st", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(ACTION_LAYOUT)
                .build();
    }

    public static ItemStack startDye() {
        return ItemBuilder.of(Material.LIME_DYE)
                .name(Component.text("Start Practice", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD))
                .lore(Component.text("Right-click to start (5s countdown)", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(ACTION_START)
                .build();
    }

    public static ItemStack maceSettings() {
        return ItemBuilder.of(Material.MACE)
                .name(Component.text("Mace Settings", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Density / Breach / Wind Burst", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(ACTION_MACE_SETTINGS)
                .build();
    }

    public static ItemStack botSettings(boolean shieldUp) {
        return ItemBuilder.of(Material.SHIELD)
                .name(Component.text("Bot Settings", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Shield: " + (shieldUp ? "UP" : "DOWN"), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .action(ACTION_BOT_SETTINGS)
                .build();
    }

    public static ItemStack[] defaultLayout(String layoutKey) {
        ItemStack anchor = new ItemStack(Material.RESPAWN_ANCHOR, 64);
        ItemStack glow = new ItemStack(Material.GLOWSTONE, 64);
        if (LAYOUT_GLOW_FIRST.equals(layoutKey)) {
            return new ItemStack[]{glow, anchor};
        }
        return new ItemStack[]{anchor, glow};
    }

    public static ItemStack buildMace(int density, int breach, int windBurst) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.displayName(Component.text("Practice Mace", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        mace.setItemMeta(meta);
        applyEnchant(mace, "density", density);
        applyEnchant(mace, "breach", breach);
        applyEnchant(mace, "wind_burst", windBurst);
        return mace;
    }

    private static void applyEnchant(ItemStack stack, String key, int level) {
        if (level <= 0) {
            return;
        }
        Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (ench != null) {
            stack.addUnsafeEnchantment(ench, level);
        }
    }

    public static String readAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(ItemKeys.guiAction(), PersistentDataType.STRING);
    }
}
