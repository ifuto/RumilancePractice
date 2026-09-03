package com.rumilance.practice.ffa;

import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.TickHealth;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Indexes grass standing spots for FFA arenas off the main thread.
 * Snapshots are taken on the server thread; scanning uses {@link ChunkSnapshot} which is
 * documented as a thread-safe read-only copy.
 */
public final class FfaSpawnIndex implements Listener {

    private static final int LOADS_PER_TICK = 4;
    private static final int MAX_IN_FLIGHT = 16;

    record Spot(int x, int y, int z) {
    }

    private record Warmup(String arenaId, String worldName, int chunkX, int chunkZ) {
    }

    /** A chunk whose terrain changed in-fight; its indexed spots need a refresh. */
    private record DirtyChunk(String arenaId, String worldName, int chunkX, int chunkZ) {
    }

    private final Plugin plugin;
    private final AsyncExecutor asyncExecutor;
    private final FfaService ffaService;
    private final Map<String, ConcurrentHashMap<Long, List<Spot>>> byArena = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Warmup> warmup = new ConcurrentLinkedQueue<>();
    private final java.util.Set<DirtyChunk> dirtySet = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<DirtyChunk> dirtyQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean tickerStarted = new AtomicBoolean();
    private final AtomicInteger inFlight = new AtomicInteger();

    public FfaSpawnIndex(Plugin plugin, AsyncExecutor asyncExecutor, FfaService ffaService) {
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.ffaService = ffaService;
    }

    public void reindexAll() {
        byArena.clear();
        warmup.clear();
        for (FfaService.FfaArena arena : ffaService.list()) {
            enqueueRegion(arena);
        }
        ensureTicker();
    }

    public void reindex(FfaService.FfaArena arena) {
        if (arena == null) {
            return;
        }
        byArena.remove(arena.id());
        enqueueRegion(arena);
        ensureTicker();
    }

    public void remove(String arenaId) {
        if (arenaId != null) {
            byArena.remove(arenaId.toLowerCase());
        }
    }

    public Location pick(FfaService.FfaArena arena, List<Location> occupied) {
        if (arena == null) {
            return null;
        }
        World world = arena.region().world();
        ConcurrentHashMap<Long, List<Spot>> chunks = byArena.get(arena.id());
        if (world == null || chunks == null || chunks.isEmpty()) {
            return null;
        }
        // The index is a point-in-time snapshot: explosions / placed blocks inside the arena
        // invalidate spots until the chunk is re-captured. Validate every candidate against
        // the LIVE world and evict stale entries, so no one spawns floating over a blast
        // crater or buried under a player-built tower.
        for (int attempt = 0; attempt < 6; attempt++) {
            Location picked = pickOnce(arena, world, chunks, occupied);
            if (picked == null) {
                return null;
            }
            Spot spot = new Spot(picked.getBlockX(), picked.getBlockY(), picked.getBlockZ());
            if (isLiveValid(world, spot)) {
                return picked;
            }
            evictSpot(arena.id(), spot);
        }
        return null;
    }

    private Location pickOnce(FfaService.FfaArena arena, World world,
                              ConcurrentHashMap<Long, List<Spot>> chunks, List<Location> occupied) {
        List<Spot> loaded = null;
        int n = 0;
        for (Map.Entry<Long, List<Spot>> entry : chunks.entrySet()) {
            long key = entry.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (!world.isChunkLoaded(cx, cz)) {
                continue;
            }
            List<Spot> spots = entry.getValue();
            if (spots == null || spots.isEmpty()) {
                continue;
            }
            if (loaded == null) {
                loaded = new ArrayList<>(spots.size() * Math.max(1, chunks.size()));
            }
            loaded.addAll(spots);
            n += spots.size();
        }
        if (loaded == null || n == 0) {
            return null;
        }
        int[] xs = new int[n];
        int[] zs = new int[n];
        for (int i = 0; i < n; i++) {
            Spot spot = loaded.get(i);
            xs[i] = spot.x();
            zs[i] = spot.z();
        }
        int[] occX = new int[occupied == null ? 0 : occupied.size()];
        int[] occZ = new int[occX.length];
        int occN = 0;
        if (occupied != null) {
            for (Location loc : occupied) {
                if (loc == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
                    continue;
                }
                occX[occN] = loc.getBlockX();
                occZ[occN] = loc.getBlockZ();
                occN++;
            }
        }
        int[] trimmedX = new int[occN];
        int[] trimmedZ = new int[occN];
        System.arraycopy(occX, 0, trimmedX, 0, occN);
        System.arraycopy(occZ, 0, trimmedZ, 0, occN);
        int pick = FfaSpawnMath.pickIndex(
                n, xs, zs, trimmedX, trimmedZ, FfaSpawnLocator.MIN_DISTANCE * FfaSpawnLocator.MIN_DISTANCE,
                ThreadLocalRandom.current());
        if (pick < 0) {
            return null;
        }
        Spot spot = loaded.get(pick);
        return new Location(world, spot.x() + 0.5d, spot.y(), spot.z() + 0.5d,
                ThreadLocalRandom.current().nextFloat() * 360.0f, 0f);
    }

