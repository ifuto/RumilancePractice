package com.rumilance.practice.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.function.IntPredicate;

/**
 * Standing pose for a <em>pinned</em> teleport (lobby spawn, duel p1/p2, FFA fixed spawn).
 * X/Z stay on that one block. Y only un-buries onto the surface of the same column  E
 * it never walks to a cave floor or a roof several blocks away.
 */
public final class SpawnFooting {

    /** Same-column: the block underfoot, or one more if YAML stored standing height. */
    static final int PIN_DOWN = 2;
    /** Same-column: pop out of a thick floor after schematic paste; not a cave hunt. */
    static final int PIN_UP = 16;
    static final int FORCE_UP = 48;

    /** Upward scan cap for the post-teleport un-bury sweep in SafeTeleport. */
    public static int maxLiftForUnbury() {
        return FORCE_UP;
    }
    private static final double HALF_WIDTH = 0.3d;
    private static final double HEIGHT = 1.8d;
    private static final double EPS = 1.0e-3d;

    private SpawnFooting() {
    }

    /**
     * {@code spawn} is the configured point. Result keeps that block column. Y is the
     * collision top of the solid at/immediately below it, or {@code null} if that マス
     * has no standable surface nearby.
     */
    public static Location standOneAbove(Location spawn) {
        return standClear(spawn);
    }

    public static Location standClear(Location spawn) {
        if (spawn == null) {
            return null;
        }
        Location desired = LocationUtil.safeTeleportLocation(spawn);
        Location clear = standClearPearl(desired, PIN_UP);
        if (clear != null) {
            return clear;
        }
        return forceLift(desired);
    }

    /**
     * Same-column footing for pearl landings  Enever applies {@link LocationUtil#safeTeleportLocation}
     * (personal WorldBorder must not yank pearls toward arena center / void edge).
     */
    public static Location standClearPearl(Location spawn, int maxLiftUp) {
        if (spawn == null) {
            return null;
        }
        World world = spawn.getWorld();
        if (world == null) {
            return null;
        }
        Location desired = spawn.clone();
        double x = desired.getX();
        double y = desired.getY();
        double z = desired.getZ();
        if (playerFits(world, x, y, z) && supported(world, x, y, z)) {
            return pose(desired, x, y, z);
        }
        int bx = desired.getBlockX();
        int bz = desired.getBlockZ();
        double cx = bx + 0.5d;
        double cz = bz + 0.5d;
        if (playerFits(world, cx, y, cz) && supported(world, cx, y, cz)) {
            return pose(desired, cx, y, cz);
        }
        int startY = desired.getBlockY();
        int liftCap = Math.max(0, maxLiftUp);
        Location down = scan(desired, startY, startY - PIN_DOWN, -1);
        if (down != null) {
            return down;
        }
        Location up = scan(desired, startY + 1, startY + liftCap, 1);
        if (up != null) {
            return up;
        }
        return null;
    }

    /**
     * Last resort in the same column: climb until two air blocks sit on a solid.
     */
    public static Location forceLift(Location spawn) {
        if (spawn == null || spawn.getWorld() == null) {
            return null;
        }
        Location desired = LocationUtil.safeTeleportLocation(spawn);
        int startY = desired.getBlockY();
        int cap = Math.min(desired.getWorld().getMaxHeight() - 3, startY + FORCE_UP);
        Location lifted = scan(desired, startY, cap, 1);
        if (lifted != null) {
            return lifted;
        }
        // A point in mid-air is not a safe fallback. Returning it used to let a player
        // teleport into an incomplete schematic and fall into the void.
        return null;
    }

    public static boolean bothReady(Location spawnA, Location spawnB) {
        return standClear(spawnA) != null && standClear(spawnB) != null;
    }

    public static boolean isBuried(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        return !playerFits(location.getWorld(), location.getX(), location.getY(), location.getZ());
    }

