package com.rumilance.practice.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialFlagsTest {

    @Test
    void glassMatchesNameSuffixRules() {
        assertTrue(MaterialFlags.isGlass(Material.GLASS));
        assertTrue(MaterialFlags.isGlass(Material.TINTED_GLASS));
        assertTrue(MaterialFlags.isGlass(Material.WHITE_STAINED_GLASS_PANE));
        assertFalse(MaterialFlags.isGlass(Material.GLASS_BOTTLE));
        assertFalse(MaterialFlags.isGlass(Material.STONE));
    }
}
