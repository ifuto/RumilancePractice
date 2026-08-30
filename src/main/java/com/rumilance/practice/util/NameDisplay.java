package com.rumilance.practice.util;

/**
 * Shared display formatting for arena / FFA / kit-like ids: underscores become half-width spaces.
 * Delegates to {@link KitNames#pretty(String)} so {@code gui.kit-name-case} applies consistently.
 */
public final class NameDisplay {

    private NameDisplay() {
    }

    /** @return display form of an id ({@code _} → space + configured casing). */
    public static String pretty(String id) {
        return KitNames.pretty(id);
    }
}
