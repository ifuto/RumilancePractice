package com.rumilance.practice.testarena;

import com.rumilance.practice.PluginIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Low-lag, plugin-side builder for the temporary {@code /testarena} maps.
 *
 * <p>Height calculation and column planning are done asynchronously. Without FAWE, Bukkit world
 * access is intentionally limited to small, main-thread batches: Minecraft does not permit block
 * writes from an async thread. When FastAsyncWorldEdit is installed, the planned columns are
 * converted into one clipboard and pasted through the FAWE bridge instead.</p>
 */
public final class SmoothTerrainGenerator {

    public static final int WIDTH = 100;
    /** Smallest one-side length whose control grid keeps at least two cells. */
    public static final int MIN_WIDTH = 21;
    /** Largest one-side length accepted for a single temporary test map. */
    public static final int MAX_WIDTH = 256;
    public static final int MAX_HEIGHT_DELTA = 4;
    public static final int UNDERGROUND_DEPTH = 200;
    public static final int SURFACE_ONLY_FOUNDATION_LAYERS = 2;
    // Wider control cells make broad, gently rolling land instead of many small bumps.
    private static final int CONTROL_SPACING = 20;
    private static final int COLUMNS_PER_TICK = 16;
    /** Kept as a public compatibility constant for callers that describe the surface stack. */
    public static final int LAYERS = 50;
    private static final int CLEAR_ABOVE = 16;
    private static final String STATE_FILE = "testarena.yml";

    private final Plugin plugin;
    private final TerrainEditBridge terrainEditBridge;
    private final ConcurrentMap<UUID, BukkitTask> running = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> busy = ConcurrentHashMap.newKeySet();
    // TestArena owns zero or more maps; serialise operations so two clipboards can never overlap.
    private final Object operationLock = new Object();
    private long nextOperationId;
    private volatile long activeOperationId;
    private volatile UUID activeOperationOwner;
    private volatile boolean editInFlight;
    private final ConcurrentMap<String, Area> maps = new ConcurrentHashMap<>();

    public SmoothTerrainGenerator(Plugin plugin) {
        this(plugin, null);
    }

    public SmoothTerrainGenerator(Plugin plugin, TerrainEditBridge terrainEditBridge) {
        this.plugin = plugin;
        this.terrainEditBridge = terrainEditBridge;
        this.maps.putAll(readState());
    }

    /** Shape is independent from the material palette, so Grass can also be a bowl. */
    public enum TerrainShape {
        RANDOM("Random", "Smooth random height map"),
        CENTER_LOW("Center-low bowl", "The centre gently slopes down");

        private final String label;
        private final String description;

