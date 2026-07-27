package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A snapshot of a player's original (non-kit) inventory and armor, saved before they enter
 * practice mode so it can be restored later via {@code /originalkit load}.
 */
public record OriginalKitSnapshot(UUID uuid, String itemDataBase64, String armorDataBase64, Instant savedAt) {

    public OriginalKitSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(itemDataBase64, "itemDataBase64");
        Objects.requireNonNull(savedAt, "savedAt");
    }

    public static OriginalKitSnapshot create(UUID uuid, String itemDataBase64, String armorDataBase64) {
        return new OriginalKitSnapshot(uuid, itemDataBase64, armorDataBase64, Instant.now());
    }
}
