package com.rumilance.practice.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/**
 * Helpers for converting {@link Location} instances to/from a compact, human-readable
 * string form suitable for storage in YAML configuration files, plus WorldBorder-safe
 * teleport targets.
 */
public final class LocationUtil {

    private static final String DELIMITER = ";";
    /** Blocks kept inside the absolute border so vanilla does not bounce the player back. */
    private static final double DEFAULT_BORDER_MARGIN = 2.0d;

    private LocationUtil() {
    }

    /**
     * Serializes to {@code world;x;y;z;yaw;pitch} using {@link Locale#ROOT} number formatting.
     */
    public static String serialize(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        String worldName = world != null ? world.getName() : "world";
        return String.join(
                DELIMITER,
                worldName,
                String.format(Locale.ROOT, "%.4f", location.getX()),
                String.format(Locale.ROOT, "%.4f", location.getY()),
                String.format(Locale.ROOT, "%.4f", location.getZ()),
                String.format(Locale.ROOT, "%.4f", location.getYaw()),
                String.format(Locale.ROOT, "%.4f", location.getPitch())
        );
    }

    /**
     * Parses a string produced by {@link #serialize(Location)}. The world does not need to be
     * loaded; if it is not, the resulting {@link Location} will have a {@code null} world.
     */
    public static Location deserialize(String serialized) {
        Objects.requireNonNull(serialized, "serialized");
        String[] parts = serialized.split(DELIMITER);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Malformed serialized location: " + serialized);
        }
        World world = Bukkit.getWorld(parts[0]);
        double x = Double.parseDouble(parts[1]);
        double y = Double.parseDouble(parts[2]);
        double z = Double.parseDouble(parts[3]);
        float yaw = Float.parseFloat(parts[4]);
        float pitch = Float.parseFloat(parts[5]);
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * @return a copy of {@code location} shifted to the horizontal center of its block.
     */
    public static Location blockCenter(Location location) {
        Objects.requireNonNull(location, "location");
        Location copy = location.clone();
        copy.setX(location.getBlockX() + 0.5d);
        copy.setZ(location.getBlockZ() + 0.5d);
        return copy;
    }

    public static double distanceSquaredIgnoringY(Location a, Location b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Ensures a teleport target is strictly inside the effective WorldBorder for {@code viewer}
     * (per-player border if set, otherwise the world's border). Prevents the vanilla
     * "flash outside then pull back" when a configured spawn sits on/outside the border.
     */
    public static Location safeTeleportLocation(Location desired, Player viewer) {
        Objects.requireNonNull(desired, "desired");
        Location loc = desired.clone();
        if (loc.getWorld() == null && viewer != null) {
            loc.setWorld(viewer.getWorld());
        }
        World world = loc.getWorld();
        if (world == null) {
            return loc;
        }
        WorldBorder border = resolveBorder(world, viewer);
        return clampInsideWorldBorder(loc, border, DEFAULT_BORDER_MARGIN);
    }

    public static Location safeTeleportLocation(Location desired) {
        return safeTeleportLocation(desired, null);
    }

    /**
     * Clamps a teleport destination horizontally INSIDE the given arena region instead of the
     * world border. The per-player arena border is built from this region, so clamping against
     * the (often much smaller) world border can otherwise push the destination outside the
     * arena walls — the classic "teleported outside the border" FFA bug.
     */
    public static Location safeTeleportLocation(Location desired, Cuboid region) {
        Objects.requireNonNull(desired, "desired");
        if (region == null) {
            return safeTeleportLocation(desired);
        }
        return region.clampHorizontal(desired);
    }

    public static boolean isInsideWorldBorder(Location location, Player viewer) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        WorldBorder border = resolveBorder(location.getWorld(), viewer);
        return border.isInside(location);
    }

    static Location clampInsideWorldBorder(Location location, WorldBorder border, double marginBlocks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(border, "border");
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2.0d;
        boolean inside = border.isInside(location);
        double[] xz = clampXZ(location.getX(), location.getZ(), center.getX(), center.getZ(), halfSize, marginBlocks, inside);
        Location fixed = location.clone();
        fixed.setX(xz[0]);
        fixed.setZ(xz[1]);
        return fixed;
    }

    /**
     * Pure X/Z clamp used by {@link #clampInsideWorldBorder}. {@code halfSize} is half of
     * {@link WorldBorder#getSize()} (i.e. distance from center to an absolute edge).
     */
    static double[] clampXZ(
            double x,
            double z,
            double centerX,
            double centerZ,
            double halfSize,
            double marginBlocks,
            boolean currentlyInside
    ) {
        double dx = Math.abs(x - centerX);
        double dz = Math.abs(z - centerZ);
        double edge = halfSize - 1.0d;
        boolean needsClamp = !currentlyInside || (edge > 0 && (dx >= edge || dz >= edge));
        if (!needsClamp) {
            return new double[]{x, z};
        }
        double effectiveMargin = currentlyInside ? Math.min(marginBlocks, 1.5d) : Math.max(1.0d, marginBlocks);
        double half = halfSize - effectiveMargin;
        if (half < 1.0d) {
            half = Math.max(0.5d, halfSize - 0.5d);
        }
        return new double[]{
                clamp(x, centerX - half, centerX + half),
                clamp(z, centerZ - half, centerZ + half)
        };
    }

    private static WorldBorder resolveBorder(World world, Player viewer) {
        if (viewer != null) {
            WorldBorder personal = viewer.getWorldBorder();
            if (personal != null) {
                return personal;
            }
        }
        return world.getWorldBorder();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
