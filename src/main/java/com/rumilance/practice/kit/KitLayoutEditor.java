package com.rumilance.practice.kit;

import com.rumilance.practice.util.ItemKeys;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Maps {@link com.rumilance.practice.gui.menus.EditKitGui} chest slots to player layout
 * indices (0-40) and applies vanilla-style cursor pickup/place for rearrange-only editing.
 */
public final class KitLayoutEditor {

    private KitLayoutEditor() {
    }

    /**
     * @return layout index 0-40, or -1 for chrome (save/back/decorations).
     */
    public static int layoutIndexForGuiSlot(int guiSlot) {
        if (guiSlot < 0 || guiSlot >= 45) {
            return -1;
        }
        int row = guiSlot / 9;
        int col = guiSlot % 9;
        if (row == 0) {
            return switch (col) {
                case 1 -> 36;
                case 2 -> 37;
                case 3 -> 38;
                case 4 -> 39;
                case 6 -> 40;
                default -> -1;
            };
        }
        if (row >= 1 && row <= 3) {
            return 9 + (row - 1) * 9 + col;
        }
        if (row == 4) {
            return col;
        }
        return -1;
    }

    public static boolean isKitSlot(int guiSlot) {
        return layoutIndexForGuiSlot(guiSlot) >= 0;
    }

    public static int guiSlotForLayoutIndex(int layoutIndex) {
        if (layoutIndex >= 0 && layoutIndex <= 8) {
            return 36 + layoutIndex;
        }
        if (layoutIndex >= 9 && layoutIndex <= 35) {
            return 9 + (layoutIndex - 9);
        }
        return switch (layoutIndex) {
            case 36 -> 1;
            case 37 -> 2;
            case 38 -> 3;
            case 39 -> 4;
            case 40 -> 6;
            default -> -1;
        };
    }

    /**
     * Vanilla-style left click: empty cursor picks up; cursor with item places or swaps.
     */
    public static void handleSlotPickup(org.bukkit.entity.Player player, ItemStack[] layout, int layoutIndex) {
        if (layout == null || layoutIndex < 0 || layoutIndex >= layout.length) {
            return;
        }
        var view = player.getOpenInventory();
        ItemStack cursor = view.getCursor();
        ItemStack current = layout[layoutIndex];
        if (cursor == null || cursor.getType().isAir()) {
            if (current != null && !current.getType().isAir()) {
                view.setCursor(stripEditorTags(current.clone()));
                layout[layoutIndex] = null;
            }
            return;
        }
        ItemStack placed = stripEditorTags(cursor.clone());
        layout[layoutIndex] = placed;
        if (current != null && !current.getType().isAir()) {
            view.setCursor(stripEditorTags(current.clone()));
        } else {
            view.setCursor(null);
        }
    }

    /** After an allowed drag within the top inventory, copy displayed items back into {@code layout}. */
    public static void syncLayoutFromTopInventory(Inventory top, ItemStack[] layout) {
        if (top == null || layout == null || layout.length < 41) {
            return;
        }
        for (int i = 0; i <= 40; i++) {
            int guiSlot = guiSlotForLayoutIndex(i);
            if (guiSlot < 0 || guiSlot >= top.getSize()) {
                continue;
            }
            layout[i] = itemFromDisplay(top.getItem(guiSlot));
        }
    }

    static ItemStack itemFromDisplay(ItemStack displayed) {
        if (displayed == null || displayed.getType().isAir()) {
            return null;
        }
        if (KitLayoutContents.isPlaceholder(displayed)) {
            return null;
        }
        return stripEditorTags(displayed.clone());
    }

    /** Places armor into armor slots; other items into first free storage/hotbar slot. */
    public static boolean addToLayout(ItemStack[] layout, ItemStack item) {
        if (layout == null || item == null || item.getType().isAir()) {
            return false;
        }
        int armorSlot = armorSlotIndex(item);
        if (armorSlot >= 0 && armorSlot < layout.length) {
            layout[armorSlot] = item.clone();
            return true;
        }
        for (int i = 9; i <= 35; i++) {
            if (isEmpty(layout[i])) {
                layout[i] = item.clone();
                return true;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (isEmpty(layout[i])) {
                layout[i] = item.clone();
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || KitLayoutContents.isPlaceholder(stack);
    }

    private static int armorSlotIndex(ItemStack item) {
        if (item == null) {
            return -1;
        }
        String name = item.getType().name();
        if (name.endsWith("_HELMET") || name.endsWith("_HEAD") || name.endsWith("_SKULL")
                || name.equals("CARVED_PUMPKIN")) {
            return 36;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return 37;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 38;
        }
        if (name.endsWith("_BOOTS")) {
            return 39;
        }
        if (name.equals("SHIELD")) {
            return 40;
        }
        return -1;
    }

    static ItemStack stripEditorTags(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return stack;
        }
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(ItemKeys.guiAction());
        copy.setItemMeta(meta);
        return copy;
    }
}