        TerrainShape(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    public enum TerrainMap {
        GRASS_STONE("grass-stone", "Grass / Stone", Material.GRASS_BLOCK, Material.DIRT, Material.STONE),
        SAND_SANDSTONE("sand-sandstone", "Sand / Sandstone", Material.SAND, Material.SAND, Material.SANDSTONE),
        RED_SAND_RED_SANDSTONE("red-sand-red-sandstone", "Red Sand / Red Sandstone",
                Material.RED_SAND, Material.RED_SAND, Material.RED_SANDSTONE);

        private final String key;
        private final String displayName;
        private final Material top;
        private final Material middle;
        private final Material deep;

        TerrainMap(String key, String displayName, Material top, Material middle, Material deep) {
            this.key = key;
            this.displayName = displayName;
            this.top = top;
            this.middle = middle;
            this.deep = deep;
        }

        public String key() {
            return key;
        }

        public String displayName() {
            return displayName;
        }

        /** Material for a layer counted downward from the surface. */
        public Material materialAtLayer(int layer) {
            if (this == GRASS_STONE) {
                return layer == 1 ? top : layer <= 3 ? middle : deep;
            }
            if (this == RED_SAND_RED_SANDSTONE) {
                return layer <= 3 ? top : deep;
            }
            return layer <= 4 ? middle : deep;
        }

        public static TerrainMap parse(String raw) {
            if (raw == null) {
                return null;
            }
            for (TerrainMap map : values()) {
                if (map.key.equalsIgnoreCase(raw) || map.name().equalsIgnoreCase(raw)) {
                    return map;
                }
            }
            return null;
        }
    }

    /** Settings selected in the PvP map menu. */
    public record TerrainSettings(TerrainMap map, int sideLength, TerrainShape shape,
                                  boolean surfaceOnly, int maxHeightDelta) {
        public TerrainSettings {
            if (map == null) {
                throw new IllegalArgumentException("map is required");
            }
            if (shape == null) {
                shape = TerrainShape.RANDOM;
            }
            int length = sideLength == 0 ? WIDTH : sideLength;
            if (length < MIN_WIDTH || length > MAX_WIDTH) {
                throw new IllegalArgumentException("sideLength must be within ["
                        + MIN_WIDTH + ", " + MAX_WIDTH + "]");
            }
            sideLength = length;
            maxHeightDelta = Math.max(0, Math.min(MAX_HEIGHT_DELTA, maxHeightDelta));
        }

        public TerrainSettings(TerrainMap map, TerrainShape shape, boolean surfaceOnly,
                               int maxHeightDelta) {
            this(map, WIDTH, shape, surfaceOnly, maxHeightDelta);
        }

        /** Number of solid material layers below the surface before the bedrock layer. */
        public int foundationDepth() {
            return surfaceOnly ? SURFACE_ONLY_FOUNDATION_LAYERS : UNDERGROUND_DEPTH;
        }
    }

    /** Summary returned after all columns have been written. */
    public record Result(TerrainMap map, int width, int columns, int minimumHeight,
                         int maximumHeight, long seed) {
        public int heightRange() {
            return maximumHeight - minimumHeight;
        }
    }

    /** A completed temporary test map. */
    public record Area(String id, String world, int centerX, int centerZ, int baseY, int width,
                       TerrainSettings settings, int minY, int maxY) {
        public int size() {
            return width * width;
        }
    }

    /** Planned surface column exposed to the optional FAWE clipboard writer. */
    public record ColumnData(int x, int z, int topY) {
    }

    /** A column plus the vertical span to clear, for bulk deletes across several maps. */
    private record ClearColumn(ColumnData column, int minY, int maxY) {
    }

    public boolean hasPreviousMap() {
        return !maps.isEmpty();
    }

    /** All temporary test maps, keyed by their unique map id. */
    public java.util.Map<String, Area> maps() {
        return java.util.Collections.unmodifiableMap(maps);
    }

    public boolean hasMap(String mapId) {
        return mapId != null && maps.containsKey(mapId);
    }

    public boolean isRunning(UUID playerId) {
        return busy.contains(playerId);
    }

    private long beginOperation(UUID playerId) {
        synchronized (operationLock) {
            if (activeOperationId != 0L) {
                return 0L;
            }
            activeOperationId = ++nextOperationId;
            activeOperationOwner = playerId;
            editInFlight = false;
            return activeOperationId;
        }
    }

    private boolean isActive(long operationId) {
        return activeOperationId == operationId;
    }

    private void markEditInFlight(long operationId) {
        synchronized (operationLock) {
            if (activeOperationId == operationId) {
                editInFlight = true;
            }
        }
    }

    private void releaseOperation(long operationId) {
        synchronized (operationLock) {
            if (activeOperationId == operationId) {
                activeOperationId = 0L;
                activeOperationOwner = null;
                editInFlight = false;
            }
        }
    }

    /** Cancels only the pending generation; a completed map remains available for delete. */
    public void cancel(UUID playerId) {
        BukkitTask task = running.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        busy.remove(playerId);
        synchronized (operationLock) {
            // FAWE cannot safely interrupt an already queued paste. Keep the global lock until
            // its completion callback releases it; planning/fallback writes can be cancelled.
            if (activeOperationOwner != null && activeOperationOwner.equals(playerId)
                    && !editInFlight) {
                activeOperationId = 0L;
                activeOperationOwner = null;
                editInFlight = false;
            }
        }
    }

    /**
     * Plans and builds one smooth terrain map centered on the command sender. Multiple maps can
     * coexist: a second {@code /testarena spawn} does not require deleting the previous one, so
     * several test maps may be placed before any are removed again.
     */
    public void generate(Player player, TerrainMap map, long seed,
                          Consumer<Result> complete, Consumer<String> failure) {
        generate(player, new TerrainSettings(map, TerrainShape.RANDOM, false, MAX_HEIGHT_DELTA),
                seed, complete, failure);
    }

    public void generate(Player player, TerrainSettings settings, long seed,
                          Consumer<Result> complete, Consumer<String> failure) {
        if (settings == null || settings.map() == null) {
            fail(failure, "Unknown test map settings.");
            return;
        }
        UUID playerId = player.getUniqueId();
        long operationId = beginOperation(playerId);
        if (operationId == 0L) {
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        if (!busy.add(playerId)) {
            releaseOperation(operationId);
            fail(failure, "A test map is already being generated for you.");
            return;
        }
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        int foundationDepth = settings.foundationDepth();
        // Keep the bedrock one block above the world's minimum Y. This preserves the requested
        // depth when the world has room, while preventing a deep map from touching the Void.
        int minimumSafeSurface = world.getMinHeight() + foundationDepth + 2;
        int baseY = Math.max(minimumSafeSurface, player.getLocation().getBlockY() - 1);
        int highestSafeSurface = world.getMaxHeight() - CLEAR_ABOVE - settings.maxHeightDelta() - 1;
        baseY = Math.min(baseY, highestSafeSurface);
        int bedrockY = baseY - foundationDepth - 1;
        int width = settings.sideLength();
        Area area = new Area(nextMapId(world.getName()), world.getName(), centerX, centerZ,
                baseY, width, settings, bedrockY, baseY + settings.maxHeightDelta() + CLEAR_ABOVE);

        // No Bukkit world/block calls are made in this future. This keeps noise generation and
        // the 10,000-column plan off the server thread, then hands only the write phase back to
        // Bukkit's scheduler.
        CompletableFuture
                .supplyAsync(() -> plan(area, seed))
                .whenComplete((planned, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!isActive(operationId) || !busy.contains(playerId)) {
                        releaseOperation(operationId);
                        return;
                    }
                    if (error != null || planned == null) {
                        busy.remove(playerId);
                        releaseOperation(operationId);
                        fail(failure, "Could not plan the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    if (terrainEditBridge != null && terrainEditBridge.isAvailable()) {
                        scheduleFawePaste(player, area, planned, seed, complete, failure, operationId);
                    } else {
                        scheduleWrites(player, area, planned, seed, complete, failure, operationId);
                    }
                }));
        // The async planning phase has no BukkitTask yet. A small sentinel task makes a second
        // click fail immediately instead of starting another planner before the callback arrives.
        BukkitTask sentinel = Bukkit.getScheduler().runTaskLater(plugin, () -> { }, 1L);
        running.put(playerId, sentinel);
    }

    /** Removes one completed map (or every map, when {@code mapId} is null) in low-lag batches. */
    public void delete(Player player, Consumer<Integer> complete, Consumer<String> failure) {
        delete(player, null, complete, failure);
    }

    public void delete(Player player, String mapId, Consumer<Integer> complete, Consumer<String> failure) {
        if (mapId == null) {
            deleteAll(player, complete, failure);
            return;
        }
        Area area = maps.get(mapId);
        if (area == null) {
            fail(failure, "There is no test map '" + mapId + "' to delete.");
            return;
        }
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            maps.remove(mapId);
            writeState();
            fail(failure, "The world containing test map '" + mapId + "' is not loaded.");
            return;
        }
        UUID playerId = player.getUniqueId();
        long operationId = beginOperation(playerId);
        if (operationId == 0L) {
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        if (!busy.add(playerId)) {
            releaseOperation(operationId);
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        if (terrainEditBridge != null && terrainEditBridge.isAvailable()) {
            scheduleFaweClear(player, area, mapId, complete, failure, operationId);
            return;
        }
        List<ColumnData> columns = new ArrayList<>(area.width() * area.width());
        for (int x = 0; x < area.width(); x++) {
            for (int z = 0; z < area.width(); z++) {
                columns.add(new ColumnData(area.centerX() - area.width() / 2 + x,
                        area.centerZ() - area.width() / 2 + z, area.maxY()));
            }
        }
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int index;

            @Override
            public void run() {
                int end = Math.min(columns.size(), index + COLUMNS_PER_TICK);
                for (; index < end; index++) {
                    clearColumn(world, columns.get(index), area.minY(), area.maxY());
                }
                if (index >= columns.size()) {
                    maps.remove(mapId);
                    writeState();
                    running.remove(playerId);
                    busy.remove(playerId);
                    releaseOperation(operationId);
                    holder[0].cancel();
                    if (complete != null) {
                        complete.accept(columns.size());
                    }
                }
            }
        }, 1L, 1L);
        running.put(playerId, holder[0]);
    }