    /** Live re-check of an indexed spot: grass below, safe passable feet/head, inside region. */
    static boolean isLiveValid(World world, Spot spot) {
        if (world == null || spot == null) {
            return false;
        }
        int x = spot.x();
        int feetY = spot.y();
        int z = spot.z();
        if (feetY <= world.getMinHeight() + 1 || feetY >= world.getMaxHeight() - 2) {
            return false;
        }
        if (!FfaSpawnMath.isSpawnGround(world.getBlockAt(x, feetY - 1, z).getType().name())) {
            return false;
        }
        String feet = world.getBlockAt(x, feetY, z).getType().name();
        if (FfaSpawnMath.isUnsafeFeet(feet) || !FfaSpawnMath.isPassableSpawnFeet(feet)) {
            return false;
        }
        org.bukkit.block.Block head = world.getBlockAt(x, feetY + 1, z);
        if (!head.isPassable() || head.isLiquid()) {
            return false;
        }
        return true;
    }

    private void evictSpot(String arenaId, Spot spot) {
        ConcurrentHashMap<Long, List<Spot>> chunks = byArena.get(arenaId);
        if (chunks == null) {
            return;
        }
        long key = chunkKey(spot.x() >> 4, spot.z() >> 4);
        chunks.computeIfPresent(key, (k, spots) -> {
            if (spots.stream().noneMatch(s -> s.x() == spot.x() && s.z() == spot.z())) {
                return spots;
            }
            List<Spot> filtered = new ArrayList<>(spots.size());
            for (Spot s : spots) {
                if (s.x() != spot.x() || s.z() != spot.z()) {
                    filtered.add(s);
                }
            }
            return List.copyOf(filtered);
        });
    }

