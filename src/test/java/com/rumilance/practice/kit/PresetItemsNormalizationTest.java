package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Old preset files merged weapons and armor into one combined category
 * ({@code 武器/防具} / {@code Weapons/Armor}); {@link CategoryKeys#canonicalPreset} leaves such
 * keys orphaned, which made the Armor tab render empty. The loader must pull those pools back
 * into Armor/Gear.
 */
class PresetItemsNormalizationTest {

    private static Map<Integer, String> pool(String... values) {
        Map<Integer, String> map = new TreeMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(i, values[i]);
        }
        return map;
    }

    @Test
    void splitsLegacyCombinedPoolIntoArmorAndGear() {
        Map<String, Map<Integer, String>> pools = new HashMap<>();
        pools.put("武器/防具", pool("IRON_SWORD", "IRON_HELMET", "DIAMOND_CHESTPLATE", "BOW"));

        boolean changed = PresetItems.normalizeCombinedPools(pools);

        assertTrue(changed);
        assertFalse(pools.containsKey("武器/防具"));
        // Slot numbers are preserved; armor pieces split off into the Armor pool.
        Map<Integer, String> armor = pools.get("Armor");
        assertEquals(2, armor.size());
        assertEquals("IRON_HELMET", armor.get(1));
        assertEquals("DIAMOND_CHESTPLATE", armor.get(2));
        Map<Integer, String> gear = pools.get("Gear");
        assertEquals("IRON_SWORD", gear.get(0));
        assertEquals("BOW", gear.get(3));
    }

    @Test
    void englishCombinedKeyIsHandledToo() {
        Map<String, Map<Integer, String>> pools = new HashMap<>();
        pools.put("Weapons/Armor", pool("NETHERITE_BOOTS"));

        assertTrue(PresetItems.normalizeCombinedPools(pools));
        assertEquals(pool("NETHERITE_BOOTS"), pools.get("Armor"));
        assertTrue(pools.get("Gear").isEmpty());
    }

    @Test
    void existingArmorPoolIsNotOverwritten() {
        Map<String, Map<Integer, String>> pools = new HashMap<>();
        pools.put("Armor", pool("LEATHER_HELMET"));
        pools.put("武器/防具", pool("IRON_HELMET", "IRON_SWORD"));

        assertTrue(PresetItems.normalizeCombinedPools(pools));
        // Armor already had content: orphan armor pieces stay in Gear.
        assertEquals(pool("LEATHER_HELMET"), pools.get("Armor"));
        Map<Integer, String> gear = pools.get("Gear");
        assertEquals("IRON_HELMET", gear.get(0));
        assertEquals("IRON_SWORD", gear.get(1));
    }

    @Test
    void canonicalOnlyPoolsAreUntouched() {
        Map<String, Map<Integer, String>> pools = new HashMap<>();
        pools.put("Armor", pool("IRON_HELMET"));
        pools.put("Gear", pool("IRON_SWORD"));
        pools.put("Potions", pool("speed"));
        pools.put("Consumables", pool("GOLDEN_APPLE"));

        assertFalse(PresetItems.normalizeCombinedPools(pools));
    }

    @Test
    void detectsArmorEntries() {
        assertTrue(PresetItems.isArmorEntry("IRON_HELMET"));
        assertTrue(PresetItems.isArmorEntry("diamond_chestplate"));
        assertTrue(PresetItems.isArmorEntry("NETHERITE_LEGGINGS"));
        assertTrue(PresetItems.isArmorEntry("GOLDEN_BOOTS"));
        assertTrue(PresetItems.isArmorEntry("TURTLE_HELMET"));
        assertFalse(PresetItems.isArmorEntry("IRON_SWORD"));
        assertFalse(PresetItems.isArmorEntry("SHIELD"));
        assertFalse(PresetItems.isArmorEntry(""));
        assertFalse(PresetItems.isArmorEntry(null));
        // Corrupt NBT payloads must not throw — they simply are not armor.
        assertFalse(PresetItems.isArmorEntry("data:not-valid-item-bytes"));
    }
}