    private void deleteAll(Player player, Consumer<Integer> complete, Consumer<String> failure) {
        List<Area> all = new ArrayList<>(maps.values());
        if (all.isEmpty()) {
            fail(failure, "There is no test map to delete.");
            return;
        }
        World world = Bukkit.getWorld(all.get(0).world());
        if (world == null) {
            maps.clear();
            writeState();
            fail(failure, "The world containing the test maps is not loaded.");
            return;
        }
        UUID playerId = player.getUniqueId();
        long operationId = beginOperation(playerId);
        if (operationId == 0L) {
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        if (!busy.add(playerId)) {
            releaseOperation(operationId);
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        int totalColumns = all.stream().mapToInt(a -> a.width() * a.width()).sum();
        if (terrainEditBridge != null && terrainEditBridge.isAvailable()) {
            scheduleFaweClearAll(player, all, totalColumns, complete, failure, operationId);
            return;
        }
        List<ClearColumn> clearColumns = new ArrayList<>(totalColumns);
        all.forEach(area -> {
            for (int x = 0; x < area.width(); x++) {
                for (int z = 0; z < area.width(); z++) {
                    clearColumns.add(new ClearColumn(
                            new ColumnData(area.centerX() - area.width() / 2 + x,
                                    area.centerZ() - area.width() / 2 + z, area.maxY()),
                            area.minY(), area.maxY()));
                }
            }
        });
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int index;

            @Override
            public void run() {
                int end = Math.min(clearColumns.size(), index + COLUMNS_PER_TICK);
                for (; index < end; index++) {
                    ClearColumn clear = clearColumns.get(index);
                    clearColumn(world, clear.column(), clear.minY(), clear.maxY());
                }
                if (index >= clearColumns.size()) {
                    maps.clear();
                    writeState();
                    running.remove(playerId);
                    busy.remove(playerId);
                    releaseOperation(operationId);
                    holder[0].cancel();
                    if (complete != null) {
                        complete.accept(totalColumns);
                    }
                }
            }
        }, 1L, 1L);
        running.put(playerId, holder[0]);
    }

