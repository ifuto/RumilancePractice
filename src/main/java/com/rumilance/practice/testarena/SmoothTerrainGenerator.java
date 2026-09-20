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
    public static final int MAX_HEIGHT_DELTA = 5;
    // Wider control cells make broad, gently rolling land instead of many small bumps.
    private static final int CONTROL_SPACING = 20;
    private static final int COLUMNS_PER_TICK = 16;
    public static final int LAYERS = 50;
    private static final int CLEAR_ABOVE = 16;
    private static final String STATE_FILE = "testarena.yml";

    private final Plugin plugin;
    private final TerrainEditBridge terrainEditBridge;
    private final ConcurrentMap<UUID, BukkitTask> running = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private volatile Area previousMap;

    public SmoothTerrainGenerator(Plugin plugin) {
        this(plugin, null);
    }

    public SmoothTerrainGenerator(Plugin plugin, TerrainEditBridge terrainEditBridge) {
        this.plugin = plugin;
        this.terrainEditBridge = terrainEditBridge;
        this.previousMap = readState();
    }

    public enum TerrainMap {
        GRASS_STONE("grass-stone", "Grass / Stone", Material.GRASS_BLOCK, Material.DIRT, Material.STONE),
        SAND_SANDSTONE("sand-sandstone", "Sand / Sandstone", Material.SAND, Material.SAND, Material.SANDSTONE);

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

        /** Material for a layer counted downward from the surface (1..50). */
        Material materialAtLayer(int layer) {
            if (this == GRASS_STONE) {
                return layer == 1 ? top : layer <= 3 ? middle : deep;
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

    /** Summary returned after all columns have been written. */
    public record Result(TerrainMap map, int width, int columns, int minimumHeight,
                         int maximumHeight, long seed) {
        public int heightRange() {
            return maximumHeight - minimumHeight;
        }
    }

    private record Area(String world, int centerX, int centerZ, int baseY, int width,
                        TerrainMap map, int minY, int maxY) {
    }

    /** Planned surface column exposed to the optional FAWE clipboard writer. */
    public record ColumnData(int x, int z, int topY) {
    }

    public boolean hasPreviousMap() {
        return previousMap != null;
    }

    public boolean isRunning(UUID playerId) {
        return busy.contains(playerId);
    }

    /** Cancels only the pending generation; a completed map remains available for delete. */
    public void cancel(UUID playerId) {
        BukkitTask task = running.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        busy.remove(playerId);
    }

    /**
     * Plans and builds the selected 100 by 100 map centered on the command sender. A previous
     * map must be deleted first so {@code /testarena delete} always has one unambiguous target.
     */
    public void generate(Player player, TerrainMap map, long seed,
                          Consumer<Result> complete, Consumer<String> failure) {
        if (map == null) {
            fail(failure, "Unknown test map.");
            return;
        }
        if (previousMap != null) {
            fail(failure, "Delete the previous test map first with /testarena delete.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!busy.add(playerId)) {
            fail(failure, "A test map is already being generated for you.");
            return;
        }
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();
        int baseY = Math.max(world.getMinHeight() + LAYERS + 2, player.getLocation().getBlockY() - 1);
        Area area = new Area(world.getName(), centerX, centerZ, baseY, WIDTH, map,
                baseY - (LAYERS - 1), baseY + MAX_HEIGHT_DELTA + CLEAR_ABOVE);

        // No Bukkit world/block calls are made in this future. This keeps noise generation and
        // the 10,000-column plan off the server thread, then hands only the write phase back to
        // Bukkit's scheduler.
        CompletableFuture
                .supplyAsync(() -> plan(area, seed))
                .whenComplete((planned, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!busy.contains(playerId)) {
                        return;
                    }
                    if (error != null || planned == null) {
                        busy.remove(playerId);
                        fail(failure, "Could not plan the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    if (terrainEditBridge != null && terrainEditBridge.isAvailable()) {
                        scheduleFawePaste(player, area, planned, seed, complete, failure);
                    } else {
                        scheduleWrites(player, area, planned, seed, complete, failure);
                    }
                }));
        // The async planning phase has no BukkitTask yet. A small sentinel task makes a second
        // click fail immediately instead of starting another planner before the callback arrives.
        BukkitTask sentinel = Bukkit.getScheduler().runTaskLater(plugin, () -> { }, 1L);
        running.put(playerId, sentinel);
    }

    /** Removes the last completed map in the same low-lag batches as generation. */
    public void delete(Player player, Consumer<Integer> complete, Consumer<String> failure) {
        Area area = previousMap;
        if (area == null) {
            fail(failure, "There is no previous test map to delete.");
            return;
        }
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            previousMap = null;
            deleteState();
            fail(failure, "The world containing the previous test map is not loaded.");
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!busy.add(playerId)) {
            fail(failure, "Wait for the current test map operation to finish.");
            return;
        }
        if (terrainEditBridge != null && terrainEditBridge.isAvailable()) {
            scheduleFaweClear(player, area, complete, failure);
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
                    previousMap = null;
                    deleteState();
                    running.remove(playerId);
                    busy.remove(playerId);
                    holder[0].cancel();
                    if (complete != null) {
                        complete.accept(columns.size());
                    }
                }
            }
        }, 1L, 1L);
        running.put(playerId, holder[0]);
    }

    private void scheduleFawePaste(Player player, Area area, List<ColumnData> columns, long seed,
                                   Consumer<Result> complete, Consumer<String> failure) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
            fail(failure, "The target world is no longer loaded.");
            return;
        }
        int minX = area.centerX() - area.width() / 2;
        int minZ = area.centerZ() - area.width() / 2;
        int maxX = minX + area.width() - 1;
        int maxZ = minZ + area.width() - 1;
        terrainEditBridge.paste(world, minX, area.minY(), minZ, maxX, area.maxY(), maxZ,
                        area.map(), columns)
                .whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID playerId = player.getUniqueId();
                    running.remove(playerId);
                    busy.remove(playerId);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        fail(failure, "FAWE could not paste the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    previousMap = area;
                    writeState(area);
                    Result result = new Result(area.map(), area.width(), columns.size(),
                            minimum(columns, area.baseY()), maximum(columns, area.baseY()), seed);
                    if (complete != null && player.isOnline()) {
                        complete.accept(result);
                    }
                }));
    }

    private void scheduleFaweClear(Player player, Area area, Consumer<Integer> complete,
                                   Consumer<String> failure) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            busy.remove(player.getUniqueId());
            fail(failure, "The world containing the previous test map is no longer loaded.");
            return;
        }
        int minX = area.centerX() - area.width() / 2;
        int minZ = area.centerZ() - area.width() / 2;
        int maxX = minX + area.width() - 1;
        int maxZ = minZ + area.width() - 1;
        terrainEditBridge.clear(world, minX, area.minY(), minZ, maxX, area.maxY(), maxZ)
                .whenComplete((success, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID playerId = player.getUniqueId();
                    running.remove(playerId);
                    busy.remove(playerId);
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        fail(failure, "FAWE could not delete the test map: "
                                + (error == null ? "unknown error" : error.getMessage()));
                        return;
                    }
                    previousMap = null;
                    deleteState();
                    if (complete != null) {
                        complete.accept(area.width() * area.width());
                    }
                }));
    }

    private void scheduleWrites(Player player, Area area, List<ColumnData> columns, long seed,
                                Consumer<Result> complete, Consumer<String> failure) {
        World world = Bukkit.getWorld(area.world());
        if (world == null) {
            running.remove(player.getUniqueId());
            busy.remove(player.getUniqueId());
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
                    writeColumn(world, columns.get(index), area.map());
                }
                if (index >= columns.size()) {
                    previousMap = area;
                    writeState(area);
                    running.remove(player.getUniqueId());
                    holder[0].cancel();
                    Result result = new Result(area.map(), area.width(), columns.size(),
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
        HeightMap map = HeightMap.create(area.width(), seed);
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

    private static void writeColumn(World world, ColumnData column, TerrainMap map) {
        for (int y = column.topY() + 1; y <= column.topY() + CLEAR_ABOVE; y++) {
            if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
                world.getBlockAt(column.x(), y, column.z()).setType(Material.AIR, false);
            }
        }
        for (int layer = 1; layer <= LAYERS; layer++) {
            int y = column.topY() - (layer - 1);
            if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
                world.getBlockAt(column.x(), y, column.z()).setType(map.materialAtLayer(layer), false);
            }
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

    private void writeState(Area area) {
        File file = stateFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", area.world());
        yaml.set("center-x", area.centerX());
        yaml.set("center-z", area.centerZ());
        yaml.set("base-y", area.baseY());
        yaml.set("width", area.width());
        yaml.set("map", area.map().key());
        yaml.set("min-y", area.minY());
        yaml.set("max-y", area.maxY());
        try {
            yaml.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not persist testarena state: " + e.getMessage());
        }
    }

    private Area readState() {
        File file = stateFile();
        if (!file.isFile()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            TerrainMap map = TerrainMap.parse(yaml.getString("map"));
            String world = yaml.getString("world");
            if (map == null || world == null || !yaml.isSet("center-x") || !yaml.isSet("base-y")) {
                return null;
            }
            int baseY = yaml.getInt("base-y");
            return new Area(world, yaml.getInt("center-x"), yaml.getInt("center-z"), baseY,
                    yaml.getInt("width", WIDTH), map,
                    yaml.getInt("min-y", baseY - LAYERS + 1),
                    yaml.getInt("max-y", baseY + MAX_HEIGHT_DELTA + CLEAR_ABOVE));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void deleteState() {
        File file = stateFile();
        if (file.isFile() && !file.delete()) {
            plugin.getLogger().warning("Could not remove " + file.getName());
        }
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
                    values[x][z] = random.nextInt(MAX_HEIGHT_DELTA + 1);
                    minimum = Math.min(minimum, values[x][z]);
                    maximum = Math.max(maximum, values[x][z]);
                }
            }
            if (maximum > minimum) {
                for (int x = 0; x < gridSize; x++) {
                    for (int z = 0; z < gridSize; z++) {
                        values[x][z] = Math.round((values[x][z] - minimum)
                                * (float) MAX_HEIGHT_DELTA / (maximum - minimum));
                    }
                }
                minimum = 0;
                maximum = MAX_HEIGHT_DELTA;
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
            // retaining the 0..5 envelope. This is pure data work and runs off-thread.
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
