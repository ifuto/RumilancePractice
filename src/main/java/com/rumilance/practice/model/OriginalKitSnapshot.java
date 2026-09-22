package com.rumilance.practice.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A snapshot of an original kit assigned to one paper slot in the OriginalKitGUI.
 * The same player can own multiple original kits, one per {@code slot}.
 *
 * @param settingsJson serialised {@link OriginalKitSettings} (see {@code OriginalKitSettings.serialize()}),
 *                     or {@code null} when the kit was saved before settings existed (treated as defaults)
 */
public record OriginalKitSnapshot(UUID uuid, int slot, String itemDataBase64, String armorDataBase64,
                                  String settingsJson, Instant savedAt) {

    public OriginalKitSnapshot {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(itemDataBase64, "itemDataBase64");
        Objects.requireNonNull(savedAt, "savedAt");
    }

    public static OriginalKitSnapshot create(UUID uuid, int slot, String itemDataBase64, String armorDataBase64) {
        return new OriginalKitSnapshot(uuid, slot, itemDataBase64, armorDataBase64, null, Instant.now());
    }

    public OriginalKitSettings settings() {
        return OriginalKitSettings.parse(settingsJson);
    }

    public OriginalKitSnapshot withSettings(OriginalKitSettings settings) {
        return new OriginalKitSnapshot(uuid, slot, itemDataBase64, armorDataBase64,
                settings == null ? null : settings.serialize(), savedAt);
    }
}
