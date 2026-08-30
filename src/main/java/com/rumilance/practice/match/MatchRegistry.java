package com.rumilance.practice.match;

import com.rumilance.practice.session.MatchSession;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents the same player or arena instance from being bound to multiple live matches.
 */
public final class MatchRegistry {

    private final Map<UUID, MatchSession> matches = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToMatch = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> arenaToMatch = new ConcurrentHashMap<>();

    public synchronized boolean register(MatchSession session) {
        Objects.requireNonNull(session, "session");
        for (UUID participant : session.participants()) {
            if (playerToMatch.containsKey(participant)) {
                return false;
            }
        }
        if (session.arenaInstanceId() != null && arenaToMatch.containsKey(session.arenaInstanceId())) {
            return false;
        }
        matches.put(session.id(), session);
        for (UUID participant : session.participants()) {
            playerToMatch.put(participant, session.id());
        }
        if (session.arenaInstanceId() != null) {
            arenaToMatch.put(session.arenaInstanceId(), session.id());
        }
        return true;
    }

    public synchronized boolean tryReserveArena(UUID arenaInstanceId, UUID matchId) {
        UUID existing = arenaToMatch.putIfAbsent(arenaInstanceId, matchId);
        return existing == null || existing.equals(matchId);
    }

    public synchronized void bindArena(UUID matchId, UUID arenaInstanceId) {
        MatchSession session = matches.get(matchId);
        if (session != null) {
            session.setArenaInstanceId(arenaInstanceId);
            arenaToMatch.put(arenaInstanceId, matchId);
        }
    }

    public Optional<MatchSession> get(UUID matchId) {
        return Optional.ofNullable(matches.get(matchId));
    }

    public Optional<MatchSession> byPlayer(UUID playerId) {
        UUID matchId = playerToMatch.get(playerId);
        return matchId == null ? Optional.empty() : Optional.ofNullable(matches.get(matchId));
    }

    public synchronized void unregister(UUID matchId) {
        MatchSession session = matches.remove(matchId);
        if (session == null) {
            return;
        }
        for (UUID participant : session.participants()) {
            playerToMatch.remove(participant, matchId);
        }
        if (session.arenaInstanceId() != null) {
            arenaToMatch.remove(session.arenaInstanceId(), matchId);
        }
    }

    public Collection<MatchSession> all() {
        return matches.values();
    }

    public int activeCount() {
        return matches.size();
    }

    public boolean isPlayerInMatch(UUID playerId) {
        return playerToMatch.containsKey(playerId);
    }
}
