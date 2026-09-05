package com.rumilance.practice.cosmetic.namecolor;

import java.util.Locale;
import java.util.Objects;

/**
 * A VIP+ player's custom name-color choice.
 *
 * @param mode          {@code none} (vanilla), {@code solid} single color or
 *                      {@code gradient} between two colors
 * @param primaryHex    {@code RRGGBB} hex for solid mode / gradient start ("" when unset)
 * @param secondaryHex  {@code RRGGBB} hex for gradient end ("" when unset)
 * @param changedAtMillis epoch millis of the last saved change (3-day cooldown basis; 0 = never)
 */
public record NameColorSelection(Mode mode, String primaryHex, String secondaryHex,
                                 long changedAtMillis) {

    public enum Mode {
        NONE, SOLID, GRADIENT;

        public static Mode parse(String raw) {
            if (raw == null) {
                return NONE;
            }
            try {
                return Mode.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    public static final NameColorSelection DEFAULT = new NameColorSelection(Mode.NONE, "", "", 0L);

    public NameColorSelection {
        Objects.requireNonNull(mode, "mode");
        primaryHex = normalizeHex(primaryHex);
        secondaryHex = normalizeHex(secondaryHex);
    }

    /** Keeps only valid 6-digit hex (case-insensitive, leading # tolerated); else "". */
    public static String normalizeHex(String hex) {
        if (hex == null) {
            return "";
        }
        String trimmed = hex.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.length() != 6) {
            return "";
        }
        for (char c : trimmed.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return "";
            }
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /** True when this selection actually paints the name (valid colors for its mode). */
    public boolean active() {
        return switch (mode) {
            case NONE -> false;
            case SOLID -> !primaryHex.isEmpty();
            case GRADIENT -> !primaryHex.isEmpty() && !secondaryHex.isEmpty();
        };
    }

    public NameColorSelection withMode(Mode newMode) {
        return new NameColorSelection(newMode, primaryHex, secondaryHex, changedAtMillis);
    }

    public NameColorSelection withPrimary(String hex) {
        return new NameColorSelection(mode, hex, secondaryHex, changedAtMillis);
    }

    public NameColorSelection withSecondary(String hex) {
        return new NameColorSelection(mode, primaryHex, hex, changedAtMillis);
    }

    public NameColorSelection withChangedAt(long millis) {
        return new NameColorSelection(mode, primaryHex, secondaryHex, millis);
    }
}
