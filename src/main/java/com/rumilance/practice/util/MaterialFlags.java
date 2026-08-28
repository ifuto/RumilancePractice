package com.rumilance.practice.util;

import org.bukkit.Material;

/**
 * O(1) material flags. Names match {@code PracticeTnt.isGlass} ({@code GLASS_BOTTLE} is not glass).
 */
public final class MaterialFlags {

    private static final boolean[] GLASS;

    static {
        Material[] values = Material.values();
        GLASS = new boolean[values.length];
        for (Material material : values) {
            String name = material.name();
            GLASS[material.ordinal()] = name.endsWith("GLASS") || name.endsWith("GLASS_PANE");
        }
    }

    private MaterialFlags() {
    }

    public static boolean isGlass(Material material) {
        if (material == null) {
            return false;
        }
        int ordinal = material.ordinal();
        return ordinal >= 0 && ordinal < GLASS.length && GLASS[ordinal];
    }
}
