package com.rumilance.practice.team;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference to a party owner's saved original kit (paper slot in the original-kit GUI).
 * When a party battle starts with this reference, the loadout AND every rule come from
 * that slot alone: the owner's original-kit settings are synthesized into the session's
 * rules kit, so no shared match kit is used at all.
 */
public record OriginalKitRef(UUID owner, int slot) {

    public OriginalKitRef {
        Objects.requireNonNull(owner, "owner");
        if (slot < 0) {
            throw new IllegalArgumentException("slot");
        }
    }
}
