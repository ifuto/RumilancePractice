package com.rumilance.practice.arena.fawe;

import com.rumilance.practice.util.AsyncExecutor;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real WorldEdit-API-backed implementation of {@link FaweBridge}. Works with either vanilla
 * WorldEdit or FastAsyncWorldEdit (which maintains API compatibility with WorldEdit and
 * transparently makes these operations fast/async under the hood).
 *
 * <p>All heavy lifting runs on the plugin's {@link AsyncExecutor} worker pool; WorldEdit/FAWE
 * are designed to have their edit operations driven from background threads.</p>
 */
public final class FaweBridgeImpl implements FaweBridge {

    private static final Logger LOGGER = Logger.getLogger(FaweBridgeImpl.class.getName());

    private final AsyncExecutor asyncExecutor;

    private FaweBridgeImpl(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        // Touch the API eagerly so a LinkageError surfaces immediately (during creation)
        // instead of lazily on the first arena operation.
        WorldEdit.getInstance();
    }

    /**
     * Detects whether WorldEdit/FastAsyncWorldEdit is installed and its API is actually
     * loadable, returning a working bridge if so, or a graceful {@link NoOpFaweBridge} otherwise.
     */
    public static FaweBridge createIfAvailable(Plugin plugin, AsyncExecutor asyncExecutor) {
        boolean present = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        if (!present) {
            return NoOpFaweBridge.INSTANCE;
        }
        try {
            return new FaweBridgeImpl(asyncExecutor);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "WorldEdit/FastAsyncWorldEdit was detected but the bridge "
                    + "failed to initialize; arena regeneration will be disabled.", e);
            return NoOpFaweBridge.INSTANCE;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> saveSchematic(org.bukkit.World bukkitWorld, int minX, int minY, int minZ,
                                                      int maxX, int maxY, int maxZ, Path outputFile) {
        return asyncExecutor.supplyAsync(() -> {
            try {
                World weWorld = BukkitAdapter.adapt(bukkitWorld);
                BlockVector3 min = BlockVector3.at(minX, minY, minZ);
                BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
                CuboidRegion region = new CuboidRegion(weWorld, min, max);

                BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                    ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
                    copy.setCopyingEntities(true);
                    Operations.complete(copy);
                }

                Files.createDirectories(outputFile.toAbsolutePath().getParent());
                ClipboardFormat format = ClipboardFormats.findByFile(outputFile.toFile());
                if (format == null) {
                    format = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC;
                }
                try (ClipboardWriter writer = format.getWriter(new FileOutputStream(outputFile.toFile()))) {
                    writer.write(clipboard);
                }
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save schematic to " + outputFile, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> regenerate(Path schematicFile, Location pasteAnchor) {
        return asyncExecutor.supplyAsync(() -> {
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFile.toFile());
                if (format == null) {
                    LOGGER.warning("Unrecognized schematic format for file: " + schematicFile);
                    return false;
                }

                org.bukkit.World bukkitWorld = pasteAnchor.getWorld();
                if (bukkitWorld == null) {
                    LOGGER.warning("Cannot regenerate arena: paste anchor has no associated world");
                    return false;
                }
                World weWorld = BukkitAdapter.adapt(bukkitWorld);

                try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile.toFile()))) {
                    Clipboard clipboard = reader.read();
                    try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                        Operation operation = new ClipboardHolder(clipboard)
                                .createPaste(editSession)
                                .to(BlockVector3.at(pasteAnchor.getBlockX(), pasteAnchor.getBlockY(), pasteAnchor.getBlockZ()))
                                .ignoreAirBlocks(false)
                                .build();
                        Operations.complete(operation);
                    }
                }
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to regenerate arena from schematic " + schematicFile, e);
                return false;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unexpected WorldEdit failure while regenerating from " + schematicFile, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> clearRegion(org.bukkit.World bukkitWorld, int minX, int minY, int minZ,
                                                  int maxX, int maxY, int maxZ) {
        return asyncExecutor.supplyAsync(() -> {
            try {
                World weWorld = BukkitAdapter.adapt(bukkitWorld);
                CuboidRegion region = new CuboidRegion(weWorld,
                        BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld).build()) {
                    editSession.setBlocks((com.sk89q.worldedit.regions.Region) region,
                            com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                }
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to clear region for disposable arena copy", e);
                return false;
            }
        });
    }
}
