package com.rumilance.practice.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure helper functions for working with Bukkit inventory slot indices (0-based,
 * 9 columns per row). Kept free of Bukkit types so it can be unit tested directly.
 */
public final class GuiSlots {

    public static final int ROW_SIZE = 9;

    private GuiSlots() {
    }

    /**
     * @return the 0-based slot index for the given 0-based row/column pair.
     */
    public static int slot(int row, int column) {
        if (row < 0) {
            throw new IllegalArgumentException("row must not be negative: " + row);
        }
        if (column < 0 || column >= ROW_SIZE) {
            throw new IllegalArgumentException("column must be within [0, " + (ROW_SIZE - 1) + "]: " + column);
        }
        return row * ROW_SIZE + column;
    }

    public static int row(int slot) {
        return Math.floorDiv(slot, ROW_SIZE);
    }

    public static int column(int slot) {
        return Math.floorMod(slot, ROW_SIZE);
    }

    public static boolean isValidSlot(int slot, int inventorySize) {
        return slot >= 0 && slot < inventorySize;
    }

    /**
     * @return an inclusive range of slots {@code [from, to]}, in ascending order.
     */
    public static int[] range(int from, int to) {
        if (to < from) {
            throw new IllegalArgumentException("to must be >= from (from=" + from + ", to=" + to + ")");
        }
        int[] result = new int[to - from + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = from + i;
        }
        return result;
    }

    /**
     * @return all slots forming the outer border of a rectangular inventory whose
     * size is a multiple of {@link #ROW_SIZE} (e.g. 9, 18, 27, 36, 45, 54).
     */
    public static int[] border(int inventorySize) {
        if (inventorySize <= 0 || inventorySize % ROW_SIZE != 0) {
            throw new IllegalArgumentException("inventorySize must be a positive multiple of " + ROW_SIZE);
        }
        int rows = inventorySize / ROW_SIZE;
        List<Integer> slots = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < ROW_SIZE; c++) {
                boolean edgeRow = r == 0 || r == rows - 1;
                boolean edgeColumn = c == 0 || c == ROW_SIZE - 1;
                if (edgeRow || edgeColumn) {
                    slots.add(slot(r, c));
                }
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * @return the centermost slot of a rectangular inventory. For inventories with an even
     * number of rows or columns, the slot is biased toward the top-left of the true center.
     */
    public static int centerSlot(int inventorySize) {
        if (inventorySize <= 0 || inventorySize % ROW_SIZE != 0) {
            throw new IllegalArgumentException("inventorySize must be a positive multiple of " + ROW_SIZE);
        }
        int rows = inventorySize / ROW_SIZE;
        int centerRow = (rows - 1) / 2;
        int centerColumn = (ROW_SIZE - 1) / 2;
        return slot(centerRow, centerColumn);
    }
}
