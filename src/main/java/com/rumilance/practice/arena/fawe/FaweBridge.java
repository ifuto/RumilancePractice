package com.rumilance.practice.arena.fawe;

import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over FastAsyncWorldEdit/WorldEdit used to snapshot and regenerate arenas between
 * matches. A no-op implementation is used automatically when neither plugin is installed so the
 * rest of the plugin can call this interface unconditionally.
 */
public interface FaweBridge {

    /**
     * @return {@code true} if a working WorldEdit (or FastAsyncWorldEdit) API was detected and
     * successfully initialized.
     */
    boolean isAvailable();

    /**
     * Copies the given world region into a schematic file on disk, asynchronously.
     *
     * @return a future completing with {@code true} on success, {@code false} on failure
     * (never completes exceptionally; failures are logged and reflected in the return value).
     */
    CompletableFuture<Boolean> saveSchematic(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Path outputFile);

    /**
     * Pastes a previously saved schematic back into the world, anchored at {@code pasteAnchor}'s
     * block position, asynchronously. Used to reset an arena to its pristine state after a match.
     *
     * @return a future completing with {@code true} on success, {@code false} on failure.
     */
    CompletableFuture<Boolean> regenerate(Path schematicFile, Location pasteAnchor);
}
