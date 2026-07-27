package com.rumilance.practice.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Immutable axis-aligned block region within a single world, normalized so that
 * {@code min <= max} on every axis. Coordinates are stored as block (integer) coordinates.
 */
public final class Cuboid {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private Cuboid(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static Cuboid of(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        Objects.requireNonNull(worldName, "worldName");
        return new Cuboid(
                worldName,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }

    public static Cuboid of(Location a, Location b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        World worldA = a.getWorld();
        World worldB = b.getWorld();
        if (worldA == null || worldB == null || !worldA.equals(worldB)) {
            throw new IllegalArgumentException("Both locations must belong to the same, loaded world");
        }
        return of(worldA.getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String worldName() {
        return worldName;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null || !world.getName().equals(worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Cuboid expand(int amount) {
        return new Cuboid(worldName, minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    public Location minLocation() {
        return new Location(resolveWorldOrNull(), minX, minY, minZ);
    }

    public Location maxLocation() {
        return new Location(resolveWorldOrNull(), maxX, maxY, maxZ);
    }

    public Location center() {
        World world = resolveWorldOrNull();
        double x = minX + (maxX - minX) / 2.0d + 0.5d;
        double y = minY + (maxY - minY) / 2.0d;
        double z = minZ + (maxZ - minZ) / 2.0d + 0.5d;
        return new Location(world, x, y, z);
    }

    private World resolveWorldOrNull() {
        return Bukkit.getWorld(worldName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cuboid other)) {
            return false;
        }
        return minX == other.minX && minY == other.minY && minZ == other.minZ
                && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ
                && worldName.equals(other.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "Cuboid{world=" + worldName + ", min=(" + minX + "," + minY + "," + minZ
                + "), max=(" + maxX + "," + maxY + "," + maxZ + ")}";
    }
}
