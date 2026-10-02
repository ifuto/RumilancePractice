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
            String arenaName,
            int firstTo,
            /** 中キット (inner kit) preset id; null = the kit's own default loadout. */
            String innerKitName,
            /** KB 選択 (KbProfileService の CHOICE_* はそのまま保持; null = 既定/デフォルトKB。 */
            String kbChoice,
            /**
             * 戦闘モード選択 ({@code "java"} / {@code "bedrock"})。
             * null = 自動判定（同一プラットフォーム同士ならデフォルト、クロスなら送信者優先）。
             */
            String combatMode
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

        /** 戦闘モードを CombatMode enum で返す。null なら自動判定。 */
        public com.rumilance.practice.combat.CombatMode resolvedCombatMode() {
            if (combatMode == null || combatMode.isBlank()) return null;
            return com.rumilance.practice.combat.CombatMode.fromString(combatMode);
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
        return create(sender, target, kit, ranked, bestOf, null,
                com.rumilance.practice.match.FirstTo.UNLIMITED);
    }

    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName
    ) {
        return create(sender, target, kit, ranked, bestOf, arenaName,
                com.rumilance.practice.match.FirstTo.UNLIMITED);
    }

    /** Duel request with an FT (先取点数). Queue never carries one. */
    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName,
            int firstTo
    ) {
        return create(sender, target, kit, ranked, bestOf, arenaName, firstTo, null);
    }

    /**
     * Duel request with a 中キット (inner kit): {@code innerKit} names the preset both fighters
     * use, {@code null} / blank / {@code default} keeps the kit's own loadout. Queue never carries
     * a preset — only a duel (or a party fight, which has its own path) can ask for one.
     */
    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName,
            int firstTo, String innerKit
    ) {
        return create(sender, target, kit, ranked, bestOf, arenaName, firstTo, innerKit, null);
    }

    /** Duel request carrying a KB profile choice (Duel Request GUI の KB セレクタ). */
    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName,
            int firstTo, String innerKit, String kbChoice
    ) {
        return create(sender, target, kit, ranked, bestOf, arenaName, firstTo, innerKit, kbChoice,
                null);
    }

    /**
     * Duel request carrying KB + combat mode choice.
     * {@code combatMode}: {@code "java"} / {@code "bedrock"} / {@code null} (auto).
     */
    public synchronized Optional<RichDuelRequest> create(
            UUID sender, UUID target, String kit, boolean ranked, int bestOf, String arenaName,
            int firstTo, String innerKit, String kbChoice, String combatMode
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
                map,
                com.rumilance.practice.match.FirstTo.normalise(firstTo),
                com.rumilance.practice.kit.InnerKitService.isDefault(innerKit)
                        ? null
                        : com.rumilance.practice.kit.InnerKitService.normalizeId(innerKit),
                kbChoice == null || kbChoice.isBlank() ? null : kbChoice,
                combatMode == null || combatMode.isBlank() ? null : combatMode
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
