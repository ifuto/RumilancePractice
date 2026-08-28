package com.rumilance.practice.model;

import com.rumilance.practice.state.ArenaInstanceState;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime, mutable handle to a single arena "slot" backed by an {@link ArenaTemplate}.
 * Multiple {@code ArenaInstance}s may reference the same template so several matches can
 * run concurrently on independently regenerated copies.
 *
 * <p>An instance carries its own <b>origin</b> (min corner). For in-place arenas the origin
 * equals the template's min corner; for disposable pasted copies it is wherever the copy was
 * placed, and every bound/spawn is shifted by the same offset.</p>
 */
public final class ArenaInstance {

    private final UUID id;
    private final ArenaTemplate template;
    private final int originX;
    private final int originY;
    private final int originZ;
    private volatile ArenaInstanceState state;
    private volatile UUID currentMatchId;

    /** In-place instance: bounds identical to the template's. */
    public ArenaInstance(UUID id, ArenaTemplate template) {
        this(id, template, template.minX(), template.minY(), template.minZ());
    }

    /** Relocated instance: a pasted copy whose min corner sits at the given origin. */
    public ArenaInstance(UUID id, ArenaTemplate template, int originX, int originY, int originZ) {
        this.id = Objects.requireNonNull(id, "id");
        this.template = Objects.requireNonNull(template, "template");
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.state = ArenaInstanceState.AVAILABLE;
        this.currentMatchId = null;
    }

    public UUID id() {
        return id;
    }

    public ArenaTemplate template() {
        return template;
    }

    // ---- placed bounds (origin-shifted copies of the template bounds) ----

    public int minX() {
        return originX;
    }

    public int minY() {
        return originY;
    }

    public int minZ() {
        return originZ;
    }

    public int maxX() {
        return originX + (template.maxX() - template.minX());
    }

    public int maxY() {
        return originY + (template.maxY() - template.minY());
    }

    public int maxZ() {
        return originZ + (template.maxZ() - template.minZ());
    }

    /** Axis-aligned playable region for this instance (origin-shifted when relocated). */
    public com.rumilance.practice.util.Cuboid bounds() {
        return com.rumilance.practice.util.Cuboid.of(
                template.world(), minX(), minY(), minZ(), maxX(), maxY(), maxZ());
    }

    /** @return a copy of {@code base} shifted from template space into this instance's space. */
    public org.bukkit.Location offset(org.bukkit.Location base) {
        return base.clone().add(
                originX - template.minX(),
                originY - template.minY(),
                originZ - template.minZ());
    }

    /** @return true when this instance is a pasted copy (not the template's own location). */
    public boolean isRelocated() {
        return originX != template.minX() || originY != template.minY() || originZ != template.minZ();
    }

    public ArenaInstanceState state() {
        return state;
    }

    public void setState(ArenaInstanceState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
    }

    public UUID currentMatchId() {
        return currentMatchId;
    }

    public void assignMatch(UUID matchId) {
        this.currentMatchId = matchId;
        this.state = ArenaInstanceState.IN_USE;
    }

    public void release() {
        this.currentMatchId = null;
        this.state = ArenaInstanceState.AVAILABLE;
    }

    public boolean isAvailable() {
        return state == ArenaInstanceState.AVAILABLE;
    }
}
