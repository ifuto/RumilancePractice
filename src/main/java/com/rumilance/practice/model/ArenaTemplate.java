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
        String iconMaterial,
        String displayName,
        boolean queueSelectable
) {

    public ArenaTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(world, "world");
        if (iconMaterial != null && iconMaterial.isBlank()) {
            iconMaterial = null;
        }
        // 外部名は未設定なら内部名をそのまま使う(ユーザーに見える名前は常に埋まっている)。
        displayName = displayName == null || displayName.isBlank() ? name : displayName.trim();
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

    /**
     * Backward-compatible constructor: 外部名は内部名と同じ、Queue 戦・Random Map で
     * 選ばれる(既存のアリーナは今まで通り振る舞う)。
     */
    public ArenaTemplate(
            UUID id, String name, ArenaType type, String world,
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
            String serializedSpawnA, String serializedSpawnB,
            String schematicPath, boolean enabled, boolean party, String iconMaterial
    ) {
        this(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial,
                name, true);
    }

    /**
     * 内部名({@link #name()}、一意・ユーザーにはほぼ見せない)に対して、ユーザーに
     * 見せる名前。重複してよい。未設定なら内部名が返る。
     */
    public String displayNameOrName() {
        return displayName == null || displayName.isBlank() ? name : displayName;
    }

    public ArenaTemplate withDisplayName(String newDisplayName) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial,
                newDisplayName, queueSelectable);
    }

    /** Queue 戦・Random Map の候補に入れるか。false なら Duel Request 一覧にだけ出る。 */
    public ArenaTemplate withQueueSelectable(boolean selectable) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial,
                displayName, selectable);
    }

    public ArenaTemplate withName(String newName) {
        return new ArenaTemplate(id, newName, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withType(ArenaType newType) {
        return new ArenaTemplate(id, name, newType, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withBounds(String newWorld, int nMinX, int nMinY, int nMinZ,
                                    int nMaxX, int nMaxY, int nMaxZ) {
        return new ArenaTemplate(id, name, type, newWorld, nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withSpawns(String spawnA, String spawnB) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                spawnA, spawnB, schematicPath, enabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withSchematic(String path) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, path, enabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withEnabled(boolean newEnabled) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, newEnabled, party, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withParty(boolean partyEnabled) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, partyEnabled, iconMaterial, displayName, queueSelectable);
    }

    public ArenaTemplate withIconMaterial(String material) {
        return new ArenaTemplate(id, name, type, world, minX, minY, minZ, maxX, maxY, maxZ,
                serializedSpawnA, serializedSpawnB, schematicPath, enabled, party, material, displayName, queueSelectable);
    }
}
