package com.rumilance.practice.session;

import com.rumilance.practice.state.MatchMode;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of all currently active {@link PlayerSession}s and {@link MatchSession}s.
 */
public final class SessionManager {

    private final Map<UUID, PlayerSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MatchSession> matchSessions = new ConcurrentHashMap<>();

    public PlayerSession createSession(UUID uuid, String locale) {
        return playerSessions.computeIfAbsent(uuid, id -> new PlayerSession(id, locale));
    }

    public Optional<PlayerSession> getSession(UUID uuid) {
        return Optional.ofNullable(playerSessions.get(uuid));
    }

    public void removeSession(UUID uuid) {
        playerSessions.remove(uuid);
    }

    public Collection<PlayerSession> allSessions() {
        return List.copyOf(playerSessions.values());
    }

    public MatchSession createMatch(MatchMode mode, String kitName, List<UUID> participants, UUID arenaInstanceId) {
        return createMatch(mode, kitName, participants, arenaInstanceId, 1);
    }

    public MatchSession createMatch(MatchMode mode, String kitName, List<UUID> participants,
                                    UUID arenaInstanceId, int bestOf) {
        MatchSession session = new MatchSession(
                UUID.randomUUID(), mode, kitName, participants, arenaInstanceId, bestOf);
        matchSessions.put(session.id(), session);
        for (UUID participant : participants) {
            getSession(participant).ifPresent(playerSession -> playerSession.setCurrentMatchId(session.id()));
        }
        return session;
    }

    public Optional<MatchSession> getMatch(UUID matchId) {
        return Optional.ofNullable(matchSessions.get(matchId));
    }

    public Optional<MatchSession> findMatchForPlayer(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return matchSessions.values().stream()
                .filter(match -> match.isParticipant(playerUuid))
                .findFirst();
    }

    public void removeMatch(UUID matchId) {
        MatchSession removed = matchSessions.remove(matchId);
        if (removed != null) {
            for (UUID participant : removed.participants()) {
                getSession(participant).ifPresent(playerSession -> {
                    if (matchId.equals(playerSession.currentMatchId())) {
                        playerSession.setCurrentMatchId(null);
                    }
                });
            }
        }
    }

    public Collection<MatchSession> allMatches() {
        return List.copyOf(matchSessions.values());
    }
}