    public static boolean isBuried(Player player) {
        if (player == null) {
            return true;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return overlapsSolid(player.getWorld(), player.getBoundingBox());
    }

    /**
     * Integer-column search used by tests. Stays within {@link #PIN_DOWN}/{@link #PIN_UP}
     * of {@code startY} so a pinned マス cannot fall through to a cave.
     */
    public static int findGroundY(int startY, int minBound, int maxBound,
                                  IntPredicate standable, IntPredicate passable) {
        int toY = Math.max(minBound, startY - PIN_DOWN);
        int fromY = Math.min(maxBound, startY + PIN_UP);
        for (int y = startY; y >= toY; y--) {
            if (standable.test(y) && passable.test(y + 1) && passable.test(y + 2)) {
                return y;
            }
        }
        for (int y = startY + 1; y <= fromY; y++) {
            if (standable.test(y) && passable.test(y + 1) && passable.test(y + 2)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * True when {@code feetY} is standing on the collision top of a solid in this column,
     * not floating and not inside it.
     */
    static boolean supported(World world, double x, double feetY, double z) {
        Block ground = world.getBlockAt(
                (int) Math.floor(x),
                (int) Math.floor(feetY - EPS),
                (int) Math.floor(z)
        );
        if (!isStandable(ground)) {
            return false;
        }
        double top = standYOn(ground);
        return feetY + EPS >= top && feetY <= top + 0.51d;
    }

    private static Location scan(Location desired, int fromY, int toY, int step) {
        World world = desired.getWorld();
        int x = desired.getBlockX();
        int z = desired.getBlockZ();
        for (int groundY = fromY; step < 0 ? groundY >= toY : groundY <= toY; groundY += step) {
            Block ground = world.getBlockAt(x, groundY, z);
            if (!isStandable(ground)) {
                continue;
            }
            double standY = standYOn(ground);
            if (playerFits(world, desired.getX(), standY, desired.getZ())) {
                return pose(desired, desired.getX(), standY, desired.getZ());
            }
            double cx = x + 0.5d;
            double cz = z + 0.5d;
            if (playerFits(world, cx, standY, cz)) {
                return pose(desired, cx, standY, cz);
            }
        }
        return null;
    }

    private static Location pose(Location desired, double x, double y, double z) {
        Location stand = desired.clone();
        stand.setX(x);
        stand.setY(y);
        stand.setZ(z);
        return stand;
    }

    static double standYOn(Block ground) {
        BoundingBox box = ground.getBoundingBox();
        if (box.getVolume() > 0.0d) {
            return box.getMaxY();
        }
        return ground.getY() + 1.0d;
    }

    static boolean playerFits(World world, double x, double feetY, double z) {
        BoundingBox playerBox = new BoundingBox(
                x - HALF_WIDTH + EPS,
                feetY + EPS,
                z - HALF_WIDTH + EPS,
                x + HALF_WIDTH - EPS,
                feetY + HEIGHT - EPS,
                z + HALF_WIDTH - EPS
        );
        return !overlapsSolid(world, playerBox);
    }

    static boolean overlapsSolid(World world, BoundingBox playerBox) {
        int minX = (int) Math.floor(playerBox.getMinX());
        int maxX = (int) Math.floor(playerBox.getMaxX());
        int minY = (int) Math.floor(playerBox.getMinY());
        int maxY = (int) Math.floor(playerBox.getMaxY());
        int minZ = (int) Math.floor(playerBox.getMinZ());
        int maxZ = (int) Math.floor(playerBox.getMaxZ());
        int worldMin = world.getMinHeight();
        int worldMax = world.getMaxHeight() - 1;
        if (minY < worldMin || maxY > worldMax) {
            return true;
        }
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    Material type = block.getType();
                    if (type == Material.LAVA || type == Material.WATER) {
                        return true;
                    }
                    if (block.isPassable()) {
                        continue;
                    }
                    if (block.getBoundingBox().overlaps(playerBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isStandable(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || !type.isSolid() || type.isEmpty()) {
            return false;
        }
        return type != Material.LAVA && type != Material.WATER && !type.name().contains("SIGN");
    }
}
