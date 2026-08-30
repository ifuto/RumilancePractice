package com.rumilance.practice.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDeepMergeTest {

    @Test
    void diskLeafOverridesJarButMissingLayoutsComeFromJar() {
        YamlConfiguration jar = new YamlConfiguration();
        jar.set("branding.server-name", "Jar Name");
        jar.set("layouts.lobby.lines", java.util.List.of("<aqua>Jar Lobby"));
        jar.set("layouts.lobby.title", "<bold>Jar");

        YamlConfiguration disk = new YamlConfiguration();
        disk.set("branding.server-name", "Custom Name");
        disk.set("layouts.lobby.title", "<bold>Custom");
        // intentionally omit layouts.lobby.lines

        YamlConfiguration merged = ConfigService.deepMerge(jar, disk);
        assertEquals("Custom Name", merged.getString("branding.server-name"));
        assertEquals("<bold>Custom", merged.getString("layouts.lobby.title"));
        assertEquals(java.util.List.of("<aqua>Jar Lobby"), merged.getStringList("layouts.lobby.lines"));
        assertTrue(merged.isSet("layouts.lobby.lines"));
    }

    @Test
    void emptyDiskKeepsFullJarTree() {
        YamlConfiguration jar = new YamlConfiguration();
        jar.set("layouts.match.lines", java.util.List.of("a", "b"));
        YamlConfiguration merged = ConfigService.deepMerge(jar, new YamlConfiguration());
        assertEquals(2, merged.getStringList("layouts.match.lines").size());
    }

    @Test
    void emptyLayoutListOnDiskDoesNotWipeJarLines() {
        YamlConfiguration jar = new YamlConfiguration();
        jar.set("layouts.lobby.lines", java.util.List.of("<aqua>Jar Lobby", "<white>Line2"));
        YamlConfiguration disk = new YamlConfiguration();
        disk.set("layouts.lobby.lines", java.util.List.of());
        YamlConfiguration merged = ConfigService.deepMerge(jar, disk);
        assertEquals(2, merged.getStringList("layouts.lobby.lines").size());
    }
}
