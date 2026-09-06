package com.rumilance.practice.practice;

import com.rumilance.practice.util.Cuboid;

/**
 * In-memory admin draft for a practice room (p1 spawn + optional bot home position).
 */
public final class PracticeDraft {

    private final String id;
    private PracticeType type;
    private Cuboid region;
    private String serializedSpawn;
    private String serializedBotSpawn;

    public PracticeDraft(String id, PracticeType type) {
        this.id = id;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public PracticeType type() {
        return type;
    }

    public void setType(PracticeType type) {
        this.type = type;
    }

    public Cuboid region() {
        return region;
    }

    public void setRegion(Cuboid region) {
        this.region = region;
    }

    public String serializedSpawn() {
        return serializedSpawn;
    }

    public void setSerializedSpawn(String serializedSpawn) {
        this.serializedSpawn = serializedSpawn;
    }

    public String serializedBotSpawn() {
        return serializedBotSpawn;
    }

    public void setSerializedBotSpawn(String serializedBotSpawn) {
        this.serializedBotSpawn = serializedBotSpawn;
    }

    public boolean readyToSave() {
        return region != null && serializedSpawn != null && !serializedSpawn.isBlank();
    }
}
