package com.rumilance.practice.arena;

import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disposable-copy arena service: every match gets its own fresh copy of the template.
 *
 * <p>On {@code reserve}, the template's schematic is pasted at a random location inside a
 * configurable placement band that never overlaps any currently-live copy (or any template's
 * own build). On {@code release}, the copy's whole region is filled with air and the slot is
 * forgotten. This allows unlimited concurrent matches per template.</p>
 *
 * <p>Random placement rules:</p>
 * <ul>
 *   <li>X/Z are picked uniformly inside {@code [-placementRange, +placementRange]} around the
 *       configured centre, snapped to a 16-block grid (chunk aligned).</li>
 *   <li>The candidate box (arena bounds + {@code spacing} margin) must not intersect any live
 *       copy's box or any enabled template's own region.</li>
 *   <li>Y is kept identical to the template so relative arena heights (void floors, platforms)
 *       behave exactly as built.</li>
 * </ul>
 */
public final class DisposableArenaService extends AbstractArenaService {

    private static final Logger LOGGER = Logger.getLogger(DisposableArenaService.class.getName());
    private static final int MAX_PLACEMENT_ATTEMPTS = 60;

    private final org.bukkit.plugin.Plugin plugin;
    private final FaweBridge faweBridge;
    private final File schematicRoot;
    private final int placementRange;
    private final int spacing;
    private final int centerX;
    private final int centerZ;

    /** Live pasted copies (instanceId -> instance); used for overlap checks and cleanup. */
    private final Map<UUID, ArenaInstance> liveCopies = new ConcurrentHashMap<>();
    /** Optional hooks fired on the main thread after a copy is pasted / before it is cleared. */
    private volatile java.util.function.Consumer<ArenaInstance> onCopyPasted;
    private volatile java.util.function.Consumer<ArenaInstance> onCopyCleared;

    public void setCopyHooks(java.util.function.Consumer<ArenaInstance> pasted,
                             java.util.function.Consumer<ArenaInstance> cleared) {
        this.onCopyPasted = pasted;
        this.onCopyCleared = cleared;
    }

    public DisposableArenaService(org.bukkit.plugin.Plugin plugin, FaweBridge faweBridge, File schematicRoot,
                                  int placementRange, int spacing, int centerX, int centerZ) {
        this.plugin = plugin;
        this.faweBridge = faweBridge;
        this.schematicRoot = schematicRoot;
        this.placementRange = Math.max(256, placementRange);
        this.spacing = Math.max(16, spacing);
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    @Override
    public Optional<ArenaInstance> get(UUID instanceId) {
        ArenaInstance live = liveCopies.get(instanceId);
        if (live != null) {
            return Optional.of(live);
        }
        return super.get(instanceId);
    }

    @Override
    public CompletableFuture<Optional<ArenaInstance>> reserve(ArenaType type, ArenaTerrain terrain, UUID matchId) {
        // Pick a template (round-robin over enabled candidates via random start).
        List<ArenaTemplate> candidates = templates().stream()
                .filter(t -> t.enabled() && t.type() == type && t.terrain().matches(terrain))
                .filter(t -> t.schematicPath() != null && !t.schematicPath().isBlank())
                .toList();
        if (candidates.isEmpty()) {
            // No schematic-backed template: fall back to the classic in-place reservation.
            return CompletableFuture.completedFuture(reserveInstance(type, terrain, matchId));
        }
        ArenaTemplate template = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        World world = Bukkit.getWorld(template.world());
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        Optional<int[]> originOpt = findFreeOrigin(template);
        if (originOpt.isEmpty()) {
            LOGGER.warning("No free placement found for a disposable copy of '" + template.name() + "'.");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        int[] origin = originOpt.get();
        ArenaInstance instance = new ArenaInstance(UUID.randomUUID(), template,
                origin[0], template.minY(), origin[1]);
        instance.assignMatch(matchId);
        liveCopies.put(instance.id(), instance);

        Path schematic = resolveSchematicPath(template.schematicPath());
        Location anchor = new Location(world, instance.minX(), instance.minY(), instance.minZ());
        return faweBridge.regenerate(schematic, anchor).handle((success, throwable) -> {
            if (throwable != null || !Boolean.TRUE.equals(success)) {
                LOGGER.log(Level.WARNING, "Failed to paste disposable arena copy of '"
                        + template.name() + "'", throwable);
                liveCopies.remove(instance.id());
                return Optional.<ArenaInstance>empty();
            }
            java.util.function.Consumer<ArenaInstance> hook = onCopyPasted;
            if (hook != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> hook.accept(instance));
            }
            return Optional.of(instance);
        });
    }

    @Override
    public CompletableFuture<Void> release(UUID instanceId) {
        ArenaInstance instance = liveCopies.remove(instanceId);
        if (instance == null) {
            // Not a disposable copy (in-place fallback): just release the slot.
            super.get(instanceId).ifPresent(this::markAvailable);
            return CompletableFuture.completedFuture(null);
        }
        java.util.function.Consumer<ArenaInstance> hook = onCopyCleared;
        if (hook != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> hook.accept(instance));
        }
        World world = Bukkit.getWorld(instance.template().world());
        if (world == null || !faweBridge.isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        return faweBridge.clearRegion(world,
                        instance.minX(), instance.minY(), instance.minZ(),
                        instance.maxX(), instance.maxY(), instance.maxZ())
                .handle((success, throwable) -> {
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        LOGGER.warning("Failed to clear disposable arena copy " + instanceId
                                + " (template=" + instance.template().name() + ").");
                    }
                    return null;
                });
    }

