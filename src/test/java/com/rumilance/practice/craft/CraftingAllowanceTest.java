package com.rumilance.practice.craft;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingAllowanceTest {

    @Test
    void planksResultsAreAllowed() {
        assertTrue(CraftingAllowance.isPlanksResult(Material.OAK_PLANKS));
        assertTrue(CraftingAllowance.isPlanksResult(Material.CRIMSON_PLANKS));
        assertTrue(CraftingAllowance.isPlanksResult(Material.BAMBOO_PLANKS));
        assertTrue(CraftingAllowance.isPlanksResult(Material.CHERRY_PLANKS));
    }

    @Test
    void otherResultsAreDenied() {
        assertFalse(CraftingAllowance.isPlanksResult(Material.STICK));
        assertFalse(CraftingAllowance.isPlanksResult(Material.WOODEN_SWORD));
        assertFalse(CraftingAllowance.isPlanksResult(Material.CRAFTING_TABLE));
        assertFalse(CraftingAllowance.isPlanksResult(Material.DIAMOND_SWORD));
        assertFalse(CraftingAllowance.isPlanksResult(null));
    }
}
