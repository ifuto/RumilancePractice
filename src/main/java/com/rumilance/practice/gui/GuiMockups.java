package com.rumilance.practice.gui;

import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The 2026-10 mockup frame language (designed in an inventory editor and saved as JSON).
 *
 * <p>The mockups replace the generic perimeter frame with a per-screen colour scheme:
 * a full ring of stained glass in the screen's accent colour, copper chains as side
 * accents, light-gray/white interior panes named 装飾 (decoration), black panes marking
 * 存在しないマス (cells that do not exist on this screen) and gray panes marking 空き枠
 * (empty slots, e.g. free party member seats). Screens opt in cell by cell — the helpers
 * here just keep the panes and their labels consistent across every mockup-styled GUI.</p>
 */
public final class GuiMockups {

    private GuiMockups() {
    }

    /** 装飾 pane — the light interior filler. */
    public static ItemStack deco(Player player, Material material, MessageService messages) {
        return ItemBuilder.of(material)
                .name(messages.render(messages.resolveLocale(player), "gui.deco")
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate")
                .build();
    }

    /** 存在しないマス — a black pane marking a cell this screen deliberately does not use. */
    public static ItemStack noCell(Player player, MessageService messages) {
        return ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                .name(messages.render(messages.resolveLocale(player), "gui.no-cell")
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate")
                .build();
    }

    /** 空き枠 — a gray pane marking an empty but usable seat (party member grid etc.). */
    public static ItemStack emptySlot(Player player, MessageService messages) {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(messages.render(messages.resolveLocale(player), "gui.empty-slot")
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate")
                .build();
    }

    /** Copper chain accent (unnamed in the mockups). */
    public static ItemStack chain() {
        return ItemBuilder.of(Material.COPPER_CHAIN)
                .action("decorate")
                .build();
    }

    /** Plain accent pane (unnamed) — the mockup ring colour, e.g. orange on the duel screen. */
    public static ItemStack accent(Material material) {
        return ItemBuilder.of(material)
                .action("decorate")
                .build();
    }

    /** Fills {@code slots} with the same stack (a full row / ring segment). */
    public static void fill(Inventory inventory, ItemStack stack, int... slots) {
        for (int slot : slots) {
            inventory.setItem(slot, stack);
        }
    }

    /** All nine slots of one row. */
    public static int[] row(int row) {
        int[] slots = new int[9];
        for (int col = 0; col < 9; col++) {
            slots[col] = row * 9 + col;
        }
        return slots;
    }
}
