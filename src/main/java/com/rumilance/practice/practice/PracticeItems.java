package com.rumilance.practice.practice;

import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.util.ItemKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
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

    private static Component name(MessageService messages, Player player, String key,
                                  net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... tags) {
        return messages.render(player, key, tags).decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack durationClock(MessageService messages, Player player, int seconds) {
        return ItemBuilder.of(Material.CLOCK)
                .name(name(messages, player, "practice.item-duration",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
                                .unparsed("secs", String.valueOf(seconds))))
                .lore(name(messages, player, "practice.item-duration-lore"))
                .action(ACTION_DURATION)
                .build();
    }

    public static ItemStack layoutSword(MessageService messages, Player player) {
        return ItemBuilder.of(Material.IRON_SWORD)
                .name(name(messages, player, "practice.item-layout"))
                .lore(name(messages, player, "practice.item-layout-lore"))
                .action(ACTION_LAYOUT)
                .build();
    }

    public static ItemStack startDye(MessageService messages, Player player) {
        return ItemBuilder.of(Material.LIME_DYE)
                .name(name(messages, player, "practice.item-start"))
                .lore(name(messages, player, "practice.item-start-lore"))
                .action(ACTION_START)
                .build();
    }

    public static ItemStack maceSettings(MessageService messages, Player player) {
        return ItemBuilder.of(Material.MACE)
                .name(name(messages, player, "practice.item-mace-settings"))
                .lore(name(messages, player, "practice.item-mace-settings-lore"))
                .action(ACTION_MACE_SETTINGS)
                .build();
    }

    public static ItemStack botSettings(MessageService messages, Player player, boolean shieldUp) {
        String state = messages.raw(player, shieldUp ? "practice.item-shield-up" : "practice.item-shield-down");
        return ItemBuilder.of(Material.SHIELD)
                .name(name(messages, player, "practice.item-bot-settings"))
                .lore(name(messages, player, "practice.item-bot-shield",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("state", state)))
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

    public static ItemStack buildMace(MessageService messages, Player player,
                                      int density, int breach, int windBurst) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.displayName(name(messages, player, "practice.item-mace-name"));
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
