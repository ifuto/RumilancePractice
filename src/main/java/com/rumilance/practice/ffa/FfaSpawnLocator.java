package com.rumilance.practice.ffa;

import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finds a standing location on any safe ground surface inside an FFA cuboid — grass,
 * stone, planks, terracotta etc. ({@link FfaSpawnMath#isSpawnGround}), since custom
 * arenas are rarely floored with grass alone. The fallback point also passes through
 * {@link com.rumilance.practice.util.SpawnFooting#standClear} so a stale configured
 * spawn can never come back raw (buried under a freshly pasted floor or floating
 * above a crater).
 * Samples loaded columns only so a large region does not hitch the main thread.
 */
public final class FfaSpawnLocator {

    private static final int SAMPLE_ATTEMPTS = 12;
    private static final int MAX_LOADED_SCANS = 8;
    static final int MIN_DISTANCE = 8;

    private FfaSpawnLocator() {
    }

    public static Location find(FfaService.FfaArena arena, List<Location> occupied) {
        World world = arena.region().world();
        Location configured = arena.spawn();
        Location fallback;
        if (configured != null) {
            fallback = LocationUtil.blockCenter(configured);
        } else if (world != null) {
            Cuboid region = arena.region();
            fallback = new Location(world,
                    (region.minX() + region.maxX()) * 0.5d + 0.5d,
                    region.minY() + 1.0d,
                    (region.minZ() + region.maxZ()) * 0.5d + 0.5d);
        } else {
            // No configured spawn and no world/region to scan: nothing safe to offer.
            if (configured == null && arena.region() != null && arena.region().world() != null) {
                Location centerish = new Location(arena.region().world(),
                        (arena.region().minX() + arena.region().maxX()) * 0.5d + 0.5d,
                        arena.region().minY() + 1.0d,
                        (arena.region().minZ() + arena.region().maxZ()) * 0.5d + 0.5d);
                return com.rumilance.practice.util.SpawnFooting.standClear(centerish);
            }
            return configured;
        }
        if (world == null) {
            randomYaw(fallback);
            return fallback;
        }
        if (fallback.getWorld() == null) {
            fallback.setWorld(world);
        }
        Cuboid region = arena.region();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Location> grass = new ArrayList<>(MAX_LOADED_SCANS);
        int loadedScans = 0;
        for (int attempt = 0; attempt < SAMPLE_ATTEMPTS && loadedScans < MAX_LOADED_SCANS; attempt++) {
            int x = rng.nextInt(region.minX(), region.maxX() + 1);
            int z = rng.nextInt(region.minZ(), region.maxZ() + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            loadedScans++;
            Location found = scanColumn(world, region, x, z);
            if (found != null) {
                grass.add(found);
            }
        }
        if (grass.isEmpty()) {
            Location nearSpawn = scanColumn(world, region, fallback.getBlockX(), fallback.getBlockZ());
            if (nearSpawn != null) {
                grass.add(nearSpawn);
            }
        }
        if (grass.isEmpty()) {
            Location clear = com.rumilance.practice.util.SpawnFooting.standClear(fallback);
            if (clear == null) {
                int minY = Math.max(world.getMinHeight(), region.minY());
                clear = com.rumilance.practice.util.SpawnFooting.standClearDeep(fallback, minY);
            }
            if (clear != null) {
                fallback = clear;
                fallback.setWorld(world);
            }
            randomYaw(fallback);
            return fallback;
        }
        int[] occX = new int[occupied.size()];
        int[] occZ = new int[occupied.size()];
        int n = 0;
        for (Location loc : occupied) {
            if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
                continue;
            }
            occX[n] = loc.getBlockX();
            occZ[n] = loc.getBlockZ();
            n++;
        }
        int[] trimmedX = new int[n];
        int[] trimmedZ = new int[n];
        System.arraycopy(occX, 0, trimmedX, 0, n);
        System.arraycopy(occZ, 0, trimmedZ, 0, n);

        int[] cx = new int[grass.size()];
        int[] cz = new int[grass.size()];
        for (int i = 0; i < grass.size(); i++) {
            cx[i] = grass.get(i).getBlockX();
            cz[i] = grass.get(i).getBlockZ();
        }
        int pick = FfaSpawnMath.pickIndex(
                grass.size(), cx, cz, trimmedX, trimmedZ, MIN_DISTANCE * MIN_DISTANCE, rng);
        Location chosen = pick < 0 ? null : grass.get(pick).clone();
        if (chosen == null) {
            chosen = com.rumilance.practice.util.SpawnFooting.standClear(fallback);
            if (chosen == null) {
                int minY = Math.max(world.getMinHeight(), region.minY());
                chosen = com.rumilance.practice.util.SpawnFooting.standClearDeep(fallback, minY);
            }
        }
        if (chosen == null) {
            chosen = fallback;
        }
        randomYaw(chosen);
        return chosen;
    }

    static Location scanColumn(World world, Cuboid region, int x, int z) {
        int maxY = region.maxY();
        int minY = region.minY();
        for (int y = maxY; y >= minY; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!FfaSpawnMath.isSpawnGround(ground.getType().name())) {
                continue;
            }
            int feetY = y + 1;
            if (feetY > maxY) {
                continue;
            }
            Block feet = world.getBlockAt(x, feetY, z);
            Block head = world.getBlockAt(x, feetY + 1, z);
            String feetName = feet.getType().name();
            if (FfaSpawnMath.isUnsafeFeet(feetName) || !FfaSpawnMath.isPassableSpawnFeet(feetName)) {
                continue;
            }
            if (!head.isPassable() || head.isLiquid()) {
                continue;
            }
            // Border-wall guard: top of a 1-wide shell is air-adjacent, floors are not.
            if (!floorSupport(world, x, y, z)) {
                continue;
            }
            return new Location(world, x + 0.5d, feetY, z + 0.5d, 0f, 0f);
        }
        return null;
    }

    /** 8-neighbour support probe shared with the index (wall tops fail, floors pass). */
    static boolean floorSupport(World world, int x, int groundY, int z) {
        String[] ground = new String[8];
        String[] below = new String[8];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block g = world.getBlockAt(x + dx, groundY, z + dz);
                Block b = world.getBlockAt(x + dx, groundY - 1, z + dz);
                ground[i] = g.getType().name();
                below[i] = b.getType().name();
                i++;
            }
        }
        return FfaSpawnMath.hasFloorSupport(ground, below);
    }

    private static void randomYaw(Location location) {
        if (location == null) {
            return;
        }
        location.setYaw(ThreadLocalRandom.current().nextFloat() * 360.0f);
        location.setPitch(0f);
    }
}
