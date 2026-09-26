package com.rumilance.practice.ffa;

import com.rumilance.practice.ffa.FfaMannequinLoadout.Piece;
import com.rumilance.practice.ffa.FfaMannequinLoadout.Slot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the FFA mannequin spec ({@code /bot} inside FFA) as requested:
 * full unbreakable netherite, Protection 4 on every piece with the leggings as the ONLY
 * Blast Protection 4 piece, a totem of undying in each hand, and a bold aqua
 * {@code NARENA BOT} nametag.
 *
 * <p>The loadout is plain data on purpose — {@code ItemStack} would need a running server, so
 * without this seam the numbers could only be checked in game.</p>
 */
class FfaMannequinLoadoutTest {

    @Test
    void everyArmorSlotIsCoveredExactlyOnce() {
        List<Piece> armor = FfaMannequinLoadout.ARMOR;
        assertEquals(4, armor.size(), "full netherite = 4 pieces");
        for (Slot slot : Slot.values()) {
            assertEquals(1, armor.stream().filter(p -> p.slot() == slot).count(),
                    slot + " must be equipped exactly once");
            assertEquals(FfaMannequinLoadout.piece(slot).slot(), slot);
        }
    }

    @Test
    void everyPieceIsNetheriteLevelFourAndUnbreakable() {
        for (Piece piece : FfaMannequinLoadout.ARMOR) {
            assertTrue(piece.material().startsWith("NETHERITE_"),
                    piece.slot() + " is not netherite: " + piece.material());
            assertEquals(4, piece.level(), piece.slot() + " must be protection 4");
            assertEquals(FfaMannequinLoadout.ENCHANT_LEVEL, piece.level());
            assertTrue(piece.unbreakable(), piece.slot() + " must be unbreakable (耐久無限)");
        }
        assertEquals("NETHERITE_HELMET", FfaMannequinLoadout.piece(Slot.HELMET).material());
        assertEquals("NETHERITE_CHESTPLATE", FfaMannequinLoadout.piece(Slot.CHESTPLATE).material());
        assertEquals("NETHERITE_LEGGINGS", FfaMannequinLoadout.piece(Slot.LEGGINGS).material());
        assertEquals("NETHERITE_BOOTS", FfaMannequinLoadout.piece(Slot.BOOTS).material());
    }

    @Test
    void onlyTheLeggingsCarryBlastProtection() {
        assertEquals(FfaMannequinLoadout.BLAST_PROTECTION,
                FfaMannequinLoadout.piece(Slot.LEGGINGS).enchantment(),
                "レギンスだけ Blast Protection 4");
        for (Slot slot : List.of(Slot.HELMET, Slot.CHESTPLATE, Slot.BOOTS)) {
            assertEquals(FfaMannequinLoadout.PROTECTION,
                    FfaMannequinLoadout.piece(slot).enchantment(),
                    slot + " must be generic Protection 4 (全身 Protect 4)");
        }
        assertEquals(1, FfaMannequinLoadout.ARMOR.stream()
                .filter(p -> FfaMannequinLoadout.BLAST_PROTECTION.equals(p.enchantment()))
                .count(), "exactly one blast-protection piece");
    }

    @Test
    void bothHandsHoldATotem() {
        assertEquals("TOTEM_OF_UNDYING", FfaMannequinLoadout.HAND_MATERIAL);
    }

    @Test
    void nametagIsBoldAquaArenaBot() {
        assertEquals("NARENA BOT", FfaMannequinLoadout.NAMETAG);
        // §b = aqua, §l = bold, in that order, immediately before the text.
        assertEquals("\u00A7b\u00A7lNARENA BOT", FfaMannequinLoadout.NAMETAG_LEGACY);
        assertTrue(FfaMannequinLoadout.NAMETAG_LEGACY.endsWith(FfaMannequinLoadout.NAMETAG));
    }

    @Test
    void theDummyIsAVanillaHealthBody() {
        assertEquals(20.0d, FfaMannequinLoadout.MAX_HEALTH, "mannequins always run 20 HP");
        assertEquals(2, FfaMannequinLoadout.SPAWN_DISTANCE, "spawns two blocks in front");
        assertTrue(FfaMannequinLoadout.HEAL_DELAY_MS > 0);
        assertTrue(FfaMannequinLoadout.HEAL_PER_SECOND > 0);
    }

    @Test
    void unknownSlotIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FfaMannequinLoadout.piece(null));
    }
}
