package com.rumilance.practice.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Column search around a landing point — the preventive half of the anti-bury work: instead of
 * teleporting a player into a block and pulling them out again, the landing is moved to a free
 * column <em>before</em> the move.
 *
 * <p>Pure integer maths (no Bukkit types) so the ordering is unit tested directly
 * ({@code LandingSearchTest}).</p>
 *
 * <p>The pearl case this exists for: a pearl thrown at a one-block-thick glass wall dies just
 * outside the wall, but the player is 0.6 blocks wide and 1.8 tall, so the hitbox still overlaps
 * the glass and vanilla drops the player inside it. The nearest free column is on the side the
 * pearl came from, which is also where a player expects to land — so the search is biased by
 * {@link #towards(double, double, double, double)}, the direction back to the thrower, and never
 * resolves through the wall.</p>
 */
public final class LandingSearch {

    private LandingSearch() {
    }

    /**
     * Columns to try around a landing, nearest ring first ({@code radius 1}, then {@code 2} …).
     * Inside one ring the columns are ordered by how directly they lie in the bias direction;
     * without a bias the order is purely geometric and therefore deterministic.
     *
     * @return {@code {dx, dz}} block-column offsets, never {@code {0, 0}} (the landing itself
     *         is always tried first by the caller)
     */
    public static List<int[]> nearbyOffsets(int radius, double biasX, double biasZ) {
        List<int[]> offsets = new ArrayList<>();
        int cap = Math.max(1, radius);
        for (int ring = 1; ring <= cap; ring++) {
            List<int[]> shell = new ArrayList<>();
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        shell.add(new int[]{dx, dz});
                    }
                }
            }
            shell.sort(comparator(biasX, biasZ));
            offsets.addAll(shell);
        }
        return offsets;
    }

    /**
     * Search direction back towards where a pearl was thrown from: the reverse of the travel
     * vector. Returns {@code {0, 0}} for a (near-)vertical throw, which degrades the search to
     * its deterministic geometric order.
     */
    public static double[] towards(double fromX, double fromZ, double toX, double toZ) {
        double dx = fromX - toX;
        double dz = fromZ - toZ;
        if (Math.hypot(dx, dz) < 1.0e-6d) {
            return new double[]{0.0d, 0.0d};
        }
        return new double[]{dx, dz};
    }

    private static Comparator<int[]> comparator(double biasX, double biasZ) {
        double bias = Math.hypot(biasX, biasZ);
        return (a, b) -> {
            if (bias > 1.0e-6d) {
                double ca = cosine(a, biasX, biasZ, bias);
                double cb = cosine(b, biasX, biasZ, bias);
                if (Math.abs(ca - cb) > 1.0e-9d) {
                    return Double.compare(cb, ca); // most aligned with the bias first
                }
            }
            int manhattanA = Math.abs(a[0]) + Math.abs(a[1]);
            int manhattanB = Math.abs(b[0]) + Math.abs(b[1]);
            if (manhattanA != manhattanB) {
                return Integer.compare(manhattanA, manhattanB);
            }
            if (a[0] != b[0]) {
                return Integer.compare(a[0], b[0]);
            }
            return Integer.compare(a[1], b[1]);
        };
    }

    private static double cosine(int[] offset, double biasX, double biasZ, double bias) {
        double length = Math.hypot(offset[0], offset[1]);
        return (offset[0] * biasX + offset[1] * biasZ) / (length * bias);
    }
}
