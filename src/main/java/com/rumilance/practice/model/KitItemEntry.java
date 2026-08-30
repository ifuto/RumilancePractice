package com.rumilance.practice.model;

import java.util.Objects;

/**
 * A single configured item within a {@link KitDefinition}'s loadout, referenced by
 * Bukkit {@code Material} name (kept as a plain string here to avoid a hard Bukkit
 * dependency in the pure data model).
 *
 * <p>{@code itemDataBase64}, when present, is a full {@code ItemSerializer}-encoded
 * {@code ItemStack} (preserving enchantments, custom names, lore, etc.) as produced by the
 * {@code /ekit} official kit layout editor. Simple kits declared directly in {@code kits.yml}
 * leave it {@code null} and are reconstructed from {@link #material()}/{@link #amount()}/
 * {@link #displayName()} alone.</p>
 */
public record KitItemEntry(int slot, String material, int amount, String displayName, String itemDataBase64) {

    public KitItemEntry {
        Objects.requireNonNull(material, "material");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative: " + slot);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive: " + amount);
        }
    }

    public KitItemEntry(int slot, String material, int amount) {
        this(slot, material, amount, null, null);
    }

    public KitItemEntry(int slot, String material, int amount, String displayName) {
        this(slot, material, amount, displayName, null);
    }

    public boolean hasSerializedItem() {
        return itemDataBase64 != null && !itemDataBase64.isBlank();
    }

    public KitItemEntry withSlot(int newSlot) {
        return new KitItemEntry(newSlot, material, amount, displayName, itemDataBase64);
    }
}
