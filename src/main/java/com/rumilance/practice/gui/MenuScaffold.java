package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Layout helpers that draw the standard RumilancePractice menu chrome (dark fill,
 * thin top accent, navigation buttons) into an {@link Inventory}. Every public method
 * returns the inventory it was given so calls can be chained. The content "grid" for a
 * standard 6-row menu is rows 1-4, columns 1-7 (28 slots), matching the existing
 * kit/player selectors, while row 0 stays reserved for the accent strip and row 5 for nav.
 */
public final class MenuScaffold {

    public static final int ROWS = 6;
    public static final int SIZE = ROWS * GuiSlots.ROW_SIZE;

    private MenuScaffold() {
    }

    /** Fills every slot with the black background tile. */
    public static Inventory fillBackground(Inventory inventory) {
        ItemStack filler = ItemBuilder.background();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        return inventory;
    }

    private static int rowCount(Inventory inventory) {
        return inventory.getSize() / GuiSlots.ROW_SIZE;
    }

    private static boolean rowInBounds(Inventory inventory, int row) {
        return row >= 0 && row < rowCount(inventory);
    }

    /** Draws a horizontal accent bar across an entire row (no-op when row is out of range). */
    public static Inventory bar(Inventory inventory, int row) {
        if (!rowInBounds(inventory, row)) {
            return inventory;
        }
        ItemStack accent = ItemBuilder.accent();
        for (int col = 0; col < GuiSlots.ROW_SIZE; col++) {
            inventory.setItem(GuiSlots.slot(row, col), accent);
        }
        return inventory;
    }

    /**
     * Frames a rectangular region. Prefer {@link #chrome} for menus — gray glass frames
     * were dropped from the house style; this remains for rare admin panels.
     */
    public static Inventory frame(Inventory inventory, int fromRow, int fromCol, int toRow, int toCol) {
        ItemStack panel = ItemBuilder.panel();
        for (int row = fromRow; row <= toRow; row++) {
            for (int col = fromCol; col <= toCol; col++) {
                boolean edge = row == fromRow || row == toRow || col == fromCol || col == toCol;
                if (edge) {
                    inventory.setItem(GuiSlots.slot(row, col), panel);
                }
            }
        }
        return inventory;
    }

    /**
     * Standard chrome: dark fill, cyan accent top + subtle bottom bar on the last row.
     * Adapts to 5-row kit editors and 6-row menus without indexing past inventory size.
     */
    public static Inventory chrome(Inventory inventory) {
        fillBackground(inventory);
        bar(inventory, 0);
        int rows = rowCount(inventory);
        if (rows >= 6) {
            softBar(inventory, 5);
        } else if (rows >= 2) {
            softBar(inventory, rows - 1);
        }
        return inventory;
    }

    /** Top accent only — for 5-row kit editor where the last row is player slots. */
    public static Inventory editorChrome(Inventory inventory) {
        fillBackground(inventory);
        bar(inventory, 0);
        return inventory;
    }

    private static Inventory softBar(Inventory inventory, int row) {
        if (!rowInBounds(inventory, row)) {
            return inventory;
        }
        ItemStack soft = ItemBuilder.of(UiTheme.ACCENT_SOFT).action("decorate").build();
        for (int col = 0; col < GuiSlots.ROW_SIZE; col++) {
            inventory.setItem(GuiSlots.slot(row, col), soft);
        }
        return inventory;
    }

    /** Places a titled section header in the middle of an accent bar row. */
    public static Inventory header(Inventory inventory, int row, Component title) {
        inventory.setItem(GuiSlots.slot(row, 4),
                ItemBuilder.of(Material.NETHER_STAR).name(title).glint(true).action("decorate").build());
        return inventory;
    }

    /** Standard close button (bottom-centre). */
    public static Inventory closeButton(Inventory inventory) {
        return closeButton(inventory, Component.text("Close", UiTheme.DANGER));
    }

    public static Inventory closeButton(Inventory inventory, Component label) {
        int lastRow = rowCount(inventory) - 1;
        if (lastRow >= 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 4),
                    ItemBuilder.action(UiTheme.CLOSE, label, "close"));
        }
        return inventory;
    }

    /** Places a back button at the bottom-left. */
    public static Inventory backButton(Inventory inventory) {
        return backButton(inventory, Component.text("Back", UiTheme.WARNING));
    }

    public static Inventory backButton(Inventory inventory, Component label) {
        int lastRow = rowCount(inventory) - 1;
        if (lastRow >= 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 1),
                    ItemBuilder.action(UiTheme.BACK, label, "back"));
        }
        return inventory;
    }

    /** Places a confirm/select button at the bottom-right. */
    public static Inventory confirmButton(Inventory inventory, Component label) {
        int lastRow = rowCount(inventory) - 1;
        if (lastRow >= 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 7),
                    ItemBuilder.action(UiTheme.CONFIRM, label, "confirm"));
        }
        return inventory;
    }

    /**
     * Fills the 28-slot content grid (rows 1-4, cols 1-7) with items from {@code items},
     * returning the number actually placed. Useful for paged selectors.
     */
    public static int fillContentGrid(Inventory inventory, List<ItemStack> items, int offset) {
        int placed = 0;
        int index = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int sourceIndex = offset + index;
                if (sourceIndex >= items.size()) {
                    return placed;
                }
                inventory.setItem(GuiSlots.slot(row, col), items.get(sourceIndex));
                placed++;
                index++;
            }
        }
        return placed;
    }

    /**
     * @return the slot index for the {@code gridIndex}-th content slot (0-based) within the
     *         standard 28-slot content grid (rows 1-4, cols 1-7).
     */
    public static int gridSlot(int gridIndex) {
        int row = 1 + gridIndex / 7;
        int col = 1 + gridIndex % 7;
        return GuiSlots.slot(row, col);
    }

    /** @return last valid row index for {@code inventory}. */
    public static int lastRow(Inventory inventory) {
        return Math.max(0, rowCount(inventory) - 1);
    }

    /** Number of items one page of the standard content grid can hold. */
    public static int gridPageSize() {
        return 28;
    }

    /** Places previous/next page buttons when the list spans more than one page. */
    public static void pagingButtons(Inventory inventory, int page, int totalItems) {
        int lastRow = rowCount(inventory) - 1;
        if (lastRow < 0) {
            return;
        }
        int pageSize = gridPageSize();
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(lastRow, 2),
                    ItemBuilder.of(UiTheme.PREV_PAGE)
                            .name(Component.text("◀ Previous", UiTheme.PRIMARY))
                            .lore(UiTheme.line("Page " + page))
                            .action("page:prev")
                            .build());
        }
        if ((long) (page + 1) * pageSize < totalItems) {
            inventory.setItem(GuiSlots.slot(lastRow, 6),
                    ItemBuilder.of(UiTheme.NEXT_PAGE)
                            .name(Component.text("Next ▶", UiTheme.PRIMARY))
                            .lore(UiTheme.line("Page " + (page + 2)))
                            .action("page:next")
                            .build());
        }
    }

}