    /**
     * Terrain inside the arena changed at this block (place/break/explosion): mark the chunk
     * for re-capture so indexed grass spots do not go stale mid-fight.
     */
    public void markDirty(String arenaId, org.bukkit.Location at) {
        if (arenaId == null || at == null || at.getWorld() == null) {
            return;
        }
        if (!byArena.containsKey(arenaId)) {
            return;
        }
        DirtyChunk dirty = new DirtyChunk(arenaId, at.getWorld().getName(),
                at.getBlockX() >> 4, at.getBlockZ() >> 4);
        if (dirtySet.add(dirty)) {
            dirtyQueue.add(dirty);
            ensureTicker();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (FfaService.FfaArena arena : ffaService.arenasView()) {
            if (overlaps(arena.region(), chunk)) {
                capture(arena.id(), chunk);
            }
        }
    }

    private void enqueueRegion(FfaService.FfaArena arena) {
        Cuboid region = arena.region();
        if (region == null) {
            return;
        }
        int minCx = region.minX() >> 4;
        int maxCx = region.maxX() >> 4;
        int minCz = region.minZ() >> 4;
        int maxCz = region.maxZ() >> 4;
        String worldName = region.worldName();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                warmup.add(new Warmup(arena.id(), worldName, cx, cz));
            }
        }
    }

    private void ensureTicker() {
        if (!tickerStarted.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::pumpWarmup, 1L, 1L);
    }

    private void pumpWarmup() {
        if (TickHealth.lagging()) {
            return;
        }
        pumpDirty();
        int budget = LOADS_PER_TICK;
        while (budget-- > 0) {
            Warmup job = warmup.poll();
            if (job == null) {
                return;
            }
            World world = plugin.getServer().getWorld(job.worldName());
            if (world == null) {
                continue;
            }
            if (world.isChunkLoaded(job.chunkX(), job.chunkZ())) {
                capture(job.arenaId(), world.getChunkAt(job.chunkX(), job.chunkZ()));
                continue;
            }
            if (inFlight.get() >= MAX_IN_FLIGHT) {
                warmup.add(job);
                return;
            }
            inFlight.incrementAndGet();
            world.getChunkAtAsync(job.chunkX(), job.chunkZ(), false).whenComplete((chunk, error) -> {
                inFlight.decrementAndGet();
                if (chunk == null) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> capture(job.arenaId(), chunk));
            });
        }
    }

    /** Re-captures chunks whose terrain changed so indexed grass spots do not go stale. */
    private void pumpDirty() {
        int budget = LOADS_PER_TICK;
        while (budget-- > 0) {
            DirtyChunk job = dirtyQueue.poll();
            if (job == null) {
                return;
            }
            dirtySet.remove(job);
            if (!byArena.containsKey(job.arenaId())) {
                continue;
            }
            World world = plugin.getServer().getWorld(job.worldName());
            if (world == null || !world.isChunkLoaded(job.chunkX(), job.chunkZ())) {
                // Unloaded chunks re-index via the ChunkLoadEvent path anyway.
                continue;
            }
            capture(job.arenaId(), world.getChunkAt(job.chunkX(), job.chunkZ()));
        }
    }

    private void capture(String arenaId, Chunk chunk) {
        if (chunk == null || arenaId == null) {
            return;
        }
        FfaService.FfaArena arena = ffaService.get(arenaId).orElse(null);
        if (arena == null || !overlaps(arena.region(), chunk)) {
            return;
        }
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(true, false, false);
        Cuboid region = arena.region();
        int worldMin = chunk.getWorld().getMinHeight();
        int worldMax = chunk.getWorld().getMaxHeight() - 1;
        long key = chunkKey(chunk.getX(), chunk.getZ());
        asyncExecutor.execute(() -> {
            List<Spot> spots = scanSnapshot(snapshot, region, worldMin, worldMax);
            byArena.computeIfAbsent(arenaId, id -> new ConcurrentHashMap<>()).put(key, List.copyOf(spots));
        });
    }

    static List<Spot> scanSnapshot(ChunkSnapshot snap, Cuboid region, int worldMin, int worldMax) {
        List<Spot> spots = new ArrayList<>();
        if (snap == null || region == null) {
            return spots;
        }
        int baseX = snap.getX() << 4;
        int baseZ = snap.getZ() << 4;
        int minY = region.minY();
        int maxY = region.maxY();
        for (int lx = 0; lx < 16; lx++) {
            int x = baseX + lx;
            if (x < region.minX() || x > region.maxX()) {
                continue;
            }
            for (int lz = 0; lz < 16; lz++) {
                int z = baseZ + lz;
                if (z < region.minZ() || z > region.maxZ()) {
                    continue;
                }
                final int localX = lx;
                final int localZ = lz;
                int top = Math.min(maxY, snap.getHighestBlockYAt(localX, localZ));
                int feetY = FfaSpawnMath.findGrassFeetY(minY, top, y -> {
                    if (y < worldMin || y > worldMax) {
                        return "VOID_AIR";
                    }
                    return snap.getBlockType(localX, y, localZ).name();
                });
                if (feetY != Integer.MIN_VALUE) {
                    spots.add(new Spot(x, feetY, z));
                }
            }
        }
        return spots;
    }

    static boolean overlaps(Cuboid region, Chunk chunk) {
        if (region == null || chunk == null) {
            return false;
        }
        if (region.worldName() != null && chunk.getWorld() != null
                && !region.worldName().equals(chunk.getWorld().getName())) {
            return false;
        }
        int minCx = region.minX() >> 4;
        int maxCx = region.maxX() >> 4;
        int minCz = region.minZ() >> 4;
        int maxCz = region.maxZ() >> 4;
        int cx = chunk.getX();
        int cz = chunk.getZ();
        return cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz;
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
