package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the GZIP wrapper used by {@link ItemSerializer} without touching Bukkit's
 * ItemStack internals. The frame codec is pure bytes: pack / unpack must round-trip and
 * legacy (uncompressed) payloads must pass through unchanged.
 */
class ItemSerializerPackTest {

    @Test
    void packThenUnpackRoundTrips() {
        byte[] frame = "count=41;slot0=diamond_sword;slot5=ender_pearl".getBytes(StandardCharsets.UTF_8);
        byte[] packed = ItemSerializer.pack(frame);
        assertArrayEquals(ItemSerializer.SERIALIZE_MAGIC, Arrays.copyOf(packed, 3));
        assertArrayEquals(frame, ItemSerializer.unpack(packed));
    }

    @Test
    void unpackIsTransparentForLegacyUncompressedData() {
        byte[] legacy = new byte[]{0, 0, 0, 3, 0, 0, 0, 0};
        assertArrayEquals(legacy, ItemSerializer.unpack(legacy));
    }

    @Test
    void gzipShrinksHighlyRedundantFrames() {
        byte[] frame = new byte[8192];
        Arrays.fill(frame, (byte) 0x00); // 8 KB of zeros = maximally compressible
        byte[] packed = ItemSerializer.pack(frame);
        assertTrue(packed.length < frame.length / 4,
                "expected heavy deflate, packed=" + packed.length + " vs " + frame.length);
        assertArrayEquals(frame, ItemSerializer.unpack(packed));
    }
}
