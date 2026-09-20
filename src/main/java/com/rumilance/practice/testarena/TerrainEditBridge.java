package com.rumilance.practice.testarena;

import org.bukkit.World;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Optional accelerated editor for the temporary test arena.
 *
 * <p>The fallback generator keeps using small Bukkit batches. An implementation backed by FAWE
 * may build and paste a clipboard asynchronously because it does not call Bukkit block mutators
 * directly from the worker thread.</p>
 */
public interface TerrainEditBridge {

    boolean isAvailable();

    CompletableFuture<Boolean> paste(World world, int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ,
                                     SmoothTerrainGenerator.TerrainMap map,
                                     List<SmoothTerrainGenerator.ColumnData> columns);

    CompletableFuture<Boolean> clear(World world, int minX, int minY, int minZ,
                                     int maxX, int maxY, int maxZ);
}
