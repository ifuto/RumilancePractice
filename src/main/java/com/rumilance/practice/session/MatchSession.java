package com.rumilance.practice.session;

import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.MatchState;
import com.rumilance.practice.state.TeamColor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime representation of a single ongoing (or recently finished) match.
 *
 * <p>Supports 1v1 duels (2 participants, index 0=RED / index 1=BLUE) and team matches with
 * an explicit RED/BLUE split of up to 15 players per side (ratios may be arbitrarily uneven,
 * e.g. 1v15). Team assignment is stored explicitly per player so a rematch chain keeps every
 * player on the same side.</p>
 */
public final class MatchSession {

    /** Hard cap per side in a team battle. */
    public static final int MAX_SIDE_SIZE = 15;

    private final UUID id;
    private final MatchMode mode;
    private final String kitName;
    private final List<UUID> participants;
    private final int bestOf;
    private final boolean teamMatch;
    /** Team color per participant (explicit, supports uneven splits). */
    private final Map<UUID, TeamColor> teamColors = new ConcurrentHashMap<>();
    /** Per-participant win count of the current rematch chain (0-0 on a fresh match). */
    private final Map<UUID, Integer> seriesWins = new ConcurrentHashMap<>();
    private volatile UUID arenaInstanceId;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> roundWins = new ConcurrentHashMap<>();
    private final AtomicBoolean resultApplied = new AtomicBoolean(false);
    private final AtomicBoolean disconnectPenaltyIssued = new AtomicBoolean(false);
    /** Set when the match was decided by a disconnect forfeit — rematch offers are skipped. */
    private final AtomicBoolean disconnectForfeit = new AtomicBoolean(false);
    private volatile MatchState state;
    private volatile Instant startedAt;
    private volatile Instant endedAt;
    /** Winning team color (for team matches), or null for 1v1/individual outcomes. */
    private volatile TeamColor winningTeam;
    private volatile UUID winner;
    private volatile boolean draw;
    private volatile boolean rematchRequestedA;
    private volatile boolean rematchRequestedB;
    private volatile boolean shuttingDown;
    /** Short public id from {@code DuelLogStore} for scoreboard /tab ({@code {duel_id}}). */
    private volatile String publicDuelId;
    /** Preferred arena template for rematch / named duel reserve (nullable = random). */
    private volatile String preferredArenaName;
    /** Team-only: whether teammates can damage each other. */
    private volatile boolean friendlyFire;
    /** Guards against double rematch starts while cleanup is in flight. */
    private final AtomicBoolean rematchStarting = new AtomicBoolean(false);
    /** End-of-life inventory bytes per participant (captured on death or at match end). */
    private final Map<UUID, byte[]> endInventories = new ConcurrentHashMap<>();
    /**
     * Explicit elimination ledger (death). The alive-check must never rely on
     * {@code GameMode.SPECTATOR} alone: a limbo death (e.g. a killing blow swallowed right
     * after a totem pop) leaves the player in SURVIVAL game mode while effectively out of the
     * fight, and their side would then never count as wiped. Mid-fight disconnects are covered
     * separately by the alive-check's online requirement.
     */
    private final java.util.Set<UUID> eliminated = ConcurrentHashMap.newKeySet();

