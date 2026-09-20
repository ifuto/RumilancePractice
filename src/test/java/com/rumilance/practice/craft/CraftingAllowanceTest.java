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
    void pressurePlatesAndButtonsAreAllowed() {
        assertTrue(CraftingAllowance.isPressurePlateResult(Material.OAK_PRESSURE_PLATE));
        assertTrue(CraftingAllowance.isPressurePlateResult(Material.HEAVY_WEIGHTED_PRESSURE_PLATE));
        assertTrue(CraftingAllowance.isButtonResult(Material.STONE_BUTTON));
        assertTrue(CraftingAllowance.isButtonResult(Material.POLISHED_BLACKSTONE_BUTTON));
        assertTrue(CraftingAllowance.isAllowedResult(Material.BIRCH_PRESSURE_PLATE));
        assertTrue(CraftingAllowance.isAllowedResult(Material.CHERRY_BUTTON));
    }

    @Test
    void otherResultsAreDenied() {
        assertFalse(CraftingAllowance.isAllowedResult(Material.STICK));
        assertFalse(CraftingAllowance.isAllowedResult(Material.WOODEN_SWORD));
        assertFalse(CraftingAllowance.isAllowedResult(Material.CRAFTING_TABLE));
        assertFalse(CraftingAllowance.isAllowedResult(Material.DIAMOND_SWORD));
        assertFalse(CraftingAllowance.isAllowedResult(null));
    }
}
