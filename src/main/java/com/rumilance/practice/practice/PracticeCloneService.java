package com.rumilance.practice.practice;

import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disposable practice-room copies: schematic save-once, paste at a free origin in the
 * same placement band as disposable arenas, remap spawn by delta, clear on leave.
 */
public final class PracticeCloneService {

    private static final Logger LOGGER = Logger.getLogger(PracticeCloneService.class.getName());
    private static final int MAX_PLACEMENT_ATTEMPTS = 60;

    private final Plugin plugin;
    private final FaweBridge faweBridge;
    private final File schematicRoot;
    private final int placementRange;
    private final int spacing;
    private final int centerX;
    private final int centerZ;

    private final Map<UUID, LiveCopy> liveCopies = new ConcurrentHashMap<>();
    /** Optional AABB supplier for arena live copies (minX,minZ,maxX,maxZ + world). */
    private volatile Supplier<List<OverlapBox>> externalOverlaps = List::of;
    private volatile boolean loggedFallback;

    public PracticeCloneService(Plugin plugin, FaweBridge faweBridge, File schematicRoot,
                                int placementRange, int spacing, int centerX, int centerZ) {
        this.plugin = plugin;
        this.faweBridge = faweBridge;
        this.schematicRoot = schematicRoot;
        this.placementRange = Math.max(256, placementRange);
        this.spacing = Math.max(16, spacing);
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public void setExternalOverlaps(Supplier<List<OverlapBox>> supplier) {
        this.externalOverlaps = supplier == null ? List::of : supplier;
    }

    public boolean isAvailable() {
        return faweBridge != null && faweBridge.isAvailable();
    }

    public void logFallbackOnce() {
        if (!loggedFallback) {
            loggedFallback = true;
            plugin.getLogger().info("[Practice] FAWE unavailable — practice rooms use shared teleport.");
        }
    }

    public Path schematicPath(String practiceId) {
        String safe = practiceId.replaceAll("[\\\\/:*?\"<>|]", "_");
        return new File(schematicRoot, "practice" + File.separator + safe + ".schem").toPath();
    }

    /**
     * Saves (or refreshes) the template schematic for a practice room. Call on create/update.
     */
    public CompletableFuture<Boolean> ensureSchematic(PracticeRoom room) {
        if (!isAvailable() || room == null) {
            return CompletableFuture.completedFuture(false);
        }
        World world = Bukkit.getWorld(room.world());
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        Cuboid region = room.region();
        Path out = schematicPath(room.id());
        return faweBridge.saveSchematic(world,
                region.minX(), region.minY(), region.minZ(),
                region.maxX(), region.maxY(), region.maxZ(),
                out);
    }

    /**
     * Pastes a disposable copy of {@code room}. {@code avoidRooms} lists template regions that
     * must not be stamped over (typically every configured practice room).
     */
    public CompletableFuture<Optional<PracticeCopy>> pasteCopy(PracticeRoom room,
                                                               java.util.Collection<PracticeRoom> avoidRooms) {
        if (!isAvailable() || room == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        World world = Bukkit.getWorld(room.world());
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Path schem = schematicPath(room.id());
        CompletableFuture<Boolean> ready;
        if (!Files.isRegularFile(schem)) {
            ready = ensureSchematic(room);
        } else {
            ready = CompletableFuture.completedFuture(true);
        }
        java.util.List<PracticeRoom> avoid = avoidRooms == null ? List.of() : List.copyOf(avoidRooms);
        return ready.thenCompose(ok -> {
            if (!Boolean.TRUE.equals(ok) || !Files.isRegularFile(schem)) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Cuboid source = room.region();
            Optional<int[]> originOpt = findFreeOrigin(source, avoid);
            if (originOpt.isEmpty()) {
                LOGGER.warning("[Practice] No free placement for practice copy of '" + room.id() + "'.");
                return CompletableFuture.completedFuture(Optional.empty());
            }
            int[] origin = originOpt.get();
            int pasteMinX = origin[0];
            int pasteMinY = source.minY();
            int pasteMinZ = origin[1];
            int dx = pasteMinX - source.minX();
            int dy = pasteMinY - source.minY();
            int dz = pasteMinZ - source.minZ();

            Cuboid pasted = Cuboid.of(source.worldName(),
                    pasteMinX, pasteMinY, pasteMinZ,
                    pasteMinX + (source.maxX() - source.minX()),
                    pasteMinY + (source.maxY() - source.minY()),
                    pasteMinZ + (source.maxZ() - source.minZ()));

            Location templateSpawn = LocationUtil.deserialize(room.serializedSpawn());
            if (templateSpawn.getWorld() == null) {
                templateSpawn.setWorld(world);
            }
            Location remappedSpawn = templateSpawn.clone().add(dx, dy, dz);
            remappedSpawn.setWorld(world);

            UUID instanceId = UUID.randomUUID();
            LiveCopy live = new LiveCopy(instanceId, room.id(), pasted);
            liveCopies.put(instanceId, live);

            Location anchor = new Location(world, pasteMinX, pasteMinY, pasteMinZ);
            return faweBridge.regenerate(schem, anchor).handle((success, err) -> {
                if (err != null || !Boolean.TRUE.equals(success)) {
                    liveCopies.remove(instanceId);
                    LOGGER.log(Level.WARNING, "[Practice] Failed to paste practice copy of '" + room.id() + "'", err);
                    return Optional.<PracticeCopy>empty();
                }
                return Optional.of(new PracticeCopy(instanceId, pasted, remappedSpawn));
            });
        });
    }

    public CompletableFuture<Optional<PracticeCopy>> pasteCopy(PracticeRoom room) {
        return pasteCopy(room, List.of(room));
    }

    /**
     * Clears the existing paste (or shared template region) and re-pastes the schematic
     * at the same origin. Spawn mapping is unchanged. Returns false on failure.
     */
    public CompletableFuture<Boolean> repaste(PracticeSession session, PracticeRoom room) {
        if (session == null || room == null || !isAvailable()) {
            return CompletableFuture.completedFuture(false);
        }
        Path schem = schematicPath(room.id());
        CompletableFuture<Boolean> ready;
        if (!Files.isRegularFile(schem)) {
            ready = ensureSchematic(room);
        } else {
            ready = CompletableFuture.completedFuture(true);
        }
        return ready.thenCompose(ok -> {
            if (!Boolean.TRUE.equals(ok) || !Files.isRegularFile(schem)) {
                return CompletableFuture.completedFuture(false);
            }
            Cuboid region;
            UUID instanceId = session.cloneInstanceId();
            if (instanceId != null) {
                LiveCopy live = liveCopies.get(instanceId);
                if (live == null) {
                    return CompletableFuture.completedFuture(false);
                }
                region = live.region();
            } else if (session.activeRegion() != null) {
                region = session.activeRegion();
            } else {
                region = room.region();
            }
            World world = Bukkit.getWorld(region.worldName());
            if (world == null) {
                return CompletableFuture.completedFuture(false);
            }
            Location anchor = new Location(world, region.minX(), region.minY(), region.minZ());
            return faweBridge.clearRegion(world,
                            region.minX(), region.minY(), region.minZ(),
                            region.maxX(), region.maxY(), region.maxZ())
                    .thenCompose(cleared -> {
                        if (!Boolean.TRUE.equals(cleared)) {
                            LOGGER.warning("[Practice] Failed to clear region before repaste of '"
                                    + room.id() + "'");
                            return CompletableFuture.completedFuture(false);
                        }
                        return faweBridge.regenerate(schem, anchor).handle((success, err) -> {
                            if (err != null || !Boolean.TRUE.equals(success)) {
                                LOGGER.log(Level.WARNING,
                                        "[Practice] Failed to re-paste practice room '" + room.id() + "'", err);
                                return false;
                            }
                            return true;
                        });
                    });
        });
    }

    /** Clears pasted blocks asynchronously and forgets the live slot. Safe if unknown id. */
    public CompletableFuture<Void> release(UUID instanceId) {
        if (instanceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        LiveCopy live = liveCopies.remove(instanceId);
        if (live == null || !isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        World world = Bukkit.getWorld(live.region().worldName());
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }
        Cuboid r = live.region();
        return faweBridge.clearRegion(world, r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ())
                .handle((ok, err) -> {
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        LOGGER.warning("[Practice] Failed to clear practice copy " + instanceId);
                    }
                    return null;
                });
    }

    public CompletableFuture<Void> releaseAll() {
        CompletableFuture<?>[] futures = liveCopies.keySet().stream()
                .map(this::release)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public List<LiveCopy> liveCopies() {
        return List.copyOf(liveCopies.values());
    }

    private Optional<int[]> findFreeOrigin(Cuboid source, java.util.Collection<PracticeRoom> avoidRooms) {
        int width = source.maxX() - source.minX() + 1;
        int depth = source.maxZ() - source.minZ() + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            int x = snap(centerX + random.nextInt(-placementRange, placementRange + 1));
            int z = snap(centerZ + random.nextInt(-placementRange, placementRange + 1));
            if (isFree(source.worldName(), x, z, width, depth, avoidRooms)) {
                return Optional.of(new int[]{x, z});
            }
        }
        return Optional.empty();
    }

    private boolean isFree(String worldName, int x, int z, int width, int depth,
                           java.util.Collection<PracticeRoom> avoidRooms) {
        int minX = x - spacing;
        int minZ = z - spacing;
        int maxX = x + width - 1 + spacing;
        int maxZ = z + depth - 1 + spacing;
        for (LiveCopy live : liveCopies.values()) {
            if (!live.region().worldName().equals(worldName)) {
                continue;
            }
            Cuboid r = live.region();
            if (intersects(minX, minZ, maxX, maxZ, r.minX(), r.minZ(), r.maxX(), r.maxZ())) {
                return false;
            }
        }
        for (PracticeRoom other : avoidRooms) {
            if (other == null || other.region() == null) {
                continue;
            }
            Cuboid r = other.region();
            if (!r.worldName().equals(worldName)) {
                continue;
            }
            if (intersects(minX, minZ, maxX, maxZ, r.minX(), r.minZ(), r.maxX(), r.maxZ())) {
                return false;
            }
        }
        for (OverlapBox box : externalOverlaps.get()) {
            if (!box.worldName().equals(worldName)) {
                continue;
            }
            if (intersects(minX, minZ, maxX, maxZ, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                return false;
            }
        }
        return true;
    }

    private static boolean intersects(int aMinX, int aMinZ, int aMaxX, int aMaxZ,
                                      int bMinX, int bMinZ, int bMaxX, int bMaxZ) {
        return aMinX <= bMaxX && aMaxX >= bMinX && aMinZ <= bMaxZ && aMaxZ >= bMinZ;
    }

    private static int snap(int coordinate) {
        return coordinate & ~0xF;
    }

    public record PracticeCopy(UUID instanceId, Cuboid region, Location spawn) {
    }

    public record LiveCopy(UUID instanceId, String practiceId, Cuboid region) {
    }

    public record OverlapBox(String worldName, int minX, int minZ, int maxX, int maxZ) {
    }
}
