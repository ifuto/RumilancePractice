package com.rumilance.practice.model;

import java.util.Locale;

/**
 * Which section of the kit pickers a kit belongs to. <b>Main Kits</b> render under the
 * nature-decorated azalea header (the flagship line-up), <b>Sub Kits</b> under the
 * bolted-iron-trapdoor header (situational / fun picks). Kits default to MAIN so existing
 * configurations keep their place.
 */
public enum KitCategory {
    MAIN,
    SUB;

    /** Safe parse for YAML values; anything unknown (or blank) falls back to MAIN. */
    public static KitCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MAIN;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MAIN;
        }
    }
}
