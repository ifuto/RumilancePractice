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
 *
 * <p>{@link #party()} marks a Party Fight map; {@link #iconMaterial()} is the block type
 * shown in the party map picker (set via {@code /arena set party} + BlockPlaceEvent).</p>
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
        boolean enabled,
        boolean party,
        String iconMaterial
) {

    public ArenaTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(world, "world");
        if (iconMaterial != null && iconMaterial.isBlank()) {
            iconMaterial = null;
        }
    }

    /** Backward-compatible constructor (party off, no icon). */
    public ArenaTemplate(
            UUID id, String name, ArenaType type, String world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            String serializedSpawnA, String serializedSpawnB,
            String schematicPath, boolean enabled
    ) {
        this(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, false, null);
    }

    public ArenaTemplate withName(String newName) {
        return new ArenaTemplate(id, newName, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial);
    }

    public ArenaTemplate withType(ArenaType newType) {
        return new ArenaTemplate(id, name, newType, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial);
    }

    public ArenaTemplate withBounds(String newWorld, int nMinX, int nMinY, int nMinZ,
                                    int nMaxX, int nMaxY, int nMaxZ) {
        return new ArenaTemplate(id, name, type, newWorld, nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial);
    }

    public ArenaTemplate withSpawns(String spawnA, String spawnB) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                spawnA, spawnB, schematicPath, enabled, party, iconMaterial);
    }

    public ArenaTemplate withSchematic(String path) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, path, enabled, party, iconMaterial);
    }

    public ArenaTemplate withEnabled(boolean newEnabled) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, newEnabled, party, iconMaterial);
    }

    public ArenaTemplate withParty(boolean partyEnabled) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, partyEnabled, iconMaterial);
    }

    public ArenaTemplate withIconMaterial(String material) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, material);
    }
}
