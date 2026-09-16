package com.rumilance.practice.model;

import java.util.Map;
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
 *
 * <p>{@code enchantments} is the readable counterpart for hand-written kits: the opaque
 * base64 blob is fine for editors but useless in a YAML file, so {@code kits.yml} may also
 * declare {@code enchantments: {sharpness: 5, knockback: 1}} on an item. It is applied on top
 * of whatever the material (or {@code data:} blob) produced — unknown keys are ignored.</p>
 */
public record KitItemEntry(int slot, String material, int amount, String displayName,
                           String itemDataBase64, Map<String, Integer> enchantments) {

    public KitItemEntry {
        Objects.requireNonNull(material, "material");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative: " + slot);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be strictly positive: " + amount);
        }
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }

    public KitItemEntry(int slot, String material, int amount) {
        this(slot, material, amount, null, null, null);
    }

    public KitItemEntry(int slot, String material, int amount, String displayName) {
        this(slot, material, amount, displayName, null, null);
    }

    public KitItemEntry(int slot, String material, int amount, String displayName, String itemDataBase64) {
        this(slot, material, amount, displayName, itemDataBase64, null);
    }

    public boolean hasSerializedItem() {
        return itemDataBase64 != null && !itemDataBase64.isBlank();
    }

    public KitItemEntry withSlot(int newSlot) {
        return new KitItemEntry(newSlot, material, amount, displayName, itemDataBase64, enchantments);
    }
}
