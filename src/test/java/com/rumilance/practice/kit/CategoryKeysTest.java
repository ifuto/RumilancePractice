package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryKeysTest {

    @Test
    void mapsLegacyJapanesePresetKeys() {
        assertEquals("Armor", CategoryKeys.canonicalPreset("防具"));
        assertEquals("Gear", CategoryKeys.canonicalPreset("装備"));
        assertEquals("Potions", CategoryKeys.canonicalPreset("ポーション"));
        assertEquals("Consumables", CategoryKeys.canonicalPreset("消耗品"));
        assertEquals("Armor", CategoryKeys.canonicalPreset("Armor"));
    }

    @Test
    void mapsLegacyJapaneseEkitKeys() {
        assertEquals("Weapons/Armor", CategoryKeys.canonicalEkit("武器/防具"));
        assertEquals("Offhand", CategoryKeys.canonicalEkit("サブアイテム"));
        assertEquals("Blocks", CategoryKeys.canonicalEkit("ブロック"));
        assertEquals("Potions", CategoryKeys.canonicalEkit("ポーション"));
        assertTrue(CategoryKeys.isEkitWeapons("武器/防具"));
        assertTrue(CategoryKeys.isEkitPotion("ポーション"));
    }
}
