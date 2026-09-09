package com.rumilance.practice.practice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Budget-bounded A* grid pathfinder for combat bots — the same steering model herobot's
 * PathFinder uses: the bot stands on a column when the block above the floor is free for
 * two blocks of headroom, and each move is a walk, a 1-block jump-up, a diagonal, or a
 * drop of up to three blocks. All units are block coordinates (feet position).
 *
 * <p>World access goes through {@link Passable} so the engine is pure and unit-testable.
 */
public final class BotPathFinder {

    /** Whether the bot's feet at (x,y,z) stand on support with 2 blocks of headroom. */
    @FunctionalInterface
    public interface Passable {
        boolean standable(int x, int y, int z);

        /** Whether the block at (x,y,z) is solid (used only for jump headroom checks;
         *  test grids may leave the default). */
        default boolean solid(int x, int y, int z) {
            return false;
        }
    }

    /** One waypoint: feet block coordinates. */
    public record Node(int x, int y, int z) {
        long key() {
            return ((long) x + 100000L) * 1_000_000_000_000L
                    + ((long) z + 100000L) * 1_000_000L
                    + (long) (y + 512);
        }
    }

    private BotPathFinder() {
    }

    /** Candidate neighbour offsets: dx,dz,dy of the FOOT for walk(-0), jump(+1), drop(-1..-3). */
    private static final int[][] STEP_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int MAX_DROP = 3;

    /**
     * Finds a path from start to goal (both FEET positions). Returns the waypoints after the
     * start point, or an empty list when no path fits inside {@code maxExpand} closed nodes
     * or when the goal column itself is not standable.
     */
    public static List<Node> find(Passable world, Node start, Node goal, int maxExpand) {
        if (world == null || start == null || goal == null || maxExpand <= 0) {
            return List.of();
        }
        if (!world.standable(goal.x(), goal.y(), goal.z())) {
            return List.of();
        }
        record Op(int fx, int fy, int fz, double g, double f) {
        }
        PriorityQueue<double[]> open = new PriorityQueue<>(java.util.Comparator.comparingDouble(a -> a[3]));
        Map<Long, Double> gScores = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        // x,y,z packed into the array: [x, y, z, f, g]
        open.add(new double[]{start.x(), start.y(), start.z(), heuristic(start, goal), 0});
        gScores.put(start.key(), 0.0d);
        int expanded = 0;
        long goalKey = goal.key();
        while (!open.isEmpty() && expanded < maxExpand) {
            double[] cur = open.poll();
            int cx = (int) cur[0];
            int cy = (int) cur[1];
            int cz = (int) cur[2];
            double g = cur[4];
            long cKey = ((long) cx + 100000L) * 1_000_000_000_000L
                    + ((long) cz + 100000L) * 1_000_000L + (long) (cy + 512);
            if (cKey == goalKey) {
                return reconstruct(parent, start.key(), goalKey);
            }
            Double best = gScores.get(cKey);
            if (best == null || g > best + 1e-9) {
                continue; // stale queue entry
            }
            expanded++;
            for (int[] d : STEP_DIRS) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                // diagonal requires both orthogonal around corners (no corner cutting)
                if (d[0] != 0 && d[1] != 0) {
                    if (!world.standable(cx + d[0], cy, cz) && !world.standable(cx + d[0], cy + 1, cz)) {
                        continue;
                    }
                    if (!world.standable(cx, cy, cz + d[1]) && !world.standable(cx, cy + 1, cz + d[1])) {
                        continue;
                    }
                }
                double stepCost = (d[0] != 0 && d[1] != 0) ? 1.4d : 1.0d;
                // preferred: same level
                if (offer(world, nx, cy, nz)) {
                    relax(open, gScores, parent, cKey, nx, cy, nz, g + stepCost, goal);
                    continue;
                }
                // jump up one: the target tile is standable one higher AND there is free
                // headroom above the bot's head in its current column (block at cy+2 clear).
                if (offer(world, nx, cy + 1, nz) && !world.solid(cx, cy + 2, cz)) {
                    relax(open, gScores, parent, cKey, nx, cy + 1, nz, g + stepCost + 0.3d, goal);
                    continue;
                }
                // drops up to MAX_DROP
                for (int drop = 1; drop <= MAX_DROP; drop++) {
                    if (offer(world, nx, cy - drop, nz)) {
                        relax(open, gScores, parent, cKey, nx, cy - drop, nz,
                                g + stepCost + drop * 0.2d, goal);
                        break;
                    }
                }
            }
        }
        return List.of();
    }

    private static boolean offer(Passable world, int x, int y, int z) {
        return world.standable(x, y, z);
    }

    private static void relax(PriorityQueue<double[]> open, Map<Long, Double> gScores,
                              Map<Long, Long> parent, long parentKey,
                              int x, int y, int z, double g, Node goal) {
        long key = ((long) x + 100000L) * 1_000_000_000_000L
                + ((long) z + 100000L) * 1_000_000L + (long) (y + 512);
        Double old = gScores.get(key);
        if (old != null && g >= old) {
            return;
        }
        gScores.put(key, g);
        parent.put(key, parentKey);
        open.add(new double[]{x, y, z, g + heuristic(new Node(x, y, z), goal), g});
    }

    private static double heuristic(Node a, Node b) {
        double dx = Math.abs(a.x() - b.x());
        double dz = Math.abs(a.z() - b.z());
        double dy = Math.abs(a.y() - b.y());
        return Math.max(dx, dz) + dy * 0.5d; // octile-ish, slightly conservative
    }

    private static List<Node> reconstruct(Map<Long, Long> parent, long startKey, long goalKey) {
        ArrayDeque<Node> rev = new ArrayDeque<>();
        long key = goalKey;
        while (key != startKey) {
            int x = (int) (key / 1_000_000_000_000L) - 100000;
            int z = (int) ((key / 1_000_000L) % 1_000_000L) - 100000;
            int y = (int) (key % 1_000_000L) - 512;
            rev.push(new Node(x, y, z));
            Long prev = parent.get(key);
            if (prev == null) {
                return List.of();
            }
            key = prev;
        }
        return new ArrayList<>(rev);
    }
}
