/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 */
package com.rumilance.practice.util;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

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
        return new Cuboid(worldName, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public static Cuboid of(Location a, Location b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        World worldA = a.getWorld();
        World worldB = b.getWorld();
        if (worldA == null || worldB == null || !worldA.equals((Object)worldB)) {
            throw new IllegalArgumentException("Both locations must belong to the same, loaded world");
        }
        return Cuboid.of(worldA.getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public String worldName() {
        return this.worldName;
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    public long volume() {
        return (long)(this.maxX - this.minX + 1) * (long)(this.maxY - this.minY + 1) * (long)(this.maxZ - this.minZ + 1);
    }

    public boolean contains(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null || !world.getName().equals(this.worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public boolean containsHorizontal(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null || !world.getName().equals(this.worldName)) {
            return false;
        }
        return this.containsHorizontal(location.getBlockX(), location.getBlockZ());
    }

    public boolean containsHorizontal(int x, int z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public Cuboid including(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null || !world.getName().equals(this.worldName)) {
            return this;
        }
        return this.including(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Cuboid including(int x, int y, int z) {
        if (x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ) {
            return this;
        }
        return new Cuboid(this.worldName, Math.min(this.minX, x), Math.min(this.minY, y), Math.min(this.minZ, z), Math.max(this.maxX, x), Math.max(this.maxY, y), Math.max(this.maxZ, z));
    }

    public Location clampHorizontal(Location location) {
        Objects.requireNonNull(location, "location");
        Location copy = location.clone();
        double loX = (double)this.minX + 0.3;
        double hiX = (double)this.maxX + 0.7;
        double loZ = (double)this.minZ + 0.3;
        double hiZ = (double)this.maxZ + 0.7;
        if (loX > hiX) {
            loX = hiX = (double)(this.minX + this.maxX + 1) / 2.0;
        }
        if (loZ > hiZ) {
            loZ = hiZ = (double)(this.minZ + this.maxZ + 1) / 2.0;
        }
        copy.setX(Math.min(hiX, Math.max(loX, copy.getX())));
        copy.setZ(Math.min(hiZ, Math.max(loZ, copy.getZ())));
        return copy;
    }

    public Location slideHorizontal(Location from, Location to) {
        Objects.requireNonNull(from, "from");
        if (to == null) {
            return null;
        }
        if (this.containsHorizontal(to.getBlockX(), to.getBlockZ())) {
            return to.clone();
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 1.0E-6 && Math.abs(dz) < 1.0E-6) {
            Location clamped = this.clampHorizontal(to);
            clamped.setY(to.getY());
            clamped.setYaw(to.getYaw());
            clamped.setPitch(to.getPitch());
            return clamped;
        }
        double tBest = 0.0;
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 12; ++i) {
            double mid = (lo + hi) * 0.5;
            double px = from.getX() + dx * mid;
            double pz = from.getZ() + dz * mid;
            if (this.containsHorizontal((int)Math.floor(px), (int)Math.floor(pz))) {
                tBest = mid;
                lo = mid;
                continue;
            }
            hi = mid;
        }
        Location result = from.clone();
        result.setX(from.getX() + dx * tBest);
        result.setZ(from.getZ() + dz * tBest);
        result.setY(to.getY());
        result.setYaw(to.getYaw());
        result.setPitch(to.getPitch());
        if (!this.containsHorizontal(result.getBlockX(), result.getBlockZ())) {
            result = this.clampHorizontal(result);
            result.setY(to.getY());
            result.setYaw(to.getYaw());
            result.setPitch(to.getPitch());
        }
        return result;
    }

    public static double[] clampHorizontal(double x, double z, int minX, int maxX, int minZ, int maxZ) {
        double loX = (double)minX + 0.3;
        double hiX = (double)maxX + 0.7;
        double loZ = (double)minZ + 0.3;
        double hiZ = (double)maxZ + 0.7;
        if (loX > hiX) {
            loX = hiX = (double)(minX + maxX + 1) / 2.0;
        }
        if (loZ > hiZ) {
            loZ = hiZ = (double)(minZ + maxZ + 1) / 2.0;
        }
        return new double[]{Math.min(hiX, Math.max(loX, x)), Math.min(hiZ, Math.max(loZ, z))};
    }

    public Cuboid expand(int amount) {
        return new Cuboid(this.worldName, this.minX - amount, this.minY - amount, this.minZ - amount, this.maxX + amount, this.maxY + amount, this.maxZ + amount);
    }

    public Location minLocation() {
        return new Location(this.resolveWorldOrNull(), (double)this.minX, (double)this.minY, (double)this.minZ);
    }

    public Location maxLocation() {
        return new Location(this.resolveWorldOrNull(), (double)this.maxX, (double)this.maxY, (double)this.maxZ);
    }

    public Location center() {
        World world = this.world();
        double x = (double)this.minX + (double)(this.maxX - this.minX) / 2.0 + 0.5;
        double y = (double)this.minY + (double)(this.maxY - this.minY) / 2.0;
        double z = (double)this.minZ + (double)(this.maxZ - this.minZ) / 2.0 + 0.5;
        return new Location(world, x, y, z);
    }

    public World world() {
        return Bukkit.getWorld((String)this.worldName);
    }

    private World resolveWorldOrNull() {
        return this.world();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Cuboid)) {
            return false;
        }
        Cuboid other = (Cuboid)o;
        return this.minX == other.minX && this.minY == other.minY && this.minZ == other.minZ && this.maxX == other.maxX && this.maxY == other.maxY && this.maxZ == other.maxZ && this.worldName.equals(other.worldName);
    }

    public int hashCode() {
        return Objects.hash(this.worldName, this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    public String toString() {
        return "Cuboid{world=" + this.worldName + ", min=(" + this.minX + "," + this.minY + "," + this.minZ + "), max=(" + this.maxX + "," + this.maxY + "," + this.maxZ + ")}";
    }
}
