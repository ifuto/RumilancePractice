package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inner-kit identity rules: how a typed preset name becomes a storage id, and the guarantee that
 * {@code default} stays the kit itself. Deliberately covers only the pure helpers — the rest of
 * {@link InnerKitService} needs a running server (kits.yml, ItemStack layouts).
 */
final class InnerKitServiceTest {

    @Test
    void slugBuildsStableStorageIds() {
        assertEquals("hq-style-axe", InnerKitService.slug("HQ Style Axe"));
        assertEquals("club-style-axe", InnerKitService.slug("Club Style Axe"));
        assertEquals("hatena-style-axe", InnerKitService.slug("Hatena Style Axe"));
    }

    @Test
    void slugNormalisesSeparatorsAndNoise() {
        // underscores, repeated spaces and a padded edge all fold to single dashes
        assertEquals("club-style-axe", InnerKitService.slug("  Club_Style  Axe "));
        // punctuation that cannot be stored is dropped, not escaped
        assertEquals("hatena-style-axe", InnerKitService.slug("Hatena Style Axe!!"));
        assertEquals("a-b", InnerKitService.slug("--a---b--"));
    }

    @Test
    void slugRejectsNamesWithNothingStorable() {
        assertNull(InnerKitService.slug(null));
        assertNull(InnerKitService.slug("   "));
        assertNull(InnerKitService.slug("!!!"));
        assertNull(InnerKitService.slug("日本語名"));
    }

    @Test
    void slugStaysWithinTheConfigKeyBudget() {
        String longName = "a".repeat(80);
        assertEquals(48, InnerKitService.slug(longName).length());
    }

    @Test
    void defaultIsNeverAStorablePreset() {
        // the reserved id is the kit itself: create() refuses it, remove()/saveLayout() short-circuit
        assertEquals(InnerKitService.DEFAULT_ID, InnerKitService.slug("Default"));
        assertEquals(InnerKitService.DEFAULT_ID, InnerKitService.slug(" default "));
        assertTrue(InnerKitService.isDefault(null));
        assertTrue(InnerKitService.isDefault(""));
        assertTrue(InnerKitService.isDefault("   "));
        assertTrue(InnerKitService.isDefault("default"));
        assertTrue(InnerKitService.isDefault("DEFAULT"));
        assertTrue(InnerKitService.isDefault(" default "));
        assertFalse(InnerKitService.isDefault("club-style-axe"));
        // a real preset must not be mistaken for the base loadout
        assertFalse(InnerKitService.isDefault("hq-style-axe"));
    }

    @Test
    void normalizeIdIsTrimmedAndLowerCase() {
        assertEquals("club-style-axe", InnerKitService.normalizeId(" Club-Style-Axe "));
        assertEquals("", InnerKitService.normalizeId(null));
        assertEquals("", InnerKitService.normalizeId(""));
    }

    @Test
    void badgeIsWhatEveryPickerShows() {
        // the duel/team/edit pickers and the GUI lore all build their labels from this one constant
        assertEquals("[Default]", InnerKitService.DEFAULT_BADGE);
        assertEquals("HQ Style Axe[Default]", "HQ Style Axe" + InnerKitService.DEFAULT_BADGE);
    }
}
