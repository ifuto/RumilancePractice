package com.rumilance.practice.arena;

import com.rumilance.practice.model.ArenaInstance;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaType;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over arena reservation/regeneration used by the match engine. Exactly one
 * {@link ArenaInstance} currently backs each enabled {@link ArenaTemplate}; concurrency is
 * achieved by defining multiple templates of the same {@link ArenaType} (see {@code arenas.yml}). Two implementations are provided:
 * <ul>
 *     <li>{@link SimpleArenaService} - no FAWE/WorldEdit dependency; simply teleports players to
 *     the template's configured spawn points and performs no world regeneration. Suitable for
 *     testing or servers without FastAsyncWorldEdit/WorldEdit installed.</li>
 *     <li>{@link FaweArenaService} - delegates block regeneration between matches to
 *     {@code com.rumilance.practice.arena.fawe.FaweBridge}, pasting the template's saved
 *     schematic back over the arena bounds on release.</li>
 * </ul>
 */
public interface ArenaService {

    /**
     * Registers (or replaces) the templates this service may reserve instances from.
     */
    void setTemplates(List<ArenaTemplate> templates);

    List<ArenaTemplate> templates();

    List<ArenaTemplate> templates(ArenaType type);

    /**
     * Atomically reserves a free, enabled arena instance matching {@code type}, pastes/prepares
     * it if necessary, and marks it {@code IN_USE}. Never returns an instance already held by another
     * match - see {@code com.rumilance.practice.match.MatchRegistry} for the equivalent
     * player-side double-registration guard.
     *
     * @return a future completing with the reserved instance, or empty if none is available.
     */
    CompletableFuture<Optional<ArenaInstance>> reserve(ArenaType type, UUID matchId);

    /**
     * Reserves an instance of ONE specific template by name (kits pinned to a single arena).
     * Falls back to {@link #reserve(ArenaType, UUID)} semantics when the named
     * template does not exist or is disabled — implementations may also return empty instead.
     *
     * @return a future completing with the reserved instance, or empty if unavailable.
     */
    default CompletableFuture<Optional<ArenaInstance>> reserveNamed(String templateName, UUID matchId) {
        return reserve(ArenaType.DUEL, matchId);
    }

    /**
     * Regenerates (if configured/available) and releases {@code instanceId} back to the
     * {@code AVAILABLE} pool. Safe to call for an instance that is already available or unknown.
     */
    CompletableFuture<Void> release(UUID instanceId);

    Optional<ArenaInstance> get(UUID instanceId);

    /**
     * @return the first configured spawn point ("side A") of {@code instance}'s template.
     */
    Location spawnA(ArenaInstance instance);

    /**
     * @return the second configured spawn point ("side B") of {@code instance}'s template.
     */
    Location spawnB(ArenaInstance instance);
}
