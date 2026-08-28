package com.rumilance.practice.ffa;

import java.util.random.RandomGenerator;

/**
 * Pure spawn-selection math for FFA: pick a grass standing spot that is horizontally
 * far from other occupants. No Bukkit types so unit tests do not need a server.
 */
public final class FfaSpawnMath {

    private FfaSpawnMath() {
    }

    /**
     * @param count number of candidate XZ pairs in {@code cx}/{@code cz}
     * @param minDistSq minimum horizontal distance squared from any occupant
     * @return candidate index, or {@code -1} when {@code count} is 0
     */
    public static int pickIndex(
            int count,
            int[] cx,
            int[] cz,
            int[] occupantX,
            int[] occupantZ,
            int minDistSq,
            RandomGenerator rng
    ) {
        if (count <= 0) {
            return -1;
        }
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        for (int i = count - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        for (int idx : order) {
            if (farFromAll(cx[idx], cz[idx], occupantX, occupantZ, minDistSq)) {
                return idx;
            }
        }
        int best = 0;
        int bestScore = -1;
        for (int i = 0; i < count; i++) {
            int score = minDistSqToOccupied(cx[i], cz[i], occupantX, occupantZ);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    public static boolean farFromAll(int x, int z, int[] occupantX, int[] occupantZ, int minDistSq) {
        if (occupantX == null || occupantX.length == 0) {
            return true;
        }
        for (int i = 0; i < occupantX.length; i++) {
            int dx = x - occupantX[i];
            int dz = z - occupantZ[i];
            if (dx * dx + dz * dz < minDistSq) {
                return false;
            }
        }
        return true;
    }

    public static int minDistSqToOccupied(int x, int z, int[] occupantX, int[] occupantZ) {
        if (occupantX == null || occupantX.length == 0) {
            return Integer.MAX_VALUE;
        }
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < occupantX.length; i++) {
            int dx = x - occupantX[i];
            int dz = z - occupantZ[i];
            int d = dx * dx + dz * dz;
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    /**
     * Highest grass standing Y (feet) in a column, or {@link Integer#MIN_VALUE} if none.
     * {@code typeAtY} must accept {@code minY..maxY+1} (head block).
     */
    public static int findGrassFeetY(int minY, int maxY, java.util.function.IntFunction<String> typeAtY) {
        if (typeAtY == null || maxY < minY) {
            return Integer.MIN_VALUE;
        }
        for (int y = maxY; y >= minY; y--) {
            if (!"GRASS_BLOCK".equals(typeAtY.apply(y))) {
                continue;
            }
            int feetY = y + 1;
            if (feetY > maxY) {
                continue;
            }
            String feet = typeAtY.apply(feetY);
            String head = typeAtY.apply(feetY + 1);
            if (isUnsafeFeet(feet) || !isPassableSpawnFeet(feet) || !isPassableSpawnFeet(head)) {
                continue;
            }
            return feetY;
        }
        return Integer.MIN_VALUE;
    }

    public static boolean isPassableSpawnFeet(String materialName) {
        if (materialName == null) {
            return false;
        }
        return switch (materialName) {
            case "AIR", "CAVE_AIR", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN",
                    "DEAD_BUSH", "SNOW", "MOSS_CARPET", "PINK_PETALS" -> true;
            default -> false;
        };
    }

    public static boolean isUnsafeFeet(String materialName) {
        if (materialName == null) {
            return true;
        }
        return switch (materialName) {
            case "FIRE", "SOUL_FIRE", "LAVA", "WATER", "POWDER_SNOW",
                    "SWEET_BERRY_BUSH", "WITHER_ROSE", "CACTUS", "MAGMA_BLOCK" -> true;
            default -> false;
        };
    }
}
