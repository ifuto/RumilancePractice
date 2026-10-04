package com.rumilance.practice.kit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitVariantsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void variantKeyUsesKSpace() {
        // "#k" is a distinct key space from the Crystal FFA "#v" slots.
        assertEquals("nodebuff#k1", KitVariantsStore.variantKey("nodebuff", 1));
        assertEquals("nodebuff#k4", KitVariantsStore.variantKey("nodebuff", 4));
    }

    @Test
    void slotsClampIntoRange() {
        assertEquals(1, KitVariantsStore.clamp(0));
        assertEquals(1, KitVariantsStore.clamp(-5));
        assertEquals(3, KitVariantsStore.clamp(3));
        assertEquals(4, KitVariantsStore.clamp(9));
        assertEquals("nodebuff#k4", KitVariantsStore.variantKey("nodebuff", 99));
    }

    @Test
    void defaultSelectionIsK1() {
        KitVariantsStore store = new KitVariantsStore(tempDir.resolve("kit-variants.properties"));
        UUID player = UUID.randomUUID();
        assertEquals(1, store.selected(player, "nodebuff"));
        assertEquals(1, store.selected(null, "nodebuff"));
        assertEquals(1, store.selected(player, null));
    }

    @Test
    void selectPersistsAcrossInstances() throws Exception {
        Path file = tempDir.resolve("kit-variants.properties");
        KitVariantsStore store = new KitVariantsStore(file);
        UUID player = UUID.randomUUID();
        store.select(player, "nodebuff", 3);
        assertEquals(3, store.selected(player, "nodebuff"));
        // Other players / kits stay untouched at K1.
        assertEquals(1, store.selected(UUID.randomUUID(), "nodebuff"));
        assertEquals(1, store.selected(player, "sumo"));

        // A fresh instance (server restart) reads the same file.
        KitVariantsStore reloaded = new KitVariantsStore(file);
        assertEquals(3, reloaded.selected(player, "nodebuff"));
        assertEquals(1, reloaded.selected(player, "sumo"));
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void corruptFileFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("kit-variants.properties");
        Files.writeString(file, "this is not ?? a properties row\n###\n");
        KitVariantsStore store = new KitVariantsStore(file);
        assertEquals(1, store.selected(UUID.randomUUID(), "nodebuff"));
        // And the store still works (select overwrites the junk).
        UUID player = UUID.randomUUID();
        store.select(player, "nodebuff", 2);
        assertEquals(2, store.selected(player, "nodebuff"));
    }

    @Test
    void selectClampsOutOfRangeSlots() {
        Path file = tempDir.resolve("kit-variants.properties");
        KitVariantsStore store = new KitVariantsStore(file);
        UUID player = UUID.randomUUID();
        store.select(player, "nodebuff", 42);
        assertEquals(4, store.selected(player, "nodebuff"));
        store.select(player, "nodebuff", 0);
        assertEquals(1, store.selected(player, "nodebuff"));
    }
}
