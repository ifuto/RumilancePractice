package com.rumilance.practice;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * {@code plugin.yml} name is {@link #NAME}. YAML and other data stay under
 * {@code plugins/{@link #DATA_FOLDER_NAME}} because Paper's {@code JavaPlugin#getDataFolder()}
 * is final and would otherwise resolve to {@code plugins/NARENA}.
 * Item PDC stays on {@link #PDC_NAMESPACE} so existing kits keep their tags after the rename.
 */
public final class PluginIdentity {

    public static final String NAME = "NARENA";
    /** Current operator data root: {@code plugins/n-arena}. */
    public static final String DATA_FOLDER_NAME = "n-arena";
    /** Previous data root; migrated automatically once on startup if the new folder is absent. */
    public static final String LEGACY_DATA_FOLDER_NAME = "RumilancePractice";
    public static final String PDC_NAMESPACE = "rumilancepractice";

    private PluginIdentity() {
    }

    public static Plugin plugin() {
        Plugin named = Bukkit.getPluginManager().getPlugin(NAME);
        if (named != null) {
            return named;
        }
        return Bukkit.getPluginManager().getPlugin(LEGACY_DATA_FOLDER_NAME);
    }

    /** Operator data root: always {@code plugins/n-arena}, even when the plugin name is NARENA. */
    public static File dataFolder(Plugin plugin) {
        File bukkitFolder = plugin.getDataFolder();
        File parent = bukkitFolder.getParentFile();
        if (parent == null) {
            return bukkitFolder;
        }
        return new File(parent, DATA_FOLDER_NAME);
    }

    /**
     * One-shot migration of operator data from the legacy {@code plugins/RumilancePractice}
     * folder into {@code plugins/n-arena}. Only runs when the new folder is missing and the
     * legacy one holds files, so existing servers keep their YAML / database / schematics.
     * Safe to call on every enable.
     */
    public static void migrateLegacyDataIfNeeded(Plugin plugin) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null) {
            return;
        }
        File target = new File(pluginsDir, DATA_FOLDER_NAME);
        File legacy = new File(pluginsDir, LEGACY_DATA_FOLDER_NAME);
        if (target.exists() || !legacy.isDirectory()) {
            return;
        }
        // Only migrate when the legacy folder actually contains something.
        String[] children = legacy.list();
        if (children == null || children.length == 0) {
            return;
        }
        try {
            copyDirectory(legacy.toPath(), target.toPath());
            plugin.getLogger().info("Migrated operator data from plugins/" + LEGACY_DATA_FOLDER_NAME
                    + " to plugins/" + DATA_FOLDER_NAME + " (" + children.length + " top-level entries).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to migrate legacy data from plugins/" + LEGACY_DATA_FOLDER_NAME
                            + " to plugins/" + DATA_FOLDER_NAME + " — starting with an empty data folder.", e);
        }
    }

    private static void copyDirectory(Path source, Path destination) throws java.io.IOException {
        Files.createDirectories(destination);
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(src -> {
                try {
                    Path dst = destination.resolve(source.relativize(src).toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (Exception copyError) {
                    throw new RuntimeException(copyError);
                }
            });
        } catch (RuntimeException wrapped) {
            if (wrapped.getCause() instanceof java.io.IOException io) {
                throw io;
            }
            throw wrapped;
        }
    }
}
