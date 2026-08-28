package com.rumilance.practice.util;

import org.bukkit.Location;

/**
 * Soft border push: one block inward from the cuboid edge (never snap to center).
 */
public final class BoundsNudge {

    private BoundsNudge() {
    }

    /**
     * Clamps {@code outside} onto the horizontal border, then nudges ~1 block toward the
     * region interior along the nearest axis. Yaw/pitch/Y are preserved from {@code outside}.
     */
    public static Location nudgeInward(Cuboid region, Location outside) {
        if (region == null || outside == null) {
            return outside;
        }
        // Use block XZ — Location.containsHorizontal requires a loaded World.
        if (region.containsHorizontal(outside.getBlockX(), outside.getBlockZ())) {
            return outside.clone();
        }
        double[] xz = Cuboid.clampHorizontal(
                outside.getX(), outside.getZ(),
                region.minX(), region.maxX(), region.minZ(), region.maxZ());
        double x = xz[0];
        double z = xz[1];
        double loX = region.minX() + 0.3d;
        double hiX = region.maxX() + 0.7d;
        double loZ = region.minZ() + 0.3d;
        double hiZ = region.maxZ() + 0.7d;
        double distLoX = Math.abs(x - loX);
        double distHiX = Math.abs(x - hiX);
        double distLoZ = Math.abs(z - loZ);
        double distHiZ = Math.abs(z - hiZ);
        double min = Math.min(Math.min(distLoX, distHiX), Math.min(distLoZ, distHiZ));
        if (distHiX <= min + 1.0e-9d) {
            x = Math.max(loX, hiX - 1.0d);
        } else if (distLoX <= min + 1.0e-9d) {
            x = Math.min(hiX, loX + 1.0d);
        } else if (distHiZ <= min + 1.0e-9d) {
            z = Math.max(loZ, hiZ - 1.0d);
        } else {
            z = Math.min(hiZ, loZ + 1.0d);
        }
        double[] inside = Cuboid.clampHorizontal(
                x, z, region.minX(), region.maxX(), region.minZ(), region.maxZ());
        Location result = outside.clone();
        result.setX(inside[0]);
        result.setZ(inside[1]);
        return result;
    }
}
