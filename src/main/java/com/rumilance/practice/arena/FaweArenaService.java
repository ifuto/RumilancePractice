package com.rumilance.practice.arena;

import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ArenaService} backed by {@link FaweBridge}: on {@link #release(UUID)}, pastes the
 * template's saved schematic back over the arena bounds (anchored at the template's minimum
 * corner) so the arena is pristine for the next match. If a template has no schematic configured,
 * or {@link FaweBridge#isAvailable()} is {@code false}, release degrades gracefully to the same
 * teleport-only behaviour as {@link SimpleArenaService}.
 */
public final class FaweArenaService extends AbstractArenaService {

    private static final Logger LOGGER = Logger.getLogger(FaweArenaService.class.getName());

    private final FaweBridge faweBridge;
    private final File schematicRoot;
    private final boolean regenerateOnRelease;

    public FaweArenaService(FaweBridge faweBridge, File schematicRoot, boolean regenerateOnRelease) {
        this.faweBridge = Objects.requireNonNull(faweBridge, "faweBridge");
        this.schematicRoot = Objects.requireNonNull(schematicRoot, "schematicRoot");
        this.regenerateOnRelease = regenerateOnRelease;
    }

    @Override
    public CompletableFuture<Optional<ArenaInstance>> reserve(ArenaType type, ArenaTerrain terrain, UUID matchId) {
        return CompletableFuture.completedFuture(reserveInstance(type, terrain, matchId));
    }

    @Override
    public CompletableFuture<Void> release(UUID instanceId) {
        Optional<ArenaInstance> instanceOpt = get(instanceId);
        if (instanceOpt.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        ArenaInstance instance = instanceOpt.get();
        ArenaTemplate template = instance.template();

        if (!regenerateOnRelease || !faweBridge.isAvailable() || isBlank(template.schematicPath())) {
            markAvailable(instance);
            return CompletableFuture.completedFuture(null);
        }

        markRegenerating(instance);
        Location anchor = LocationUtil.deserialize(template.serializedSpawnA());
        Location minCorner = new Location(
                anchor.getWorld() != null ? anchor.getWorld() : Bukkit.getWorld(template.world()),
                template.minX(), template.minY(), template.minZ());
        Path schematicFile = resolveSchematicPath(template.schematicPath());

        return faweBridge.regenerate(schematicFile, minCorner).handle((success, throwable) -> {
            if (throwable != null) {
                LOGGER.log(Level.WARNING, "Arena regeneration threw an exception for instance " + instanceId, throwable);
            } else if (!Boolean.TRUE.equals(success)) {
                LOGGER.warning("Arena regeneration reported failure for instance " + instanceId
                        + " (template=" + template.name() + "); releasing anyway.");
            }
            markAvailable(instance);
            return null;
        });
    }

    private Path resolveSchematicPath(String schematicPath) {
        File file = new File(schematicPath);
        return (file.isAbsolute() ? file : new File(schematicRoot, schematicPath)).toPath();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
