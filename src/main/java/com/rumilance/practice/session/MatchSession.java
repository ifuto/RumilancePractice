package com.rumilance.practice.session;

import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime representation of a single ongoing (or recently finished) match.
 */
public final class MatchSession {

    private final UUID id;
    private final MatchMode mode;
    private final String kitName;
    private final List<UUID> participants;
    private final ArenaTerrain terrain;
    private final int bestOf;
    /** Team color per participant: index 0 is always RED, index 1 is always BLUE. */
    private final Map<UUID, TeamColor> teamColors = new ConcurrentHashMap<>();
    /** Per-participant win count of the current rematch chain (0-0 on a fresh match). */
    private final Map<UUID, Integer> seriesWins = new ConcurrentHashMap<>();
    private volatile UUID arenaInstanceId;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> roundWins = new ConcurrentHashMap<>();
    private final AtomicBoolean resultApplied = new AtomicBoolean(false);
    private final AtomicBoolean disconnectPenaltyIssued = new AtomicBoolean(false);
    private volatile MatchState state;
    private volatile Instant startedAt;
    private volatile Instant endedAt;
    private volatile UUID winner;
    private volatile boolean draw;
    private volatile boolean rematchRequestedA;
    private volatile boolean rematchRequestedB;
    private volatile boolean shuttingDown;

    public MatchSession(UUID id, MatchMode mode, String kitName, List<UUID> participants,
                        UUID arenaInstanceId, ArenaTerrain terrain, int bestOf) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.kitName = Objects.requireNonNull(kitName, "kitName");
        this.participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        this.arenaInstanceId = arenaInstanceId;
        this.terrain = terrain == null ? ArenaTerrain.ANY : terrain;
        this.bestOf = Math.max(1, bestOf);
        this.state = MatchState.CREATED;
        for (UUID participant : this.participants) {
            roundWins.put(participant, 0);
        }
        if (!this.participants.isEmpty()) {
            teamColors.put(this.participants.get(0), TeamColor.RED);
        }
        if (this.participants.size() > 1) {
            teamColors.put(this.participants.get(1), TeamColor.BLUE);
        }
    }

    public UUID id() {
        return id;
    }

    public MatchMode mode() {
        return mode;
    }

    public String kitName() {
        return kitName;
    }

    public List<UUID> participants() {
        return participants;
    }

    public UUID arenaInstanceId() {
        return arenaInstanceId;
    }

    public void setArenaInstanceId(UUID arenaInstanceId) {
        this.arenaInstanceId = arenaInstanceId;
    }

    public ArenaTerrain terrain() {
        return terrain;
    }

    public int bestOf() {
        return bestOf;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public UUID opponentOf(UUID uuid) {
        for (UUID participant : participants) {
            if (!participant.equals(uuid)) {
                return participant;
            }
        }
        return null;
    }

    /**
     * @return the player's team color. Fallback keeps the deterministic
     *         first=RED / second=BLUE mapping for players without an explicit entry.
     */
    public TeamColor teamColor(UUID playerId) {
        TeamColor color = teamColors.get(playerId);
        return color != null ? color
                : (participants.indexOf(playerId) == 0 ? TeamColor.RED : TeamColor.BLUE);
    }

    /**
     * Carries a rematch chain's accumulated wins into a new match session. Only invoked when
     * a rematch is confirmed; a fresh queue/duel-request match starts with an empty series.
     */
    public void applySeries(Map<UUID, Integer> carry) {
        if (carry != null) {
            seriesWins.putAll(carry);
        }
    }

    public void addSeriesWin(UUID playerId) {
        seriesWins.merge(playerId, 1, Integer::sum);
    }

    public int seriesWinsOf(UUID playerId) {
        return seriesWins.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> seriesWinsSnapshot() {
        return Map.copyOf(seriesWins);
    }

    public MatchState state() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public void markActive() {
        this.startedAt = Instant.now();
        this.state = MatchState.ACTIVE;
    }

    public void end(UUID winnerUuid, boolean isDraw) {
        this.winner = winnerUuid;
        this.draw = isDraw;
        this.endedAt = Instant.now();
        this.state = MatchState.ENDING;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public UUID winner() {
        return winner;
    }

    public boolean isDraw() {
        return draw;
    }

    public void addKill(UUID killer) {
        kills.merge(killer, 1, Integer::sum);
    }

    public int killsOf(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public int roundWinsOf(UUID uuid) {
        return roundWins.getOrDefault(uuid, 0);
    }

    public void addRoundWin(UUID uuid) {
        roundWins.merge(uuid, 1, Integer::sum);
    }

    public boolean tryMarkResultApplied() {
        return resultApplied.compareAndSet(false, true);
    }

    public boolean isResultApplied() {
        return resultApplied.get();
    }

    public boolean tryMarkDisconnectPenalty() {
        return disconnectPenaltyIssued.compareAndSet(false, true);
    }

    public void setRematchRequested(UUID uuid, boolean value) {
        if (participants.isEmpty()) {
            return;
        }
        if (participants.get(0).equals(uuid)) {
            rematchRequestedA = value;
        } else if (participants.size() > 1 && participants.get(1).equals(uuid)) {
            rematchRequestedB = value;
        }
    }

    public boolean bothRematchRequested() {
        return rematchRequestedA && rematchRequestedB;
    }

    public boolean isRematchRequested(UUID uuid) {
        if (participants.isEmpty()) {
            return false;
        }
        if (participants.get(0).equals(uuid)) {
            return rematchRequestedA;
        }
        if (participants.size() > 1 && participants.get(1).equals(uuid)) {
            return rematchRequestedB;
        }
        return false;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.shuttingDown = shuttingDown;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }
}
