package com.rumilance.practice.model;

import com.rumilance.practice.practice.PracticeType;
import com.rumilance.practice.util.Cuboid;

import java.util.Objects;

/**
 * A saved practice room. {@link #id()} is case-sensitive (unlike arena template names).
 */
public record PracticeRoom(
        String id,
        PracticeType type,
        String world,
        Cuboid region,
        String serializedSpawn,
        boolean enabled
) {
    public PracticeRoom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(serializedSpawn, "serializedSpawn");
    }

    /** Display label: underscores become half-width spaces. */
    public String displayName() {
        return id.replace('_', ' ');
    }

    public PracticeRoom withEnabled(boolean value) {
        return new PracticeRoom(id, type, world, region, serializedSpawn, value);
    }

    public PracticeRoom withRegion(Cuboid cuboid) {
        return new PracticeRoom(id, type, cuboid.worldName(), cuboid, serializedSpawn, enabled);
    }

    public PracticeRoom withSpawn(String serialized) {
        return new PracticeRoom(id, type, world, region, serialized, enabled);
    }
}
