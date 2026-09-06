package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * ITEM 40 rebuild: the N Arena visual foundation, following the {@code gui.json} mockups.
 *
 * <ul>
 *   <li><b>Frame</b> — every screen is wrapped in a full-perimeter border of its theme
 *       coloured glass pane (rows 0 and last fully filled, columns 0/8 on the inner rows).
 *       The INSIDE stays empty; content is placed sparsely, centred, with room to breathe.</li>
 *   <li><b>Title</b> — a themed icon with the menu name at (0,4).</li>
 *   <li><b>Close/Back</b> — bottom-centre barrier ("close" action — {@link GuiListener}
 *       bounces nested menus to the parent) or arrow ("back").</li>
 *   <li><b>Pages</b> — arrow buttons emitting {@code page:prev} / {@code page:next}.</li>
 * </ul>
 *
 * <p>Only the LOOK is new: every button emits the exact same action strings the existing
 * handlers already understand, so logic stays untouched.</p>
 */
public final class GuiFrame {

    private GuiFrame() {
    }

    /** Per-screen identity: frame colour + matching accent text colour. */
    public enum Theme {
        WHITE(Material.WHITE_STAINED_GLASS_PANE, TextColor.color(0xF8FAFC)),
        LIGHT_BLUE(Material.LIGHT_BLUE_STAINED_GLASS_PANE, TextColor.color(0x38BDF8)),
        LIME(Material.LIME_STAINED_GLASS_PANE, TextColor.color(0x84CC16)),
        YELLOW(Material.YELLOW_STAINED_GLASS_PANE, TextColor.color(0xFACC15)),
        GREEN(Material.GREEN_STAINED_GLASS_PANE, TextColor.color(0x22C55E)),
        CYAN(Material.CYAN_STAINED_GLASS_PANE, TextColor.color(0x22D3EE)),
        ORANGE(Material.ORANGE_STAINED_GLASS_PANE, TextColor.color(0xFB923C)),
        RED(Material.RED_STAINED_GLASS_PANE, TextColor.color(0xF87171)),
        PURPLE(Material.PURPLE_STAINED_GLASS_PANE, TextColor.color(0xC084FC)),
        LIGHT_GRAY(Material.LIGHT_GRAY_STAINED_GLASS_PANE, TextColor.color(0xCBD5E1));

        public final Material pane;
        /** Matching accent for headings/values on this screen. */
        public final TextColor accent;

        Theme(Material pane, TextColor accent) {
            this.pane = pane;
            this.accent = accent;
        }
    }

    private static int rows(Inventory inventory) {
        return inventory.getSize() / GuiSlots.ROW_SIZE;
    }

