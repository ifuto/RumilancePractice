package com.rumilance.practice.gui;

import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Layout helpers that draw the standard RumilancePractice menu chrome (background fill,
 * top/bottom bars, framed content panels, and standard navigation buttons) into an
 * {@link Inventory}. Every public method returns the inventory it was given so calls can be
 * chained. The content "grid" for a standard 6-row menu is rows 1-4, columns 1-7 (28 slots),
 * matching the existing kit/player selectors, while rows 0 and 5 stay reserved for chrome.
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

    /** Draws a horizontal accent bar across an entire row. */
    public static Inventory bar(Inventory inventory, int row) {
        ItemStack accent = ItemBuilder.accent();
        for (int col = 0; col < GuiSlots.ROW_SIZE; col++) {
            inventory.setItem(GuiSlots.slot(row, col), accent);
        }
        return inventory;
    }

    /** Frames a rectangular region with gray panel tiles, leaving the interior empty. */
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
     * Draws the standard 6-row chrome: black background, accent bars on the top and bottom
     * rows, and a gray frame around the content area (rows 1-4, cols 1-7). Call this before
     * placing content so content items overwrite the frame's interior (the interior is left
     * empty, not filled).
     */
    public static Inventory chrome(Inventory inventory) {
        fillBackground(inventory);
        bar(inventory, 0);
        bar(inventory, 5);
        frame(inventory, 1, 0, 4, 8);
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
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.action(UiTheme.CLOSE, label, "close"));
        return inventory;
    }

    /** Places a back button at the bottom-left. */
    public static Inventory backButton(Inventory inventory) {
        return backButton(inventory, Component.text("Back", UiTheme.WARNING));
    }

    public static Inventory backButton(Inventory inventory, Component label) {
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.action(UiTheme.BACK, label, "back"));
        return inventory;
    }

    /** Places a confirm/select button at the bottom-right. */
    public static Inventory confirmButton(Inventory inventory, Component label) {
        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.action(UiTheme.CONFIRM, label, "confirm"));
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

    /** Number of items one page of the standard content grid can hold. */
    public static int gridPageSize() {
        return 28;
    }

    /** Places previous/next page buttons when the list spans more than one page. */
    public static void pagingButtons(Inventory inventory, int page, int totalItems) {
        int pageSize = gridPageSize();
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(5, 2),
                    ItemBuilder.of(UiTheme.PREV_PAGE)
                            .name(Component.text("◀ Previous", UiTheme.PRIMARY))
                            .lore(UiTheme.line("Page " + page))
                            .action("page:prev")
                            .build());
        }
        if ((long) (page + 1) * pageSize < totalItems) {
            inventory.setItem(GuiSlots.slot(5, 6),
                    ItemBuilder.of(UiTheme.NEXT_PAGE)
                            .name(Component.text("Next ▶", UiTheme.PRIMARY))
                            .lore(UiTheme.line("Page " + (page + 2)))
                            .action("page:next")
                            .build());
        }
    }

}
