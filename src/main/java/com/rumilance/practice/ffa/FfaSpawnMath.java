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

    /**
     * Footing-repair probe trail: starting at a (possibly stale) clamped point, step in
     * 2-block strides toward the region centre until the centre is reached or
     * {@code maxProbes} cells were emitted. The first cell is the clamp point itself.
     * Pure math so the walk order is pinned by unit tests; the Bukkit glue re-resolves
     * the standing height in each emitted column.
     */
    public static int[][] inwardCells(int startX, int startZ, int centerX, int centerZ, int maxProbes) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int x = startX;
        int z = startZ;
        out.add(new int[]{x, z});
        for (int i = 1; i < maxProbes && (x != centerX || z != centerZ); i++) {
            int dx = Integer.compare(centerX, x);
            int dz = Integer.compare(centerZ, z);
            // Advance the axis with the larger remaining distance first (Manhattan trail).
            if (Math.abs(centerX - x) >= Math.abs(centerZ - z)) {
                x += dx * 2;
            } else {
                z += dz * 2;
            }
            out.add(new int[]{x, z});
        }
        return out.toArray(new int[0][]);
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
     * Highest standable-surface Y (feet) in a column, or {@link Integer#MIN_VALUE} if none.
     * {@code typeAtY} must accept {@code minY..maxY+1} (head block).
     *
     * <p>The surface is any whitelisted ground block — not only grass — so arenas floored
     * with stone, sand, planks, terracotta etc. still produce indexed spawn spots.</p>
     */
    public static int findGrassFeetY(int minY, int maxY, java.util.function.IntFunction<String> typeAtY) {
        if (typeAtY == null || maxY < minY) {
            return Integer.MIN_VALUE;
        }
        for (int y = maxY; y >= minY; y--) {
            if (!isSpawnGround(typeAtY.apply(y))) {
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

    /** True when {@code materialName} is a solid, safe surface a player can stand on at spawn. */
    public static boolean isSpawnGround(String materialName) {
        if (materialName == null) {
            return false;
        }
        return switch (materialName) {
            case "GRASS_BLOCK", "DIRT", "COARSE_DIRT", "ROOTED_DIRT", "PODZOL", "MYCELIUM",
                    "MUD", "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "ANDESITE", "DIORITE",
                    "GRANITE", "DEEPSLATE", "COBBLED_DEEPSLATE", "TUFF", "CALCITE",
                    "DRIPSTONE_BLOCK", "GRAVEL", "SAND", "RED_SAND", "CLAY", "NETHERRACK",
                    "BASALT", "SMOOTH_BASALT", "BLACKSTONE", "END_STONE", "OBSIDIAN",
                    "STONE_BRICKS", "MOSSY_STONE_BRICKS", "DEEPSLATE_BRICKS", "BRICKS",
                    "NETHER_BRICKS", "RED_NETHER_BRICKS", "SANDSTONE", "RED_SANDSTONE",
                    "CUT_SANDSTONE", "SMOOTH_SANDSTONE", "SMOOTH_RED_SANDSTONE",
                    "PRISMARINE", "PRISMARINE_BRICKS", "DARK_PRISMARINE",
                    "QUARTZ_BLOCK", "SMOOTH_QUARTZ", "SNOW_BLOCK", "PACKED_ICE", "BLUE_ICE",
                    "HONEYCOMB_BLOCK", "AMETHYST_BLOCK", "COPPER_BLOCK", "BEDROCK",
                    "SMOOTH_STONE", "PACKED_MUD", "SCULK", "GLASS", "TINTED_GLASS",
                    "PURPUR_BLOCK", "END_STONE_BRICKS", "WHITE_WOOL" -> true;
            default -> materialName.endsWith("_PLANKS")
                    || materialName.endsWith("_CONCRETE")
                    || materialName.endsWith("_TERRACOTTA")
                    || materialName.endsWith("_WOOL")
                    || materialName.endsWith("_LOG")
                    || materialName.startsWith("POLISHED_")
                    || materialName.startsWith("SMOOTH_");
        };
    }

    public static boolean isPassableSpawnFeet(String materialName) {
        if (materialName == null) {
            return false;
        }
        return switch (materialName) {
            case "AIR", "CAVE_AIR", "VOID_AIR", "LIGHT", "STRUCTURE_VOID",
                    "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN",
                    "DEAD_BUSH", "SNOW", "MOSS_CARPET", "PINK_PETALS" -> true;
            default -> false;
        };
    }

    /**
     * Minimum number of solid-ish samples (out of 16) a genuinely floored column shows.
     * Below this, the candidate is a wall sheet top / tree canopy / pillar — not a floor.
     */
    public static final int FLOOR_SUPPORT_MIN = 6;

    /**
     * Guards against "spawn on a border wall top / floating lip": a real floor carries
     * support in its neighbourhood, a 1-wide shell does not. Callers sample the 8 block
     * columns around the candidate at the ground level (grass/snow counts), and the 8
     * columns one block deeper; {@code null} means "outside the loaded snapshot" and is
     * counted as supported (chunk-edge passthrough; full confidence needs no neighbour).
     */
    public static boolean hasFloorSupport(String[] groundLevel, String[] belowLevel) {
        int supported = 0;
        if (groundLevel != null) {
            for (String name : groundLevel) {
                if (!isVoidSample(name)) {
                    supported++;
                }
            }
        }
        if (belowLevel != null) {
            for (String name : belowLevel) {
                if (!isVoidSample(name)) {
                    supported++;
                }
            }
        }
        return supported >= FLOOR_SUPPORT_MIN;
    }

    /** "No support" sample: true-air or unknown treated as supported (null = passthrough). */
    static boolean isVoidSample(String materialName) {
        return "AIR".equals(materialName) || "CAVE_AIR".equals(materialName)
                || "VOID_AIR".equals(materialName);
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
