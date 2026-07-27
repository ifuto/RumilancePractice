package com.rumilance.practice.arena;

import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * FAWE/WorldEdit-free {@link ArenaService} fallback: reservation/release only flips the in-memory
 * {@code ArenaInstanceState} and never touches the world. Used automatically when FastAsyncWorldEdit
 * and WorldEdit are both absent, and directly by unit tests that need a working {@link ArenaService}
 * without a live Bukkit world.
 */
public final class SimpleArenaService extends AbstractArenaService {

    @Override
    public CompletableFuture<Optional<ArenaInstance>> reserve(ArenaType type, ArenaTerrain terrain, UUID matchId) {
        return CompletableFuture.completedFuture(reserveInstance(type, terrain, matchId));
    }

    @Override
    public CompletableFuture<Void> release(UUID instanceId) {
        get(instanceId).ifPresent(this::markAvailable);
        return CompletableFuture.completedFuture(null);
    }
}
