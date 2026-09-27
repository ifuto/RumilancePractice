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
    void layoutKeyIsCompositeLowerCaseAndStable() {
        // 個人の「中キットの並び」は kit_layouts に置くので、キット本体やクリスタル変種と衝突しない
        // キーであること。innerId は保存用の id（slug 形）で渡り、normalizeId は trim + 小文字化
        // だけなので、大文字小文字や前後空白の違いで別の行ができないことも確認する。
        assertEquals("axe#preset#club-style-axe",
                InnerKitService.layoutKey("Axe", "club-style-axe"));
        assertEquals(InnerKitService.layoutKey("axe", "club-style-axe"),
                InnerKitService.layoutKey(" AXE ", " Club-Style-Axe "));
        assertEquals("axe#preset#", InnerKitService.layoutKey("axe", null));
    }

    @Test
    void layoutKeyCannotCollideWithCrystalVariantKeys() {
        // crystal variant keys are "<kit>#v<n>" — even a preset literally named "v3" stays apart.
        String variant = "axe#v3";
        assertFalse(variant.equals(InnerKitService.layoutKey("axe", "v3")));
        assertFalse(variant.equals(InnerKitService.layoutKey("axe", null)));
    }

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
    void badgeIsAppendedToACustomDefaultLabelOnlyOnce() {
        // The default entry's label is set apart from the kit name (/kit preset default), and it
        // still has to read as the locked entry — so the badge is appended when missing...
        assertEquals("HQ Style Axe [Default]", InnerKitService.withBadge("HQ Style Axe"));
        assertEquals("Axe [Default]", InnerKitService.withBadge("  Axe "));
        // ...but an admin who typed the badge keeps their exact text (no double badge).
        assertEquals("HQ Style Axe[Default]", InnerKitService.withBadge("HQ Style Axe[Default]"));
        assertEquals("HQ Style Axe [DEFAULT]", InnerKitService.withBadge("HQ Style Axe [DEFAULT]"));
        // nothing to label -> just the badge
        assertEquals("[Default]", InnerKitService.withBadge(null));
        assertEquals("[Default]", InnerKitService.withBadge("   "));
    }

    @Test
    void badgeIsWhatEveryPickerShows() {
        // the duel/team/edit pickers and the GUI lore all build their labels from this one constant
        assertEquals("[Default]", InnerKitService.DEFAULT_BADGE);
        assertEquals("HQ Style Axe[Default]", "HQ Style Axe" + InnerKitService.DEFAULT_BADGE);
    }
}
