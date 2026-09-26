package com.rumilance.practice.ffa;

import java.util.List;

/**
 * The exact look of the FFA training mannequin ({@code /bot} inside FFA), as pure data.
 *
 * <p>Kept apart from {@link FfaMannequinService} on purpose: {@code ItemStack} / {@code Material}
 * need a running server, so the requested spec — full unbreakable netherite, Protection 4 on
 * every piece with <em>Blast</em> Protection 4 on the leggings only, a totem in each hand and a
 * bold aqua {@code NARENA BOT} nametag — would otherwise be untestable. The plain JUnit suite
 * pins the numbers here; the service only has to read them.</p>
 */
public final class FfaMannequinLoadout {

    /** Armor slot, in equip order. */
    public enum Slot { HELMET, CHESTPLATE, LEGGINGS, BOOTS }

    /**
     * One armor piece. {@code material} / {@code enchantment} are the Bukkit enum names so this
     * record stays server-free.
     */
    public record Piece(Slot slot, String material, String enchantment, int level, boolean unbreakable) {
    }

    /** Bukkit enchantment key: generic protection. */
    public static final String PROTECTION = "protection";
    /** Bukkit enchantment key: explosion protection (leggings only). */
    public static final String BLAST_PROTECTION = "blast_protection";

    /** Every piece is level 4 (the max useful level for both protection enchantments). */
    public static final int ENCHANT_LEVEL = 4;

    /**
     * Full netherite, all unbreakable (耐久無限): Protection 4 on helmet / chestplate / boots and
     * Blast Protection 4 on the leggings — the leggings are the ONLY blast-protection piece.
     */
    public static final List<Piece> ARMOR = List.of(
            new Piece(Slot.HELMET, "NETHERITE_HELMET", PROTECTION, ENCHANT_LEVEL, true),
            new Piece(Slot.CHESTPLATE, "NETHERITE_CHESTPLATE", PROTECTION, ENCHANT_LEVEL, true),
            new Piece(Slot.LEGGINGS, "NETHERITE_LEGGINGS", BLAST_PROTECTION, ENCHANT_LEVEL, true),
            new Piece(Slot.BOOTS, "NETHERITE_BOOTS", PROTECTION, ENCHANT_LEVEL, true)
    );

    /** Both hands hold one of these, and both are restocked after every vanilla pop. */
    public static final String HAND_MATERIAL = "TOTEM_OF_UNDYING";

    /** Nametag text, without any formatting. */
    public static final String NAMETAG = "NARENA BOT";

    /** Nametag as a legacy (§) string: bold aqua, exactly as requested. */
    public static final String NAMETAG_LEGACY = "\u00A7b\u00A7l" + NAMETAG;

    /** Vanilla mannequin health — the same 20 HP the AFK BOT Crystal uses. */
    public static final double MAX_HEALTH = 20.0d;

    /** How far in front of the player the mannequin appears, in blocks. */
    public static final int SPAWN_DISTANCE = 2;

    /** Seconds without damage before the slow heal kicks in (a popped dummy must recover). */
    public static final long HEAL_DELAY_MS = 3_000L;

    /** HP restored per second while out of damage, up to {@link #MAX_HEALTH}. */
    public static final double HEAL_PER_SECOND = 1.0d;

    /** The piece for one slot. */
    public static Piece piece(Slot slot) {
        for (Piece piece : ARMOR) {
            if (piece.slot() == slot) {
                return piece;
            }
        }
        throw new IllegalArgumentException("no mannequin piece for " + slot);
    }

    private FfaMannequinLoadout() {
    }
}
