package com.rumilance.practice.shieldweb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Registry persistence + cmd allocation for Shield Web. */
class ShieldRegistryTest {

    @TempDir
    Path dir;

    @Test
    void upsertNextCmdRemoveRoundTrip() throws IOException {
        Path file = dir.resolve("shields.json");
        ShieldRegistry registry = new ShieldRegistry(file);
        registry.load();

        assertEquals(100, registry.nextCmd(), "first allocation starts at 100");
        ShieldRegistry.ShieldEntry first = registry.upsert(100, "水色の盾");
        assertEquals(100, first.cmd());
        assertEquals("水色の盾", first.name());
        RegistryAssertions.assertCreatedSet(first);

        registry.upsert(101, "club");
        assertEquals(102, registry.nextCmd());
        registry.save();

        ShieldRegistry reloaded = new ShieldRegistry(file);
        reloaded.load();
        assertEquals(List.of(100, 101), reloaded.cmdList());
        assertEquals("水色の盾", reloaded.get(100).orElseThrow().name());
        assertEquals(first.createdEpochMillis(), reloaded.get(100).orElseThrow().createdEpochMillis(),
                "re-upload keeps the original creation stamp");

        // Re-upsert replaces the artwork label but not the identity.
        reloaded.upsert(101, "");
        assertEquals("club", reloaded.get(101).orElseThrow().name(),
                "blank name keeps the previous one");
        reloaded.upsert(101, "renamed");
        assertEquals("renamed", reloaded.get(101).orElseThrow().name());

        assertTrue(reloaded.remove(100));
        assertFalse(reloaded.remove(100));
        assertEquals(List.of(101), reloaded.cmdList());
    }

    @Test
    void corruptLinesNeverLoseTheWholeRegistry() throws IOException {
        Path file = dir.resolve("shields.json");
        Files.writeString(file,
                "{\"cmd\": 100, \"name\": \"ok\", \"created\": 1}\n"
                        + "not json at all\n"
                        + "{\"cmd\": \"woo\", \"name\": \"bad-int\"}\n"
                        + "{\"cmd\": 101, \"name\": \"also ok\", \"created\": 2}\n",
                StandardCharsets.UTF_8);
        ShieldRegistry registry = new ShieldRegistry(file);
        registry.load();
        assertEquals(List.of(100, 101), registry.cmdList());
        assertEquals("also ok", registry.get(101).orElseThrow().name());
    }

    @Test
    void loadOnMissingFileIsEmpty() throws IOException {
        ShieldRegistry registry = new ShieldRegistry(dir.resolve("nope.json"));
        registry.load();
        assertTrue(registry.list().isEmpty());
        registry.save(); // write-then-rename path creation
        assertTrue(Files.isRegularFile(dir.resolve("nope.json")));
    }

    /** Small holder so the record assertions read like prose. */
    private static final class RegistryAssertions {
        private RegistryAssertions() {
        }

        static void assertCreatedSet(ShieldRegistry.ShieldEntry entry) {
            assertTrue(entry.createdEpochMillis() > 0, "created epoch should be stamped");
        }
    }
}
