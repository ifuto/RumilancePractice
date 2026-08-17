package com.rumilance.practice.util;

import java.util.Locale;

/**
 * Formats kit display names: underscores become spaces and an optional case style is applied
 * ({@code gui.kit-name-case} in config.yml, default {@code KEEP}). With KEEP, the casing typed
 * in {@code /kit create NoDebuff_Pro} is shown verbatim as {@code "NoDebuff Pro"}; TITLE would
 * render {@code "Nodebuff Pro"}, UPPER {@code "NODEBUFF PRO"}, LOWER {@code "nodebuff pro"}.
 */
public final class KitNames {

    /** How kit names are cased after underscore substitution. */
    public enum CaseStyle {
        /** Capitalise the first letter of every word ("No Debuff"). */
        TITLE,
        /** Everything upper-case ("NO DEBUFF"). */
        UPPER,
        /** Everything lower-case ("no debuff"). */
        LOWER,
        /** Keep the id's original casing, only replace underscores. */
        KEEP;

        public static CaseStyle parse(String raw) {
            if (raw == null) {
                return KEEP;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return KEEP;
            }
        }
    }

    private static volatile CaseStyle style = CaseStyle.KEEP;

    private KitNames() {
    }

    /** Sets the global case style (called once at bootstrap from config.yml). */
    public static void configure(CaseStyle newStyle) {
        style = newStyle == null ? CaseStyle.KEEP : newStyle;
    }

    /** @return the display form of a kit id: underscores to spaces + configured casing. */
    public static String pretty(String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return "";
        }
        String spaced = kitId.replace('_', ' ').trim();
        return switch (style) {
            case UPPER -> spaced.toUpperCase(Locale.ROOT);
            case LOWER -> spaced.toLowerCase(Locale.ROOT);
            case KEEP -> spaced;
            case TITLE -> titleCase(spaced);
        };
    }

    private static String titleCase(String input) {
        StringBuilder out = new StringBuilder(input.length());
        boolean atWordStart = true;
        for (char c : input.toCharArray()) {
            if (c == ' ') {
                atWordStart = true;
                out.append(c);
            } else if (atWordStart) {
                out.append(Character.toUpperCase(c));
                atWordStart = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}
