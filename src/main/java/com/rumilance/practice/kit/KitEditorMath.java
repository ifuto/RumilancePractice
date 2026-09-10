package com.rumilance.practice.kit;

/**
 * Pure slot-selection rules for the kit editor. The cursor-retention fix (saving a kit
 * while an item rides the cursor must not swallow that item) routes every "where should
 * this item live" decision through here so the local ECJ loop can pin it: armor wins its
 * dedicated slot, everything else flows into the first free storage slot (9-35) then the
 * hotbar (0-8). The Bukkit layer converts ItemStacks to material names and calls in.
 */
public final class KitEditorMath {

    private KitEditorMath() { }

    /**
     * Picks the layout index a cursor-borne item should occupy when it is absorbed into a
     * layout without a designated slot (e.g. on Save while still held on the cursor).
     *
     * @param cursorMaterial material name (e.g. {@code "NETHERITE_HELMET"}); air/blank -> -1
     * @param layout         41 layout slots as material names (null = empty slot)
     * @return layout index 0-40, or -1 when the layout is full
     */
    public static int cursorTarget(String cursorMaterial, String[] layout) {
        if (cursorMaterial == null || cursorMaterial.isBlank() || "AIR".equals(cursorMaterial)) {
            return -1;
        }
        int armor = armorSlot(cursorMaterial);
        if (armor >= 0) {
            return armor;
        }
        for (int i = 9; i <= 35; i++) {
            if (nameAt(layout, i) == null) {
                return i;
            }
        }
        for (int i = 0; i <= 8; i++) {
            if (nameAt(layout, i) == null) {
                return i;
            }
        }
        return -1;
    }

    /** Dedicated armor/offhand slot for a material, or -1 if it is not wearable there. */
    public static int armorSlot(String material) {
        if (material == null) {
            return -1;
        }
        if (material.endsWith("_HELMET") || material.endsWith("_HEAD") || material.endsWith("_SKULL")
                || "CARVED_PUMPKIN".equals(material)) {
            return 36;
        }
        if (material.endsWith("_CHESTPLATE") || "ELYTRA".equals(material)) {
            return 37;
        }
        if (material.endsWith("_LEGGINGS")) {
            return 38;
        }
        if (material.endsWith("_BOOTS")) {
            return 39;
        }
        if ("SHIELD".equals(material)) {
            return 40;
        }
        return -1;
    }

    private static String nameAt(String[] layout, int index) {
        if (layout == null || index < 0 || index >= layout.length) {
            return null;
        }
        String name = layout[index];
        return (name == null || name.isBlank() || "AIR".equals(name)) ? null : name;
    }
}
