package com.rumilance.practice.team;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a party owner's saved original kit (paper slot in the original-kit GUI).
 * When a party battle starts with this reference, every fighter receives the owner's
 * original-kit layout as their loadout; all RULES (block place/break, pearls, timeouts,
 * max HP of the base kit) still come from the chosen match kit.
 */
public record OriginalKitRef(UUID owner, int slot) {

    public OriginalKitRef {
        Objects.requireNonNull(owner, "owner");
        if (slot < 0) {
            throw new IllegalArgumentException("slot");
        }
    }
}
