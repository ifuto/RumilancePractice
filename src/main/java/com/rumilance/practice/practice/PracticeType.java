package com.rumilance.practice.practice;

import java.util.Locale;

/**
 * Practice room mode. Spelling {@code ANKER} matches the admin command / config key.
 */
public enum PracticeType {
    ANKER,
    MACE;

    public static PracticeType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Practice type required");
        }
        return PracticeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
