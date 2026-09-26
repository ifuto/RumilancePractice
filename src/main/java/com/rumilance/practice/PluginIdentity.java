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
     * Operator-facing file inside {@link #dataFolder} ({@code plugins/n-arena}), with a one-time
     * carry-over of a same-named file that an older build wrote into Paper's own
     * {@code plugins/NARENA} folder.
     *
     * <p>Use this for every YAML the plugin owns instead of {@code new File(plugin.getDataFolder(),
     * name)}: the latter silently splits operator data across two folders, which is how
     * {@code tiers.yml}, {@code crystal-ffa.yml} and the two {@code afk-crystal-*.yml} files ended
     * up in {@code plugins/NARENA} while the documentation (and every backup script) points at
     * {@code plugins/n-arena}.</p>
     *
     * <p>The carry-over copies and never deletes, and only runs while the destination is missing,
     * so an existing server keeps its data through the update and a second boot is a no-op.
     * Deliberately NOT applied to the Quantum side ({@code quantum.yml}, {@code quantum/}) or to
     * {@code resource-pack.json}: those genuinely live in {@code plugins/NARENA} and are quoted
     * that way by {@code tools/parity-runner/*} and by config.yml's own comments.</p>
     */
    public static File dataFile(Plugin plugin, String fileName) {
        File folder = dataFolder(plugin);
        File target = new File(folder, fileName);
        if (target.exists()) {
            return target;
        }
        File stray = new File(plugin.getDataFolder(), fileName);
        if (!stray.isFile()) {
            return target;
        }
        try {
            Files.createDirectories(folder.toPath());
            Files.copy(stray.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            Plugin named = plugin();
            java.util.logging.Logger logger = named == null
                    ? java.util.logging.Logger.getLogger(NAME)
                    : named.getLogger();
            logger.info("Carried " + fileName + " over from plugins/" + NAME + " to plugins/"
                    + DATA_FOLDER_NAME + " — the copy under plugins/" + NAME
                    + " is no longer used and can be deleted.");
        } catch (Exception e) {
            // Falling back to the stray copy beats starting with empty data.
            return stray;
        }
        return target;
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