    /** Clears the whole inventory, then draws the full-perimeter theme frame. */
    public static Inventory frame(Inventory inventory, Theme theme) {
        inventory.clear();
        ItemStack pane = ItemBuilder.hiddenFill(theme.pane);
        int rows = rows(inventory);
        for (int col = 0; col < GuiSlots.ROW_SIZE; col++) {
            inventory.setItem(GuiSlots.slot(0, col), pane);
            if (rows >= 2) {
                inventory.setItem(GuiSlots.slot(rows - 1, col), pane);
            }
        }
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), pane);
            inventory.setItem(GuiSlots.slot(row, GuiSlots.ROW_SIZE - 1), pane);
        }
        return inventory;
    }

    /**
     * Frame for editors whose LAST row belongs to the player content (e.g. the kit editor's
     * player-inventory strip): border on top + sides only.
     */
    public static Inventory frameOpenBottom(Inventory inventory, Theme theme) {
        inventory.clear();
        ItemStack pane = ItemBuilder.hiddenFill(theme.pane);
        int rows = rows(inventory);
        for (int col = 0; col < GuiSlots.ROW_SIZE; col++) {
            inventory.setItem(GuiSlots.slot(0, col), pane);
        }
        for (int row = 1; row < rows; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), pane);
            inventory.setItem(GuiSlots.slot(row, GuiSlots.ROW_SIZE - 1), pane);
        }
        return inventory;
    }

    /** Title icon at (0,4): themed item carrying the menu name, purely decorative. */
    public static Inventory title(Inventory inventory, Material icon, Component name) {
        inventory.setItem(GuiSlots.slot(0, 4),
                ItemBuilder.of(icon).name(name).action("decorate").build());
        return inventory;
    }

    /** Decorative icon anywhere inside the frame (e.g. an opponent's head). */
    public static ItemStack decoration(Material icon, Component name) {
        return ItemBuilder.of(icon).name(name).action("decorate").build();
    }

    /**
     * Bottom-centre exit control: Back arrow on nested screens, Close barrier otherwise —
     * the action stays {@code close} so {@link GuiListener} can route nested menus home.
     */
    public static Inventory close(Inventory inventory, GuiSession session,
                                  Component closeLabel, Component backLabel) {
        boolean nested = session != null && (session.fromGameMenu() || session.fromBattleMenu());
        int lastRow = rows(inventory) - 1;
        if (lastRow >= 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 4), nested
                    ? ItemBuilder.action(Material.ARROW, backLabel, "close")
                    : ItemBuilder.action(Material.BARRIER, closeLabel, "close"));
        }
        return inventory;
    }

    /** Bottom-centre Back arrow (sub-screens with an explicit parent). */
    public static Inventory back(Inventory inventory, Component label) {
        int lastRow = rows(inventory) - 1;
        if (lastRow >= 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 4),
                    ItemBuilder.action(Material.ARROW, label, "back"));
        }
        return inventory;
    }

    /**
     * Page arrows on the bottom-row ends, matching the mockups: prev at (last,0), next at
     * (last,8). Buttons only appear when the direction is available.
     */
    public static Inventory pages(Inventory inventory, boolean hasPrev, boolean hasNext,
                                  Component prevLabel, Component nextLabel) {
        int lastRow = rows(inventory) - 1;
        if (lastRow < 0) {
            return inventory;
        }
        if (hasPrev) {
            inventory.setItem(GuiSlots.slot(lastRow, 0),
                    ItemBuilder.action(Material.ARROW, prevLabel, "page:prev"));
        }
        if (hasNext) {
            inventory.setItem(GuiSlots.slot(lastRow, GuiSlots.ROW_SIZE - 1),
                    ItemBuilder.action(Material.ARROW, nextLabel, "page:next"));
        }
        return inventory;
    }

    /** Page arrows on an inner row: prev at (row,1), next at (row,7). */
    public static Inventory pagesOnRow(Inventory inventory, int row, boolean hasPrev,
                                       boolean hasNext, Component prevLabel, Component nextLabel) {
        if (hasPrev) {
            inventory.setItem(GuiSlots.slot(row, 1),
                    ItemBuilder.action(Material.ARROW, prevLabel, "page:prev"));
        }
        if (hasNext) {
            inventory.setItem(GuiSlots.slot(row, GuiSlots.ROW_SIZE - 2),
                    ItemBuilder.action(Material.ARROW, nextLabel, "page:next"));
        }
        return inventory;
    }

    /** An unavailable/locked filler tile ("Not Available" style). */
    public static ItemStack lockedTile(Component label) {
        return ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(label)
                .action("decorate")
                .build();
    }

    /** Permission-lock tile (VIP / VIP+ gates). */
    public static ItemStack permissionLock(Component label) {
        return ItemBuilder.of(Material.BARRIER)
                .name(label)
                .action("decorate")
                .build();
    }

    /** Standard action button used across screens. */
    public static ItemStack button(Material icon, Component name, String action) {
        return ItemBuilder.action(icon, name, action);
    }

    /** Standard action button with lore lines. */
    public static ItemStack button(Material icon, Component name, java.util.List<Component> lore,
                                   String action) {
        return ItemBuilder.of(icon).name(name).lore(lore.toArray(new Component[0])).action(action).build();
    }
}
