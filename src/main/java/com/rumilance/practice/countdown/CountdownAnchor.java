package com.rumilance.practice.countdown;

/**
 * Where the floating Ready / Leave blocks hang for one spawn pose — pure maths, no Bukkit
 * types, so the geometry is unit tested directly (see {@code CountdownAnchorTest}).
 *
 * <p>The pair is derived from the <em>teleport destination</em> (the arena spawn the fighters
 * were moved onto, including its yaw), never from the player's live position: the blocks are
 * part of the arena's start layout, so a fighter who is already standing somewhere inside the
 * arena still gets the pair in front of the spawn they are about to be pinned onto.</p>
 *
 * <p>Minecraft yaw increases clockwise (0 = south / +Z, 90 = west / −X), so the forward unit
 * vector is {@code (-sin yaw, 0, cos yaw)} and "one to the right" is that vector at
 * {@code yaw + 90°}.</p>
 */
public final class CountdownAnchor {

    /** How far in front of the spawn the pair floats. */
    public static final double FORWARD = 5.0;
    /** Half the gap between the two blocks. */
    public static final double SIDE = 2.0;
    /** Eye height of the blocks above the spawn's feet. */
    public static final double EYE = 1.55;

    private CountdownAnchor() {
    }

    /** Ready (emerald, right of the spawn) and Leave (redstone, left) block positions. */
    public record Pair(double readyX, double readyY, double readyZ,
                       double leaveX, double leaveY, double leaveZ) {
    }

    /** Both blocks for a spawn pose: same height, mirrored around the forward axis. */
    public static Pair pair(double x, double y, double z, float yawDegrees) {
        double[] forward = horizontal(yawDegrees);
        double[] right = horizontal(yawDegrees + 90.0f);
        double centreX = x + forward[0] * FORWARD;
        double centreZ = z + forward[1] * FORWARD;
        double height = y + EYE;
        return new Pair(
                centreX + right[0] * SIDE, height, centreZ + right[1] * SIDE,
                centreX - right[0] * SIDE, height, centreZ - right[1] * SIDE);
    }

    /** Horizontal unit vector for a yaw: {@code {-sin, cos}} in (x, z). */
    static double[] horizontal(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new double[]{-Math.sin(yaw), Math.cos(yaw)};
    }
}
