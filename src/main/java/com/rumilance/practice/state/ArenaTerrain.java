package com.rumilance.practice.state;

/**
 * Cosmetic/gameplay terrain classification of an arena template, independent of
 * {@link ArenaType} (which distinguishes duel vs. FFA arenas). Used by kits to declare a
 * preferred map style and by the duel request GUI's map selector. {@code ANY} matches every
 * terrain and is the default for both kits and arena templates that do not care.
 */
public enum ArenaTerrain {
    FLAT,
    BUMPY,
    CRYSTAL,
    NETHERITE,
    ANY;

    public boolean matches(ArenaTerrain other) {
        return this == ANY || other == ANY || this == other;
    }
}
