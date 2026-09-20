package com.rumilance.practice.arena.fawe;

import com.rumilance.practice.testarena.SmoothTerrainGenerator;
import com.rumilance.practice.testarena.TerrainEditBridge;
import com.rumilance.practice.util.AsyncExecutor;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FAWE-backed writer for the temporary 100 by 100 test arena.
 *
 * <p>The height map is still planned by {@link SmoothTerrainGenerator} first. This bridge then
 * creates a complete clipboard off the server thread and submits one FAWE paste instead of
 * issuing 10,000 Bukkit block writes over hundreds of ticks. The normal Bukkit batch writer
 * remains the fallback when FAWE is absent.</p>
 */
public final class FaweTerrainBridge implements TerrainEditBridge {

    private static final Logger LOGGER = Logger.getLogger(FaweTerrainBridge.class.getName());
    private final AsyncExecutor asyncExecutor;

    private FaweTerrainBridge(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
        WorldEdit.getInstance();
    }

    /** Uses FAWE only; plain WorldEdit keeps the safe Bukkit-batch fallback. */
    public static TerrainEditBridge createIfAvailable(Plugin plugin, AsyncExecutor asyncExecutor) {
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return null;
        }
        try {
            return new FaweTerrainBridge(asyncExecutor);
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "FastAsyncWorldEdit was detected but the test-arena bridge failed to initialize; "
                            + "using the low-lag Bukkit batch writer.", e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> paste(World world, int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ,
                                             SmoothTerrainGenerator.TerrainSettings settings,
                                             List<SmoothTerrainGenerator.ColumnData> columns) {
        return asyncExecutor.supplyAsync(() -> {
            try {
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                BlockVector3 min = BlockVector3.at(minX, minY, minZ);
                BlockVector3 max = BlockVector3.at(maxX, maxY, maxZ);
                CuboidRegion region = new CuboidRegion(weWorld, min, max);
                BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
                BlockState air = BlockTypes.AIR.getDefaultState();
                BlockState bedrock = BlockTypes.BEDROCK.getDefaultState();

                // Fill the whole edit volume, including air above the terrain. The bottom is a
                // flat bedrock plane; every cavity between that plane and the surface is filled
                // with the selected palette, so no floating islands or hollow columns remain.
                for (SmoothTerrainGenerator.ColumnData column : columns) {
                    for (int y = minY; y <= maxY; y++) {
                        BlockState state;
                        if (y == minY) {
                            state = bedrock;
                        } else if (y > column.topY()) {
                            state = air;
                        } else {
                            int layer = column.topY() - y + 1;
                            state = stateFor(settings.map(), layer);
                        }
                        clipboard.setBlock(BlockVector3.at(column.x(), y, column.z()), state);
                    }
                }

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder().world(weWorld).build()) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(min)
                            .ignoreAirBlocks(false)
                            .build();
                    Operations.complete(operation);
                }
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to paste the FAWE test arena", e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> clear(World world, int minX, int minY, int minZ,
                                             int maxX, int maxY, int maxZ) {
        return asyncExecutor.supplyAsync(() -> {
            try {
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
                CuboidRegion region = new CuboidRegion(weWorld,
                        BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder().world(weWorld).build()) {
                    editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
                }
                return true;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to clear the FAWE test arena", e);
                return false;
            }
        });
    }

    private static BlockState stateFor(SmoothTerrainGenerator.TerrainMap map, int layer) {
        return switch (map) {
            case GRASS_STONE -> layer == 1
                    ? BlockTypes.GRASS_BLOCK.getDefaultState()
                    : layer <= 3 ? BlockTypes.DIRT.getDefaultState() : BlockTypes.STONE.getDefaultState();
            case SAND_SANDSTONE -> layer <= 4
                    ? BlockTypes.SAND.getDefaultState() : BlockTypes.SANDSTONE.getDefaultState();
            case RED_SAND_RED_SANDSTONE -> layer <= 3
                    ? BlockTypes.RED_SAND.getDefaultState() : BlockTypes.RED_SANDSTONE.getDefaultState();
        };
    }
}
