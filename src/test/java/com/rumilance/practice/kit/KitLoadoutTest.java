package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLoadoutTest {

    @Test
    void chestSlotRejectsPotionsAndPanes() {
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "SPLASH_POTION"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "POTION"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "LINGERING_POTION"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "CYAN_STAINED_GLASS_PANE"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "DIAMOND_SWORD"));
    }

    @Test
    void armorSlotsAcceptMatchingPieces() {
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.HELMET, "DIAMOND_HELMET"));
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.HELMET, "CARVED_PUMPKIN"));
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "NETHERITE_CHESTPLATE"));
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "ELYTRA"));
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.LEGS, "IRON_LEGGINGS"));
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.BOOTS, "LEATHER_BOOTS"));
    }

    @Test
    void armorSlotsRejectCrossSlotPieces() {
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "DIAMOND_HELMET"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.HELMET, "DIAMOND_BOOTS"));
        assertFalse(KitLoadout.armorSlotAccepts(KitLoadout.LEGS, "ELYTRA"));
        assertFalse(KitLoadout.armorSlotAccepts(0, "DIAMOND_CHESTPLATE"));
    }

    /**
     * Documents the KitLoadout index contract vs Bukkit {@code inventory.setItem(36-39)}:
     * loadout 36=helmet / 37=chest / 38=legs / 39=boots, while Bukkit raw 36=boots … 39=helmet.
     * {@link KitLoadout#give} must use setHelmet/setChestplate/… so potions never wear as armor.
     */
    @Test
    void loadoutArmorIndicesDifferFromBukkitRawSlots() {
        assertEquals(36, KitLoadout.HELMET);
        assertEquals(37, KitLoadout.CHEST);
        assertEquals(38, KitLoadout.LEGS);
        assertEquals(39, KitLoadout.BOOTS);
        // Potions must never be accepted into any armor loadout index.
        for (int slot : new int[]{KitLoadout.HELMET, KitLoadout.CHEST, KitLoadout.LEGS, KitLoadout.BOOTS}) {
            assertFalse(KitLoadout.armorSlotAccepts(slot, "SPLASH_POTION"));
            assertFalse(KitLoadout.armorSlotAccepts(slot, "POTION"));
            assertFalse(KitLoadout.armorSlotAccepts(slot, "LINGERING_POTION"));
        }
        assertTrue(KitLoadout.armorSlotAccepts(KitLoadout.CHEST, "DIAMOND_CHESTPLATE"));
    }
}
