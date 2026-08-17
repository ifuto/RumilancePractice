package com.rumilance.practice.model;

import com.rumilance.practice.state.ArenaType;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted, world-editor-independent description of an arena's bounding box and spawn
 * points. Locations are kept as pre-serialized strings (see {@code LocationUtil}) so this
 * model has no hard dependency on a loaded {@code World}. Kits reference arenas by
 * {@link #name()} directly (see {@code KitDefinition#arenaName()}); there is no terrain
 * classification.
 */
public record ArenaTemplate(
        UUID id,
        String name,
        ArenaType type,
        String world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        String serializedSpawnA,
        String serializedSpawnB,
        String schematicPath,
        boolean enabled
) {

    public ArenaTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(world, "world");
    }
}
