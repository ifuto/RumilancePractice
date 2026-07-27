package com.rumilance.practice.model;

import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;

import java.util.Objects;
import java.util.UUID;

/**
 * Persisted, world-editor-independent description of an arena's bounding box and spawn
 * points. Locations are kept as pre-serialized strings (see {@code LocationUtil}) so this
 * model has no hard dependency on a loaded {@code World}.
 */
public record ArenaTemplate(
        UUID id,
        String name,
        ArenaType type,
        ArenaTerrain terrain,
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
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(world, "world");
    }

    /**
     * Backward-compatible constructor for callers predating {@link ArenaTerrain} support;
     * defaults the terrain classification to {@link ArenaTerrain#ANY}.
     */
    public ArenaTemplate(
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
        this(id, name, type, ArenaTerrain.ANY, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled);
    }
}