    private void scheduleFawePaste(Player player, Area area, List<ColumnData> columns, long seed,
                                   Consumer<Result> complete, Consumer<String> failure,
                                   long operationId) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
            releaseOperation(operationId);
            fail(failure, "The target world is no longer loaded.");
            return;
        }
        int minX = area.centerX() - area.width() / 2;
        int minZ = area.centerZ() - area.width() / 2;
        int maxX = minX + area.width() - 1;
        int maxZ = minZ + area.width() - 1;
        markEditInFlight(operationId);
        CompletableFuture<Boolean> paste;
        try {
            paste = terrainEditBridge.paste(world, minX, area.minY(), minZ, maxX, area.maxY(), maxZ,
                    area.settings(), columns);
        } catch (Throwable error) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
            releaseOperation(operationId);
            fail(failure, "FAWE could not start the test map: " + error.getMessage());
            return;
        }
        paste.whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID playerId = player.getUniqueId();
                    running.remove(playerId);
                    busy.remove(playerId);
                    releaseOperation(operationId);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        fail(failure, "FAWE could not paste the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    maps.put(area.id(), area);
                    writeState();
                    Result result = new Result(area.settings().map(), area.width(), columns.size(),
                            minimum(columns, area.baseY()), maximum(columns, area.baseY()), seed);
                    if (complete != null && player.isOnline()) {
                        complete.accept(result);
                    }
                }));
    }

    private void scheduleFaweClear(Player player, Area area, String mapId, Consumer<Integer> complete,
                                   Consumer<String> failure, long operationId) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            busy.remove(player.getUniqueId());
            releaseOperation(operationId);
            fail(failure, "The world containing the test map is no longer loaded.");
            return;
        }
        int minX = area.centerX() - area.width() / 2;
        int minZ = area.centerZ() - area.width() / 2;
        int maxX = minX + area.width() - 1;
        int maxZ = minZ + area.width() - 1;
        markEditInFlight(operationId);
        CompletableFuture<Boolean> clear;
        try {
            clear = terrainEditBridge.clear(world, minX, area.minY(), minZ, maxX, area.maxY(), maxZ);
        } catch (Throwable error) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
            releaseOperation(operationId);
            fail(failure, "FAWE could not start deleting the test map: " + error.getMessage());
            return;
        }
        clear.whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID playerId = player.getUniqueId();
                    running.remove(playerId);
                    busy.remove(playerId);
                    releaseOperation(operationId);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        fail(failure, "FAWE could not delete the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    maps.remove(mapId);
                    writeState();
                    if (complete != null) {
                        complete.accept(area.width() * area.width());
                    }
                }));
    }

    private void scheduleFaweClearAll(Player player, List<Area> all,
                                      int totalColumns, Consumer<Integer> complete,
                                      Consumer<String> failure, long operationId) {
        // The maps are independent regions: submit every clear concurrently and complete once
        // all of them report back. Progress is reported after the whole batch.
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(all.size());
        for (Area area : all) {
            World world = Bukkit.getWorld(area.world());
            if (world == null) {
                maps.remove(area.id());
                continue;
            }
            int minX = area.centerX() - area.width() / 2;
            int minZ = area.centerZ() - area.width() / 2;
            int maxX = minX + area.width() - 1;
            int maxZ = minZ + area.width() - 1;
            try {
                futures.add(terrainEditBridge.clear(world, minX, area.minY(), minZ,
                        maxX, area.maxY(), maxZ));
            } catch (Throwable error) {
                maps.remove(area.id());
            }
        }
        CompletableFuture<Void> joined = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        joined.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean anyFailed = false;
                    for (CompletableFuture<Boolean> future : futures) {
                        if (future.isCompletedExceptionally() || !Boolean.TRUE.equals(future.getNow(false))) {
                            anyFailed = true;
                        }
                    }
                    maps.clear();
                    writeState();
                    running.remove(player.getUniqueId());
                    busy.remove(player.getUniqueId());
                    releaseOperation(operationId);
                    if (anyFailed) {
                        fail(failure, "FAWE could not fully delete the test maps; some blocks "
                                + "may remain. Run /testarena delete again.");
                        return;
                    }
                    if (complete != null) {
                        complete.accept(totalColumns);
                    }
                }));
    }

    private void scheduleWrites(Player player, Area area, List<ColumnData> columns, long seed,
                                Consumer<Result> complete, Consumer<String> failure,
                                long operationId) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
            releaseOperation(operationId);
            fail(failure, "The target world is no longer loaded.");
            return;
        }
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int index;

            @Override
            public void run() {
                int end = Math.min(columns.size(), index + COLUMNS_PER_TICK);
                for (; index < end; index++) {
                    writeColumn(world, columns.get(index), area.settings(), area.minY(), area.maxY());
                }
                if (index >= columns.size()) {
                    maps.put(area.id(), area);
                    writeState();
                    running.remove(player.getUniqueId());
                    busy.remove(player.getUniqueId());
                    releaseOperation(operationId);
                    holder[0].cancel();
                    Result result = new Result(area.settings().map(), area.width(), columns.size(),
                            minimum(columns, area.baseY()), maximum(columns, area.baseY()), seed);
                    if (complete != null && player.isOnline()) {
                        complete.accept(result);
                    }
                }
            }
        }, 1L, 1L);
        running.put(player.getUniqueId(), holder[0]);
    }

    private static List<ColumnData> plan(Area area, long seed) {
        HeightMap map = HeightMap.create(area.width(), seed, area.settings().shape(), area.settings().maxHeightDelta());
        List<ColumnData> columns = new ArrayList<>(area.width() * area.width());
        for (int x = 0; x < area.width(); x++) {
            for (int z = 0; z < area.width(); z++) {
                int worldX = area.centerX() - area.width() / 2 + x;
                int worldZ = area.centerZ() - area.width() / 2 + z;
                columns.add(new ColumnData(worldX, worldZ, area.baseY() + map.heightAt(x, z)));
            }
        }
        return columns;
    }

    private static void writeColumn(World world, ColumnData column, TerrainSettings settings,
                                    int bottomY, int maxY) {
        TerrainMap map = settings.map();
        for (int y = column.topY() + 1; y <= maxY; y++) {
            if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
                world.getBlockAt(column.x(), y, column.z()).setType(Material.AIR, false);
            }
        }
        if (bottomY >= world.getMinHeight() && bottomY < world.getMaxHeight()) {
            world.getBlockAt(column.x(), bottomY, column.z()).setType(Material.BEDROCK, false);
        }
        // Use the selected palette for the complete underground volume too. This is what makes
        // the red-sand preset red sandstone below its three red-sand top layers instead of an
        // unrelated stone cavity.
        int from = Math.max(world.getMinHeight(), bottomY + 1);
        int to = Math.min(world.getMaxHeight() - 1, column.topY());
        for (int y = from; y <= to; y++) {
            int layer = column.topY() - y + 1;
            world.getBlockAt(column.x(), y, column.z()).setType(map.materialAtLayer(layer), false);
        }
    }

    private static void clearColumn(World world, ColumnData column, int minY, int maxY) {
        int from = Math.max(world.getMinHeight(), minY);
        int to = Math.min(world.getMaxHeight() - 1, maxY);
        for (int y = from; y <= to; y++) {
            world.getBlockAt(column.x(), y, column.z()).setType(Material.AIR, false);
        }
    }

    private static int minimum(List<ColumnData> columns, int baseY) {
        return columns.stream().mapToInt(c -> c.topY() - baseY).min().orElse(0);
    }

    private static int maximum(List<ColumnData> columns, int baseY) {
        return columns.stream().mapToInt(c -> c.topY() - baseY).max().orElse(0);
    }

    private void writeState() {
        File file = stateFile();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Area area : maps.values()) {
            String path = "maps." + area.id();
            yaml.set(path + ".world", area.world());
            yaml.set(path + ".center-x", area.centerX());
            yaml.set(path + ".center-z", area.centerZ());
            yaml.set(path + ".base-y", area.baseY());
            yaml.set(path + ".width", area.width());
            yaml.set(path + ".map", area.settings().map().key());
            yaml.set(path + ".shape", area.settings().shape().name());
            yaml.set(path + ".surface-only", area.settings().surfaceOnly());
            yaml.set(path + ".max-height-delta", area.settings().maxHeightDelta());
            yaml.set(path + ".foundation-depth", area.settings().foundationDepth());
            yaml.set(path + ".min-y", area.minY());
            yaml.set(path + ".max-y", area.maxY());
        }
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not persist testarena state: " + e.getMessage());
        }
    }

    private java.util.Map<String, Area> readState() {
        File file = stateFile();
        if (!file.isFile()) {
            return java.util.Map.of();
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            java.util.Map<String, Area> loaded = new java.util.HashMap<>();
            // New format: maps.<id>.* — several maps can coexist.
            org.bukkit.configuration.ConfigurationSection mapsSection =
                    yaml.getConfigurationSection("maps");
            if (mapsSection != null) {
                for (String id : mapsSection.getKeys(false)) {
                    String path = "maps." + id;
                    Area area = areaOf(yaml, path, id);
                    if (area != null) {
                        loaded.put(id, area);
                    }
                }
                return loaded;
            }
            // Legacy format: one top-level map (no id). Migrate it under a synthetic id.
            Area legacy = legacyArea(yaml);
            if (legacy != null) {
                loaded.put(legacy.id(), legacy);
            }
            return loaded;
        } catch (Exception ignored) {
            return java.util.Map.of();
        }
    }

    private Area areaOf(YamlConfiguration yaml, String path, String id) {
        TerrainMap map = TerrainMap.parse(yaml.getString(path + ".map"));
        String world = yaml.getString(path + ".world");
        if (map == null || world == null || !yaml.isSet(path + ".center-x")
                || !yaml.isSet(path + ".base-y")) {
            return null;
        }
        int baseY = yaml.getInt(path + ".base-y");
        TerrainShape shape = parseShape(yaml.getString(path + ".shape"));
        int width = yaml.getInt(path + ".width", WIDTH);
        TerrainSettings settings;
        try {
            settings = new TerrainSettings(map, width, shape,
                    yaml.getBoolean(path + ".surface-only", false),
                    yaml.getInt(path + ".max-height-delta", MAX_HEIGHT_DELTA));
        } catch (IllegalArgumentException ignored) {
            settings = new TerrainSettings(map, shape,
                    yaml.getBoolean(path + ".surface-only", false),
                    yaml.getInt(path + ".max-height-delta", MAX_HEIGHT_DELTA));
            width = settings.sideLength();
        }
        int defaultBedrockY = baseY - settings.foundationDepth() - 1;
        return new Area(id, world, yaml.getInt(path + ".center-x"), yaml.getInt(path + ".center-z"),
                baseY, width, settings,
                yaml.getInt(path + ".min-y", defaultBedrockY),
                yaml.getInt(path + ".max-y", baseY + settings.maxHeightDelta() + CLEAR_ABOVE));
    }

    private Area legacyArea(YamlConfiguration yaml) {
        TerrainMap map = TerrainMap.parse(yaml.getString("map"));
        String world = yaml.getString("world");
        if (map == null || world == null || !yaml.isSet("center-x") || !yaml.isSet("base-y")) {
            return null;
        }
        int baseY = yaml.getInt("base-y");
        TerrainShape shape = parseShape(yaml.getString("shape"));
        TerrainSettings settings = new TerrainSettings(map, shape,
                yaml.getBoolean("surface-only", false),
                yaml.getInt("max-height-delta", MAX_HEIGHT_DELTA));
        String id = nextMapId(world);
        int defaultBedrockY = baseY - settings.foundationDepth() - 1;
        return new Area(id, world, yaml.getInt("center-x"), yaml.getInt("center-z"), baseY,
                yaml.getInt("width", WIDTH), settings,
                yaml.getInt("min-y", defaultBedrockY),
                yaml.getInt("max-y", baseY + settings.maxHeightDelta() + CLEAR_ABOVE));
    }

    private TerrainShape parseShape(String raw) {
        if (raw == null) {
            return TerrainShape.RANDOM;
        }
        try {
            return TerrainShape.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return TerrainShape.RANDOM;
        }
    }

    /** Stable, filesystem-safe map id unique within the loaded map set. */
    private String nextMapId(String worldName) {
        long stamp = System.currentTimeMillis();
        String candidate;
        do {
            candidate = worldName + "-" + Long.toString(stamp, 36);
            stamp++;
        } while (maps.containsKey(candidate));
        return candidate;
    }

    private File stateFile() {
        return new File(PluginIdentity.dataFolder(plugin), STATE_FILE);
    }

    private static void fail(Consumer<String> failure, String message) {
        if (failure != null) {
            failure.accept(message);
        }
    }

    /** Pure height map: coarse random control points plus smooth-step interpolation. */
    static final class HeightMap {
        private final int width;
        private final int[] positions;
        private final int[][] control;
        private final int[][] surface;
        private final int minimum;
        private final int maximum;

        private HeightMap(int width, int[] positions, int[][] control, int[][] surface,
                          int minimum, int maximum) {
            this.width = width;
            this.positions = positions;
            this.control = control;
            this.surface = surface;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        static HeightMap create(int width, long seed) {
            return create(width, seed, TerrainShape.RANDOM);
        }

        static HeightMap create(int width, long seed, TerrainShape shape) {
            return create(width, seed, shape, MAX_HEIGHT_DELTA);
        }

        static HeightMap create(int width, long seed, TerrainShape shape, int maxHeightDelta) {
            maxHeightDelta = Math.max(0, Math.min(MAX_HEIGHT_DELTA, maxHeightDelta));
            int gridSize = (int) Math.ceil((width - 1) / (double) CONTROL_SPACING) + 1;
            int[] positions = new int[gridSize];
            for (int i = 0; i < gridSize; i++) {
                positions[i] = Math.min(width - 1, i * CONTROL_SPACING);
            }
            positions[gridSize - 1] = width - 1;
            int[][] values = new int[gridSize][gridSize];
            SplittableRandom random = new SplittableRandom(seed);
            int minimum = Integer.MAX_VALUE;
            int maximum = Integer.MIN_VALUE;
            for (int x = 0; x < gridSize; x++) {
                for (int z = 0; z < gridSize; z++) {
                    if (shape == TerrainShape.CENTER_LOW) {
                        // Use a broad smooth-step depression rather than a pointed dish with a
                        // visible circular rim. A tiny low-frequency perturbation keeps the
                        // terrain natural while the centre remains clearly lower than the edge.
                        double nx = positions[x] / (double) (width - 1) * 2.0d - 1.0d;
                        double nz = positions[z] / (double) (width - 1) * 2.0d - 1.0d;
                        double distance = Math.min(1.0d, Math.sqrt(nx * nx + nz * nz) / Math.sqrt(2.0d));
                        double smoothDistance = distance * distance * (3.0d - 2.0d * distance);
                        double variation = maxHeightDelta < 2 ? 0.0d
                                : (random.nextDouble() - 0.5d) * Math.min(0.8d, maxHeightDelta * 0.2d);
                        values[x][z] = Math.max(0, Math.min(maxHeightDelta,
                                (int) Math.round(smoothDistance * maxHeightDelta + variation)));
                    } else {
                        values[x][z] = random.nextInt(maxHeightDelta + 1);
                    }
                    minimum = Math.min(minimum, values[x][z]);
                    maximum = Math.max(maximum, values[x][z]);
                }
            }
            if (maximum > minimum) {
                for (int x = 0; x < gridSize; x++) {
                    for (int z = 0; z < gridSize; z++) {
                        values[x][z] = Math.round((values[x][z] - minimum)
                                * (float) maxHeightDelta / (maximum - minimum));
                    }
                }
                minimum = 0;
                maximum = maxHeightDelta;
            }
            // The final control interval is only three blocks wide (100 is not an exact
            // multiple of eight). Cap control-point changes by the physical interval length;
            // otherwise a 0 -> 5 change over those three blocks would still produce a visible
            // two- or three-block cliff at the map edge after rounding.
            for (int pass = 0; pass < 3; pass++) {
                for (int x = 0; x < gridSize - 1; x++) {
                    int span = positions[x + 1] - positions[x];
                    for (int z = 0; z < gridSize; z++) {
                        values[x + 1][z] = clampStep(values[x][z], values[x + 1][z], span);
                    }
                }
                for (int z = 0; z < gridSize - 1; z++) {
                    int span = positions[z + 1] - positions[z];
                    for (int x = 0; x < gridSize; x++) {
                        values[x][z + 1] = clampStep(values[x][z], values[x][z + 1], span);
                    }
                }
            }
            int[][] surface = new int[width][width];
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    surface[x][z] = interpolatedHeight(width, positions, values, x, z);
                }
            }
            // Rounding a smooth curve can still make a two-block jump where two axes meet.
            // A few directional relaxation passes produce an integer 1-Lipschitz surface while
            // retaining the selected envelope. This is pure data work and runs off-thread.
            for (int pass = 0; pass < width; pass++) {
                for (int x = 1; x < width; x++) {
                    for (int z = 0; z < width; z++) {
                        surface[x][z] = clampStep(surface[x - 1][z], surface[x][z], 1);
                    }
                }
                for (int x = width - 2; x >= 0; x--) {
                    for (int z = 0; z < width; z++) {
                        surface[x][z] = clampStep(surface[x + 1][z], surface[x][z], 1);
                    }
                }
                for (int z = 1; z < width; z++) {
                    for (int x = 0; x < width; x++) {
                        surface[x][z] = clampStep(surface[x][z - 1], surface[x][z], 1);
                    }
                }
                for (int z = width - 2; z >= 0; z--) {
                    for (int x = 0; x < width; x++) {
                        surface[x][z] = clampStep(surface[x][z + 1], surface[x][z], 1);
                    }
                }
            }
            return new HeightMap(width, positions, values, surface, minimum, maximum);
        }

        int heightAt(int x, int z) {
            x = Math.max(0, Math.min(width - 1, x));
            z = Math.max(0, Math.min(width - 1, z));
            return surface[x][z];
        }

        private static int interpolatedHeight(int width, int[] positions, int[][] control,
                                              int x, int z) {
            int gridSize = positions.length;
            int gx = 0;
            int gz = 0;
            while (gx < gridSize - 2 && x > positions[gx + 1]) {
                gx++;
            }
            while (gz < gridSize - 2 && z > positions[gz + 1]) {
                gz++;
            }
            double tx = smooth((x - positions[gx])
                    / (double) (positions[gx + 1] - positions[gx]));
            double tz = smooth((z - positions[gz])
                    / (double) (positions[gz + 1] - positions[gz]));
            double x0 = lerp(control[gx][gz], control[gx + 1][gz], tx);
            double x1 = lerp(control[gx][gz + 1], control[gx + 1][gz + 1], tx);
            return (int) Math.round(lerp(x0, x1, tz));
        }

        int minimum() {
            return minimum;
        }

        int maximum() {
            return maximum;
        }

        private int segment(int coordinate) {
            for (int i = 0; i < positions.length - 1; i++) {
                if (coordinate <= positions[i + 1]) {
                    return i;
                }
            }
            return positions.length - 2;
        }

        private static int clampStep(int previous, int current, int span) {
            return Math.max(previous - span, Math.min(previous + span, current));
        }

        private static double smooth(double value) {
            return value * value * (3.0d - 2.0d * value);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
