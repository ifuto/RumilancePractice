package com.rumilance.practice.craft;

import org.bukkit.Material;

/**
 * Pure decision helper for the server-wide crafting rule: log/stem -&gt; planks,
 * pressure-plate and button recipes are allowed for regular players. The result-based
 * check covers both the 2x2 inventory grid and crafting tables, including every wood
 * family, weighted plates and stone/blackstone buttons.
 */
public final class CraftingAllowance {

    private CraftingAllowance() {
    }

    /** True when the crafting result is any kind of planks. */
    public static boolean isPlanksResult(Material resultType) {
        return hasSuffix(resultType, "_PLANKS");
    }

    /** True when the crafting result is any vanilla pressure plate. */
    public static boolean isPressurePlateResult(Material resultType) {
        return hasSuffix(resultType, "_PRESSURE_PLATE");
    }

    /** True when the crafting result is any vanilla button. */
    public static boolean isButtonResult(Material resultType) {
        return hasSuffix(resultType, "_BUTTON");
    }

    /** True when the recipe is part of the deliberately small allowed crafting set. */
    public static boolean isAllowedResult(Material resultType) {
        return isPlanksResult(resultType)
                || isPressurePlateResult(resultType)
                || isButtonResult(resultType);
    }

    private static boolean hasSuffix(Material resultType, String suffix) {
        return resultType != null && resultType.name().endsWith(suffix);
    }
}
