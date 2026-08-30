package com.rumilance.practice.arena.fawe;

import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Graceful fallback used when neither FastAsyncWorldEdit nor WorldEdit is installed (or their
 * API failed to load). Arena regeneration features are simply disabled; nothing throws.
 */
public final class NoOpFaweBridge implements FaweBridge {

    public static final NoOpFaweBridge INSTANCE = new NoOpFaweBridge();

    private NoOpFaweBridge() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> saveSchematic(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Path outputFile) {
        Logger.getLogger(NoOpFaweBridge.class.getName())
                .warning("Cannot save schematic '" + outputFile + "': FastAsyncWorldEdit/WorldEdit is not available.");
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> regenerate(Path schematicFile, Location pasteAnchor) {
        Logger.getLogger(NoOpFaweBridge.class.getName())
                .warning("Cannot regenerate from '" + schematicFile + "': FastAsyncWorldEdit/WorldEdit is not available.");
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> clearRegion(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Logger.getLogger(NoOpFaweBridge.class.getName())
                .warning("Cannot clear region: FastAsyncWorldEdit/WorldEdit is not available.");
        return CompletableFuture.completedFuture(false);
    }
}
