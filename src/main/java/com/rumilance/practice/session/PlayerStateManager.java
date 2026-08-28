package com.rumilance.practice.session;

import com.rumilance.practice.state.PlayerState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the allowed {@link PlayerState} transition graph for every online player.
 * Framework-agnostic and keyed purely by {@link UUID}, so it is trivially unit testable.
 *
 * <p>Allowed transitions:</p>
 * <pre>
 * IDLE              -&gt; LOBBY
 * LOBBY             -&gt; OPENING_GUI, QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, SPECTATING, FFA, EDITING_KIT, PREPARING_MATCH, PRACTICE_WAIT, PRACTICE_ACTIVE
 * OPENING_GUI       -&gt; LOBBY, QUEUED_RANKED, QUEUED_UNRANKED, REQUESTING_DUEL, SPECTATING, FFA, EDITING_KIT, PREPARING_MATCH, PRACTICE_WAIT, PRACTICE_ACTIVE
 * QUEUED_RANKED     -&gt; LOBBY, PREPARING_MATCH
 * QUEUED_UNRANKED   -&gt; LOBBY, PREPARING_MATCH
 * REQUESTING_DUEL   -&gt; LOBBY, PREPARING_MATCH
 * PREPARING_MATCH   -&gt; COUNTDOWN, LOBBY
 * COUNTDOWN         -&gt; FIGHTING, LOBBY
 * FIGHTING          -&gt; ENDING
 * ENDING            -&gt; LOBBY, PREPARING_MATCH
 * SPECTATING        -&gt; LOBBY
 * FFA               -&gt; LOBBY
 * EDITING_KIT       -&gt; LOBBY
 * PRACTICE_WAIT     -&gt; PRACTICE_ACTIVE, LOBBY, OPENING_GUI
 * PRACTICE_ACTIVE   -&gt; PRACTICE_WAIT, LOBBY, OPENING_GUI
 * </pre>
 *
 * <p>Bugs the spec explicitly calls out are prevented by this graph: a player cannot be in two
 * queues at once (queue states only reachable from LOBBY/OPENING_GUI), cannot accept a duel while
 * FIGHTING (FIGHTING only leaves via ENDING), and any player stuck in an unexpected state after a
 * crash/restart can always be force-reset back to LOBBY via {@link #resetToLobby(UUID)}.</p>
 */
public final class PlayerStateManager {

    private static final Map<PlayerState, Set<PlayerState>> ALLOWED = buildTransitionGraph();

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public PlayerState getState(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return states.getOrDefault(uuid, PlayerState.IDLE);
    }

    /**
     * Registers a freshly joined player and places them directly into {@link PlayerState#LOBBY}
     * (the spec's "IDLE -&gt; LOBBY on join" rule), bypassing transition validation since there is
     * no prior state to validate against.
     */
    public PlayerState initialize(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        states.put(uuid, PlayerState.LOBBY);
        return PlayerState.LOBBY;
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    /**
     * Unconditionally forces {@code uuid} back to {@link PlayerState#LOBBY}, regardless of the
     * transition graph. Used as a safety net (queue/match error handling, admin cleanup, plugin
     * reload) so a player can never be permanently stuck in QUEUED or FIGHTING states.
     */
    public PlayerState resetToLobby(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        states.put(uuid, PlayerState.LOBBY);
        return PlayerState.LOBBY;
    }

    public static boolean canTransition(PlayerState from, PlayerState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Attempts to move {@code uuid} from its current state to {@code target}.
     *
     * @throws IllegalStateTransitionException if the transition is not allowed from the
     *                                          player's current state.
     */
    public PlayerState transition(UUID uuid, PlayerState target) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(target, "target");
        PlayerState current = getState(uuid);
        if (!canTransition(current, target)) {
            throw new IllegalStateTransitionException(uuid, current, target);
        }
        states.put(uuid, target);
        return target;
    }

    private static Map<PlayerState, Set<PlayerState>> buildTransitionGraph() {
        Map<PlayerState, Set<PlayerState>> map = new EnumMap<>(PlayerState.class);
        map.put(PlayerState.IDLE, Set.of(PlayerState.LOBBY));
        map.put(PlayerState.LOBBY, Set.of(
                PlayerState.OPENING_GUI, PlayerState.QUEUED_RANKED, PlayerState.QUEUED_UNRANKED,
                PlayerState.REQUESTING_DUEL, PlayerState.SPECTATING, PlayerState.FFA, PlayerState.EDITING_KIT,
                // Direct match entry: duel-request acceptors and team-battle members enter a
                // match straight from LOBBY (no queue). Without this edge every tryTransition
                // silently failed, players stayed in LOBBY state for the whole fight and the
                // lobby damage protection made them unhittable.
                PlayerState.PREPARING_MATCH,
                PlayerState.PRACTICE_WAIT, PlayerState.PRACTICE_ACTIVE
        ));
        map.put(PlayerState.OPENING_GUI, Set.of(
                PlayerState.LOBBY, PlayerState.QUEUED_RANKED, PlayerState.QUEUED_UNRANKED,
                PlayerState.REQUESTING_DUEL, PlayerState.SPECTATING, PlayerState.FFA, PlayerState.EDITING_KIT,
                PlayerState.PREPARING_MATCH,
                PlayerState.PRACTICE_WAIT, PlayerState.PRACTICE_ACTIVE
        ));
        map.put(PlayerState.QUEUED_RANKED, Set.of(PlayerState.LOBBY, PlayerState.PREPARING_MATCH));
        map.put(PlayerState.QUEUED_UNRANKED, Set.of(PlayerState.LOBBY, PlayerState.PREPARING_MATCH));
        map.put(PlayerState.REQUESTING_DUEL, Set.of(PlayerState.LOBBY, PlayerState.PREPARING_MATCH));
        map.put(PlayerState.PREPARING_MATCH, Set.of(PlayerState.COUNTDOWN, PlayerState.LOBBY));
        map.put(PlayerState.COUNTDOWN, Set.of(PlayerState.FIGHTING, PlayerState.LOBBY));
        map.put(PlayerState.FIGHTING, Set.of(PlayerState.ENDING));
        map.put(PlayerState.ENDING, Set.of(PlayerState.LOBBY, PlayerState.PREPARING_MATCH));
        map.put(PlayerState.SPECTATING, Set.of(PlayerState.LOBBY));
        map.put(PlayerState.FFA, Set.of(PlayerState.LOBBY));
        map.put(PlayerState.EDITING_KIT, Set.of(PlayerState.LOBBY));
        map.put(PlayerState.PRACTICE_WAIT, Set.of(
                PlayerState.PRACTICE_ACTIVE, PlayerState.LOBBY, PlayerState.OPENING_GUI));
        map.put(PlayerState.PRACTICE_ACTIVE, Set.of(
                PlayerState.PRACTICE_WAIT, PlayerState.LOBBY, PlayerState.OPENING_GUI));
        return Collections.unmodifiableMap(map);
    }
}
