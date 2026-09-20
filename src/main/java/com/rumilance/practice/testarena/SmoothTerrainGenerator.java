package com.rumilance.practice.testarena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Small, deterministic test landscape used by {@code /testarena spawnde}.
 *
 * <p>The height map is made from a coarse control grid and smooth-step interpolation rather
 * than independent random columns. That keeps adjacent columns within one block of each other
 * while the complete area stays within a five-block envelope. It is deliberately a plugin-side
 * builder, so it works in a normal world and does not depend on FAWE or a datapack.</p>
 */
public final class SmoothTerrainGenerator {

    public static final int DEFAULT_RADIUS = 24;
    public static final int MAX_RADIUS = 48;
    public static final int MAX_HEIGHT_DELTA = 5;
    private static final int CONTROL_SPACING = 8;
    private static final int BATCH_COLUMNS = 192;
    private static final int CLEAR_ABOVE = 8;

    private final Plugin plugin;
    private final ConcurrentMap<UUID, BukkitTask> running = new ConcurrentHashMap<>();

    public SmoothTerrainGenerator(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Summary returned after all columns have been written. */
    public record Result(int radius, int columns, int minimumHeight, int maximumHeight, long seed) {
        public int heightRange() {
            return maximumHeight - minimumHeight;
        }
    }

    /** Cancels an unfinished generation owned by the player. */
    public void cancel(UUID playerId) {
        BukkitTask task = running.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean isRunning(UUID playerId) {
        BukkitTask task = running.get(playerId);
        return task != null && !task.isCancelled();
    }

    /**
     * Builds a square centered on {@code center}. All Bukkit world writes happen on the server
     * thread in small batches to avoid a long tick when the command is used on a live server.
     */
    public void generate(Player player, int requestedRadius, long seed, Consumer<Result> complete) {
        int radius = Math.max(8, Math.min(MAX_RADIUS, requestedRadius));
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        cancel(playerId);

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        // Keep the new island around the command location, rather than rebuilding a possibly
        // distant natural surface under a player standing in a tall structure.
        int baseY = Math.max(world.getMinHeight() + 8, center.getBlockY() - 1);
        HeightMap map = HeightMap.create(radius, seed);
        List<Column> columns = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                columns.add(new Column(centerX + x, centerZ + z, baseY + map.heightAt(x + radius, z + radius)));
            }
        }

        BukkitTask task = new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (!player.isOnline() || player.getWorld() != world) {
                    finish(null);
                    return;
                }
                int end = Math.min(columns.size(), index + BATCH_COLUMNS);
                for (; index < end; index++) {
                    writeColumn(world, columns.get(index));
                }
                if (index >= columns.size()) {
                    finish(new Result(radius, columns.size(), map.minimum(), map.maximum(), seed));
                }
            }

            private void finish(Result result) {
                running.remove(playerId);
                cancel();
                if (result != null && player.isOnline() && player.getWorld() == world) {
                    int centerHeight = map.heightAt(radius, radius);
                    Location spawn = new Location(world, centerX + 0.5d,
                            baseY + centerHeight + 1.0d, centerZ + 0.5d,
                            player.getLocation().getYaw(), player.getLocation().getPitch());
                    player.teleport(spawn);
                    if (complete != null) {
                        complete.accept(result);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        running.put(playerId, task);
    }

    private static void writeColumn(World world, Column column) {
        int top = column.topY();
        // A bounded clear makes re-running the command idempotent and removes trees / old
        // terrain above the new five-block surface without touching blocks below the foundation.
        for (int y = top + 1; y <= top + CLEAR_ABOVE; y++) {
            if (y < world.getMaxHeight()) {
                world.getBlockAt(column.x(), y, column.z()).setType(Material.AIR, false);
            }
        }
        for (int y = top - 4; y <= top; y++) {
            Material material = y == top
                    ? Material.GRASS_BLOCK
                    : y >= top - 2 ? Material.DIRT : Material.STONE;
            if (y >= world.getMinHeight() && y < world.getMaxHeight()) {
                world.getBlockAt(column.x(), y, column.z()).setType(material, false);
            }
        }
    }

    private record Column(int x, int z, int topY) {
    }

    /** Pure height map so the terrain guarantees can be unit-tested without a Bukkit server. */
    static final class HeightMap {
        private final int radius;
        private final int gridSize;
        private final int[][] control;
        private final int minimum;
        private final int maximum;

        private HeightMap(int radius, int gridSize, int[][] control, int minimum, int maximum) {
            this.radius = radius;
            this.gridSize = gridSize;
            this.control = control;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        static HeightMap create(int radius, long seed) {
            int size = radius * 2 + 1;
            int gridSize = (size - 1) / CONTROL_SPACING + 1;
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
            // Normalize the control grid so the demo reliably exercises the full five-block
            // range even with an unlucky seed, while preserving its smooth slopes.
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
            return new HeightMap(radius, gridSize, values, minimum, maximum);
        }

        int heightAt(int x, int z) {
            int size = radius * 2 + 1;
            x = Math.max(0, Math.min(size - 1, x));
            z = Math.max(0, Math.min(size - 1, z));
            int gx = Math.min(gridSize - 2, x / CONTROL_SPACING);
            int gz = Math.min(gridSize - 2, z / CONTROL_SPACING);
            int localX = x - gx * CONTROL_SPACING;
            int localZ = z - gz * CONTROL_SPACING;
            double tx = smooth(localX / (double) CONTROL_SPACING);
            double tz = smooth(localZ / (double) CONTROL_SPACING);
            // At the final edge the last control point is exactly at the boundary.
            if (x == size - 1) {
                gx = gridSize - 2;
                tx = 1.0d;
            }
            if (z == size - 1) {
                gz = gridSize - 2;
                tz = 1.0d;
            }
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

        private static double smooth(double value) {
            return value * value * (3.0d - 2.0d * value);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
