package com.rumilance.practice.util;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serializes {@link ItemStack} arrays (e.g. full player inventories/kit loadouts) to a
 * compact binary form, and back, for persistence in the database.
 *
 * <p>Uses the modern {@code ItemStack#serializeAsBytes()} / {@code ItemStack.deserializeBytes(byte[])}
 * API (backed by Minecraft's NBT/DataComponent format) rather than legacy Java object
 * serialization ({@code BukkitObjectOutputStream}). This is smaller, faster, does not leak
 * internal class structure, and stays forward compatible across Minecraft versions.</p>
 */
public final class ItemSerializer {

    private ItemSerializer() {
    }

    /**
     * Packs an {@link ItemStack} array to a compact binary form. The serialized frame is
     * always GZIP-compressed and prefixed with a magic byte so legacy (uncompressed) rows in
     * the database still decode. NBT for a full inventory is highly redundant (counts, ids,
     * empty-slot markers, repeated enchantment keys), so deflate routinely shrinks a saved
     * original-kit row to 10-20% of its raw size. Kept in the array codec itself so every
     * persisted store (original kits, lobby inventory, practice blocks, layout deltas) gets
     * the saving without any per-caller change.
     */
    static final byte[] SERIALIZE_MAGIC = new byte[]{(byte) 0x52, (byte) 0x50, (byte) 0x47}; // "RPG"

    /** Magic-prefixed GZIP wrapper, split out so the codec is unit-testable without Bukkit. */
    static byte[] pack(byte[] frame) {
        try {
            ByteArrayOutputStream packed = new ByteArrayOutputStream(frame.length + 16);
            packed.write(SERIALIZE_MAGIC);
            try (GZIPOutputStream gz = new GZIPOutputStream(packed)) {
                gz.write(frame);
            }
            return packed.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to gzip ItemStack frame", e);
        }
    }

    /** Inflates a {@link #pack}ed frame; uncompressed (legacy) data passes through untouched. */
    static byte[] unpack(byte[] data) {
        if (data != null && data.length > SERIALIZE_MAGIC.length
                && data[0] == SERIALIZE_MAGIC[0]
                && data[1] == SERIALIZE_MAGIC[1]
                && data[2] == SERIALIZE_MAGIC[2]) {
            try {
                ByteArrayOutputStream inflated = new ByteArrayOutputStream(Math.max(64, data.length * 2));
                try (GZIPInputStream gz = new GZIPInputStream(
                        new ByteArrayInputStream(data, SERIALIZE_MAGIC.length, data.length - SERIALIZE_MAGIC.length))) {
                    gz.transferTo(inflated);
                }
                return inflated.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to gunzip ItemStack frame", e);
            }
        }
        return data;
    }

    public static byte[] serialize(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    out.writeInt(-1);
                    continue;
                }
                byte[] itemBytes = item.serializeAsBytes();
                out.writeInt(itemBytes.length);
                out.write(itemBytes);
            }
            out.flush();
            return pack(bytes.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize ItemStack array", e);
        }
    }

    public static ItemStack[] deserialize(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(unpack(data)))) {
            int length = in.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int itemLength = in.readInt();
                if (itemLength < 0) {
                    items[i] = null;
                    continue;
                }
                byte[] itemBytes = new byte[itemLength];
                in.readFully(itemBytes);
                items[i] = ItemStack.deserializeBytes(itemBytes);
            }
            return items;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize ItemStack array", e);
        }
    }

    public static String toBase64(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(serialize(items));
    }

    public static ItemStack[] fromBase64(String encoded) {
        return deserialize(Base64.getDecoder().decode(encoded));
    }

    public static byte[] serializeSingle(ItemStack item) {
        return item == null ? new byte[0] : item.serializeAsBytes();
    }

    public static ItemStack deserializeSingle(byte[] data) {
        return data == null || data.length == 0 ? null : ItemStack.deserializeBytes(data);
    }

    /** Base64 form of a single item (full NBT: enchantments, potion effects, names, ...). */
    public static String singleToBase64(ItemStack item) {
        byte[] bytes = serializeSingle(item);
        return bytes.length == 0 ? null : Base64.getEncoder().encodeToString(bytes);
    }

    /** @return the item encoded by {@link #singleToBase64}, or null when absent/corrupt. */
    public static ItemStack singleFromBase64(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return deserializeSingle(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
