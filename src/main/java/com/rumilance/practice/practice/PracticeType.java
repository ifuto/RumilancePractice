package com.rumilance.practice.practice;

import java.util.Locale;

/**
 * Practice room mode. Spelling {@code ANKER} matches the admin command / config key.
 * {@code SWORD} = sparring bot that walks up and attacks; {@code CRYSTAL} = totem bot for
 * crystal-PvP combo practice (both inspired by Quantum's PvP Practice map).
 */
public enum PracticeType {
    ANKER,
    MACE,
    SWORD,
    CRYSTAL;

    public static PracticeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Practice type required");
        }
        return PracticeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