    /** Removes every live copy's blocks; called on plugin disable so no copies leak. */
    public CompletableFuture<Void> clearAllCopies() {
        CompletableFuture<?>[] futures = liveCopies.keySet().stream()
                .map(this::release)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /** @return every currently pasted copy (for overlap checks and debugging). */
    public List<ArenaInstance> liveCopies() {
        return List.copyOf(liveCopies.values());
    }

    // ------------------------------------------------------------------ placement

    private Optional<int[]> findFreeOrigin(ArenaTemplate template) {
        int width = template.maxX() - template.minX() + 1;
        int depth = template.maxZ() - template.minZ() + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            // Chunk-aligned random origin inside the placement band.
            int x = snap(centerX + random.nextInt(-placementRange, placementRange + 1));
            int z = snap(centerZ + random.nextInt(-placementRange, placementRange + 1));
            if (isFree(template.world(), x, z, width, depth)) {
                return Optional.of(new int[]{x, z});
            }
        }
        return Optional.empty();
    }

    private boolean isFree(String worldName, int x, int z, int width, int depth) {
        int minX = x - spacing;
        int minZ = z - spacing;
        int maxX = x + width - 1 + spacing;
        int maxZ = z + depth - 1 + spacing;
        // Against live copies.
        for (ArenaInstance live : liveCopies.values()) {
            if (!live.template().world().equals(worldName)) {
                continue;
            }
            if (intersects(minX, minZ, maxX, maxZ,
                    live.minX(), live.minZ(), live.maxX(), live.maxZ())) {
                return false;
            }
        }
        // Against every template's own (source) region so we never stamp over a build.
        for (ArenaTemplate other : templates()) {
            if (!other.world().equals(worldName)) {
                continue;
            }
            if (intersects(minX, minZ, maxX, maxZ,
                    other.minX(), other.minZ(), other.maxX(), other.maxZ())) {
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
        return coordinate & ~0xF; // snap down to chunk boundary
    }

    private Path resolveSchematicPath(String schematicPath) {
        File file = new File(schematicPath);
        return (file.isAbsolute() ? file : new File(schematicRoot, schematicPath)).toPath();
    }
}
