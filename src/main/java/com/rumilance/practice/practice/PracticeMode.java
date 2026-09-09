package com.rumilance.practice.practice;

import java.util.Locale;

/**
 * Quantum mech_train practice MODE (the map's {@code .mode mode} score): instead of one
 * aggregate fight per kit family, a room can run one concrete training drill — exactly the
 * disciplines the original datapack exposes (201..701).
 *
 * <p>{@link #NONE} keeps the current aggregate fight behaviour (a room with no mode assigned).
 */
public enum PracticeMode {
    /** No drill: aggregate fight (legacy behaviour). */
    NONE(PracticeType.SWORD, 0, "Auto (aggregate fight)"),

    // ---- crystal drills (201-203) ----
    CRYSTAL_DTAP(PracticeType.CRYSTAL, 201, "D-tap"),
    CRYSTAL_LEDGE(PracticeType.CRYSTAL, 202, "Ledge Dash"),
    CRYSTAL_HIT_ANCHOR(PracticeType.CRYSTAL, 203, "Hit-anchor"),

    // ---- mace drills (301-304) ----
    MACE_ELYTRA(PracticeType.MACE, 301, "Breach Swap (elytra slam)"),
    MACE_FAR_PEARL(PracticeType.MACE, 302, "Far Pearl"),
    MACE_STUN_SLAM(PracticeType.MACE, 303, "Stun Slam"),
    MACE_DIVEBOMB(PracticeType.MACE, 304, "Divebomb"),

    // ---- pot drills (401-403) ----
    POT_REPOT(PracticeType.NETHERITE_POT, 401, "Repot"),
    POT_REFILL_HOTBAR(PracticeType.NETHERITE_POT, 402, "Refill hotbar"),
    POT_REFILL_INVENTORY(PracticeType.NETHERITE_POT, 403, "Refill inventory"),

    // ---- cart power tiers (601-607) ----
    CART_M3(PracticeType.CART, 601, "Cart -3 (weakest)"),
    CART_M2(PracticeType.CART, 602, "Cart -2"),
    CART_M1(PracticeType.CART, 603, "Cart -1"),
    CART_0(PracticeType.CART, 604, "Cart 0 (default)"),
    CART_P1(PracticeType.CART, 605, "Cart +1"),
    CART_P2(PracticeType.CART, 606, "Cart +2"),
    CART_P3(PracticeType.CART, 607, "Cart +3 (hardest)"),

    // ---- generic (701) ----
    GENERIC_FLICK_AIM(PracticeType.SWORD, 701, "Flick aim");

    private final PracticeType family;
    private final int code;
    private final String label;

    PracticeMode(PracticeType family, int code, String label) {
        this.family = family;
        this.code = code;
        this.label = label;
    }

    public PracticeType family() {
        return family;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** True for cart power-tier drills; {@code tier()} returns -3..+3. */
    public boolean cartTierMode() {
        return this == CART_M3 || this == CART_M2 || this == CART_M1 || this == CART_0
                || this == CART_P1 || this == CART_P2 || this == CART_P3;
    }

    /** Cart strength tier: -3 (weakest) to +3 (hardest); 0 for non-cart modes. */
    public int tier() {
        return switch (this) {
            case CART_M3 -> -3;
            case CART_M2 -> -2;
            case CART_M1 -> -1;
            case CART_0 -> 0;
            case CART_P1 -> 1;
            case CART_P2 -> 2;
            case CART_P3 -> 3;
            default -> 0;
        };
    }

    public static PracticeMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("NONE".equals(norm) || "AUTO".equals(norm) || "DEFAULT".equals(norm)) {
            return NONE;
        }
        try {
            return valueOf(norm);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
