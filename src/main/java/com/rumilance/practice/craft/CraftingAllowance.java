package com.rumilance.practice.craft;

import org.bukkit.Material;

/**
 * Pure decision helper for the server-wide crafting rule: only log/stem -&gt; planks
 * recipes are allowed for regular players (in the 2x2 grid, so no crafting table meta).
 * A result whose material ends with {@code _PLANKS} is treated as a planks recipe —
 * that matches vanilla's log classes, nether stems and bamboo block, and nothing else.
 */
public final class CraftingAllowance {

    private CraftingAllowance() {
    }

    /** True when the crafting result is any kind of planks. */
    public static boolean isPlanksResult(Material resultType) {
        return resultType != null && resultType.name().endsWith("_PLANKS");
    }
}
