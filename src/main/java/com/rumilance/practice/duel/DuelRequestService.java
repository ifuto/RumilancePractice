package com.rumilance.practice.duel;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending duel requests with expiry, rate limiting and invalidation on match entry.
 */
public final class DuelRequestService {

    public record RichDuelRequest(
            UUID id,
            UUID sender,
            UUID target,
            String kitName,
            boolean ranked,
            int bestOf,
            Instant createdAt,
            Instant expiresAt,
            String arenaName
    ) {
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }

        /** Preferred map template, or empty when random / unset. */
        public Optional<String> preferredArena() {
            if (arenaName == null || arenaName.isBlank() || "random".equalsIgnoreCase(arenaName)) {
                return Optional.empty();
            }
            return Optional.of(arenaName);
        }
    }

    public static final long DEFAULT_RATE_LIMIT_MS = 30_000L;

    private final Map<UUID, RichDuelRequest> byId = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Long>> lastSendMillis = new ConcurrentHashMap<>();
    private final long ttlSeconds;
    private final long rateLimitMillis;

    public DuelRequestService(long ttlSeconds, long rateLimitMillis) {
        this.ttlSeconds = ttlSeconds;
        this.rateLimitMillis = rateLimitMillis;
    }

    /** Seconds left before {@code sender} may request {@code target} again; 0 if ready. */
    public int remainingCooldownSeconds(UUID sender, UUID target) {
        if (sender == null || target == null) {
            return 0;
        }
        Map<UUID, Long> byTarget = lastSendMillis.get(sender);
        if (byTarget == null) {
            return 0;
        }
        Long last = byTarget.get(target);
        if (last == null) {
            return 0;
        }
        long left = rateLimitMillis - (System.currentTimeMillis() - last);
        if (left <= 0) {
            return 0;
        }
        return (int) ((left + 999L) / 1000L);
    }

    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf
    ) {
        return create(sender, target, kit, ranked, bestOf, null);
    }

    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName
    ) {
        if (sender.equals(target)) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        Map<UUID, Long> byTarget = lastSendMillis.computeIfAbsent(sender, id -> new ConcurrentHashMap<>());
        Long last = byTarget.get(target);
        if (last != null && now - last < rateLimitMillis) {
            return Optional.empty();
        }
        Instant created = Instant.now();
        String map = arenaName == null || arenaName.isBlank() || "random".equalsIgnoreCase(arenaName)
                ? null
                : arenaName;
        RichDuelRequest request = new RichDuelRequest(
                UUID.randomUUID(), sender, target, kit, ranked,
                Math.max(1, bestOf),
                created,
                created.plusSeconds(ttlSeconds),
                map
        );
        byId.put(request.id(), request);
        byTarget.put(target, now);
        return Optional.of(request);
    }

    public Optional<RichDuelRequest> get(UUID id) {
        purgeExpired();
        return Optional.ofNullable(byId.get(id)).filter(r -> !r.isExpired(Instant.now()));
    }

    public Optional<RichDuelRequest> latestForTarget(UUID target) {
        purgeExpired();
        return byId.values().stream()
                .filter(r -> r.target().equals(target) && !r.isExpired(Instant.now()))
                .max(Comparator.comparing(RichDuelRequest::createdAt));
    }

    public Optional<RichDuelRequest> latestFromSenderToTarget(UUID sender, UUID target) {
        purgeExpired();
        return byId.values().stream()
                .filter(r -> r.sender().equals(sender) && r.target().equals(target) && !r.isExpired(Instant.now()))
                .max(Comparator.comparing(RichDuelRequest::createdAt));
    }

    public Optional<RichDuelRequest> latestOutgoing(UUID sender) {
        purgeExpired();
        return byId.values().stream()
                .filter(r -> r.sender().equals(sender) && !r.isExpired(Instant.now()))
                .max(Comparator.comparing(RichDuelRequest::createdAt));
    }

    public List<RichDuelRequest> incoming(UUID target) {
        purgeExpired();
        return byId.values().stream()
                .filter(r -> r.target().equals(target) && !r.isExpired(Instant.now()))
                .sorted(Comparator.comparing(RichDuelRequest::createdAt).reversed())
                .toList();
    }

    public boolean cancel(UUID requestId) {
        return byId.remove(requestId) != null;
    }

    public void invalidateForPlayer(UUID playerId) {
        byId.entrySet().removeIf(e ->
                e.getValue().sender().equals(playerId) || e.getValue().target().equals(playerId));
    }

    public boolean accept(UUID requestId) {
        RichDuelRequest request = byId.remove(requestId);
        return request != null && !request.isExpired(Instant.now());
    }

    public void denyAll(UUID target) {
        byId.entrySet().removeIf(e -> e.getValue().target().equals(target));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        byId.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }
}