    /** 1v1 constructor: exactly two participants, index 0 = RED, index 1 = BLUE. */
    public MatchSession(UUID id, MatchMode mode, String kitName, List<UUID> participants,
                        UUID arenaInstanceId, int bestOf) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.kitName = Objects.requireNonNull(kitName, "kitName");
        List<UUID> ordered = new ArrayList<>(Objects.requireNonNull(participants, "participants"));
        if (ordered.size() != 2) {
            throw new IllegalArgumentException("Duel requires exactly 2 participants, got " + ordered.size());
        }
        this.participants = List.copyOf(ordered);
        this.teamMatch = false;
        this.arenaInstanceId = arenaInstanceId;
        this.bestOf = Math.max(1, bestOf);
        this.state = MatchState.CREATED;
        for (UUID participant : this.participants) {
            roundWins.put(participant, 0);
            // seriesWins intentionally starts empty: a fresh match snapshot must be empty
            // (rematch chains seed it via applySeries; seriesWinsOf defaults to 0).
        }
        teamColors.put(this.participants.get(0), TeamColor.RED);
        teamColors.put(this.participants.get(1), TeamColor.BLUE);
    }

    /**
     * Team-battle constructor: explicit RED/BLUE rosters. Each side may hold 1..15 players,
     * ratios can be arbitrarily uneven — both sides just need to be non-empty.
     */
    public MatchSession(UUID id, MatchMode mode, String kitName,
                        List<UUID> redTeam, List<UUID> blueTeam,
                        UUID arenaInstanceId, int bestOf) {
        this.id = Objects.requireNonNull(id, "id");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.kitName = Objects.requireNonNull(kitName, "kitName");
        List<UUID> red = new ArrayList<>(Objects.requireNonNull(redTeam, "redTeam"));
        List<UUID> blue = new ArrayList<>(Objects.requireNonNull(blueTeam, "blueTeam"));
        if (red.isEmpty() || blue.isEmpty()) {
            throw new IllegalArgumentException("Both teams need at least one player");
        }
        if (red.size() > MAX_SIDE_SIZE || blue.size() > MAX_SIDE_SIZE) {
            throw new IllegalArgumentException("A side may hold at most " + MAX_SIDE_SIZE + " players");
        }
        List<UUID> ordered = new ArrayList<>(red.size() + blue.size());
        ordered.addAll(red);
        ordered.addAll(blue);
        if (ordered.stream().distinct().count() != ordered.size()) {
            throw new IllegalArgumentException("A player cannot be on both teams");
        }
        this.participants = List.copyOf(ordered);
        this.teamMatch = true;
        this.arenaInstanceId = arenaInstanceId;
        this.bestOf = Math.max(1, bestOf);
        this.state = MatchState.CREATED;
        for (UUID participant : this.participants) {
            roundWins.put(participant, 0);
            // seriesWins intentionally starts empty: a fresh match snapshot must be empty
            // (rematch chains seed it via applySeries; seriesWinsOf defaults to 0).
        }
        for (UUID p : red) {
            teamColors.put(p, TeamColor.RED);
        }
        for (UUID p : blue) {
            teamColors.put(p, TeamColor.BLUE);
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

    /** Kit id for a participant (matches share one kit). */
    public String kitFor(UUID playerId) {
        return kitName;
    }

    /**
     * Whether teammates can damage each other. Defaults to {@code false} for duels;
     * team matches copy the party setting via {@link #setFriendlyFire(boolean)}.
     */
    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public List<UUID> participants() {
        return participants;
    }

    /** @return the members of the given team (1 player in a duel, up to 15 in a team battle). */
    public List<UUID> team(TeamColor color) {
        List<UUID> out = new ArrayList<>();
        for (UUID p : participants) {
            if (teamColor(p) == color) {
                out.add(p);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** @return every teammate of {@code playerId}, excluding the player themselves. */
    public List<UUID> teammatesOf(UUID playerId) {
        TeamColor color = teamColor(playerId);
        List<UUID> out = new ArrayList<>();
        for (UUID p : participants) {
            if (!p.equals(playerId) && teamColors.get(p) == color) {
                out.add(p);
            }
        }
        return out;
    }

    /** @return true when the two players are on the same team (used to disable friendly fire). */
    public boolean areTeammates(UUID a, UUID b) {
        return teamColors.get(a) != null && teamColors.get(a) == teamColors.get(b);
    }

    /** @return the size of the given side. */
    public int teamSize(TeamColor color) {
        return team(color).size();
    }

    /** Marks a participant out of the fight (death or disconnect) — idempotent. */
    public void markEliminated(UUID playerId) {
        if (playerId != null) {
            eliminated.add(playerId);
        }
    }

    public boolean isEliminated(UUID playerId) {
        return playerId != null && eliminated.contains(playerId);
    }

    public boolean isTeamMatch() {
        return teamMatch;
    }

    public UUID arenaInstanceId() {
        return arenaInstanceId;
    }

    public void setArenaInstanceId(UUID arenaInstanceId) {
        this.arenaInstanceId = arenaInstanceId;
    }

    public int bestOf() {
        return bestOf;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    /**
     * @return the opposing player in a 1v1, or null for team matches (use {@link #team(TeamColor)}
     *         to enumerate the enemy team instead).
     */
    public UUID opponentOf(UUID uuid) {
        if (isTeamMatch()) {
            return null;
        }
        for (UUID participant : participants) {
            if (!participant.equals(uuid)) {
                return participant;
            }
        }
        return null;
    }

    public TeamColor teamColor(UUID playerId) {
        TeamColor color = playerId == null ? null : teamColors.get(playerId);
        if (color != null) {
            return color;
        }
        // Non-participants (spectators, stale lookups) default to RED; never NPE.
        return TeamColor.RED;
    }

    public void applySeries(Map<UUID, Integer> carry) {
        if (carry != null) {
            seriesWins.putAll(carry);
        }
    }

    /** Records a series win for the whole team of {@code playerId} (all teammates get +1). */
    public void addSeriesWin(UUID playerId) {
        TeamColor color = teamColor(playerId);
        for (UUID p : participants) {
            if (teamColors.get(p) == color) {
                seriesWins.merge(p, 1, Integer::sum);
            }
        }
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

    /** Ends a 1v1 match with a single winner, or a team match with the winner's team. */
    public void end(UUID winnerUuid, boolean isDraw) {
        this.winner = winnerUuid;
        this.draw = isDraw;
        if (winnerUuid != null) {
            this.winningTeam = teamColor(winnerUuid);
        }
        this.endedAt = Instant.now();
        this.state = MatchState.ENDING;
    }

    /** Ends a team match with an entire team declared the winner. */
    public void endTeamMatch(TeamColor winningTeamColor, boolean isDraw) {
        this.winningTeam = winningTeamColor;
        this.draw = isDraw;
        this.winner = null;
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

    public TeamColor winningTeam() {
        return winningTeam;
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

    /** Marks the match as decided by a disconnect forfeit (idempotent). */
    public void markDisconnectForfeit() {
        disconnectForfeit.set(true);
    }

    /** True when the outcome came from a disconnect — rematch offers are then pointless. */
    public boolean isDisconnectForfeit() {
        return disconnectForfeit.get();
    }

    public void setRematchRequested(UUID uuid, boolean value) {
        if (uuid == null) {
            return;
        }
        // In 1v1, A=index0 B=index1. In a team battle, either member of a side requesting
        // counts as that side's vote.
        TeamColor color = teamColor(uuid);
        if (color == TeamColor.RED) {
            rematchRequestedA = value;
        } else {
            rematchRequestedB = value;
        }
    }

    public boolean bothRematchRequested() {
        return rematchRequestedA && rematchRequestedB;
    }

    public boolean isRematchRequested(UUID uuid) {
        return teamColor(uuid) == TeamColor.RED ? rematchRequestedA : rematchRequestedB;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.shuttingDown = shuttingDown;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public String publicDuelId() {
        return publicDuelId;
    }

    public void setPublicDuelId(String publicDuelId) {
        this.publicDuelId = publicDuelId;
    }

    public String preferredArenaName() {
        return preferredArenaName;
    }

    public void setPreferredArenaName(String preferredArenaName) {
        this.preferredArenaName = preferredArenaName;
    }

    public boolean tryBeginRematch() {
        return rematchStarting.compareAndSet(false, true);
    }

    public boolean rematchStarting() {
        return rematchStarting.get();
    }

    /** Clears both sides' rematch votes (1v1 or team). */
    public void clearRematchRequests() {
        rematchRequestedA = false;
        rematchRequestedB = false;
    }

    /** Remember a fighter's inventory once (first write wins — typically at death). */
    public void rememberEndInventory(UUID playerId, byte[] serialized) {
        if (playerId == null || serialized == null) {
            return;
        }
        endInventories.putIfAbsent(playerId, serialized);
    }

    public byte[] endInventoryOf(UUID playerId) {
        return playerId == null ? null : endInventories.get(playerId);
    }

    public Map<UUID, byte[]> endInventoriesSnapshot() {
        return Map.copyOf(endInventories);
    }
}
