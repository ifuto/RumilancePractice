package com.rumilance.practice.model;

import com.rumilance.practice.util.KitBlockRules;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Bed Explosion" kit rule: beds detonate on right click (Nether / End style) and the rule implies
 * the bed placement / break permission, because the bed IS the weapon of that kit.
 */
class KitBedExplosionTest {

    @Test
    void offByDefaultSoExistingKitsKeepVanillaBeds() {
        KitDefinition kit = KitDefinition.builder("nodebuff").build();
        assertFalse(kit.bedExplosion());
    }

    @Test
    void toggleSurvivesTheBuilderRoundTrip() {
        KitDefinition kit = KitDefinition.builder("bedpvp").bedExplosion(true).build();
        assertTrue(kit.bedExplosion());
        assertTrue(kit.toBuilder().build().bedExplosion());
        assertFalse(kit.toBuilder().bedExplosion(false).build().bedExplosion());
        // An unrelated edit must not silently drop the rule.
        assertTrue(kit.toBuilder().pearl(false).build().bedExplosion());
    }

    @Test
    void recordAccessorIsStableAcrossCopies() {
        KitDefinition kit = KitDefinition.builder("bedpvp").bedExplosion(true).totem(true).build();
        KitDefinition copy = kit.toBuilder().displayName("Bed PvP").build();
        assertEquals(kit.bedExplosion(), copy.bedExplosion());
        assertTrue(copy.totem());
    }

    @Test
    void ruleImpliesBedPlacementEvenWithoutGeneralBlockPlace() {
        KitDefinition plain = KitDefinition.builder("nodebuff").build();
        assertFalse(KitBlockRules.mayPlace(plain));

        KitDefinition bedKit = KitDefinition.builder("bedpvp").bedExplosion(true).build();
        assertTrue(KitBlockRules.mayPlace(bedKit));
        // ... and the beds of a bed-bombing kit may be broken again (clean-up / re-use).
        assertTrue(KitBlockRules.mayBreak(bedKit, Material.RED_BED, false));
        assertTrue(KitBlockRules.mayBreak(bedKit, Material.BLACK_BED, true));
        // The rule does not turn the kit into a general terraforming kit.
        assertFalse(KitBlockRules.mayBreak(bedKit, Material.OBSIDIAN, false));
        assertFalse(KitBlockRules.mayBreak(bedKit, Material.GLASS, true));
        assertFalse(KitBlockRules.mayBreak(null, Material.RED_BED, true));
    }

    @Test
    void glassStaysProtectedEvenWithTheRule() {
        KitDefinition bedKit = KitDefinition.builder("bedpvp").bedExplosion(true).blockBreak(true).build();
        assertFalse(KitBlockRules.mayBreak(bedKit, Material.GLASS, false));
        assertTrue(KitBlockRules.mayBreak(bedKit, Material.OBSIDIAN, false));
    }
}
