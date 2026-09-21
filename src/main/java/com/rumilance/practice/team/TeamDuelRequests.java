package com.rumilance.practice.team;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Team Duel Requests — a party owner challenges another party's owner, team against team.
 *
 * <p>One pending challenge per target party: the target owner sees a single accept/deny choice
 * instead of a stack, and a fresh challenge from anyone replaces the old one. Requests expire on
 * their own, and every method takes the current time (or uses the injected clock) so the expiry
 * rule is testable without waiting a minute in a test.</p>
 */
public final class TeamDuelRequests {

    /** A pending challenge. */
    public record Request(
            UUID id,
            UUID fromParty,
            String fromPartyName,
            UUID fromOwner,
            UUID toParty,
            Instant createdAt,
            Instant expiresAt
    ) {
        public boolean isExpired(Instant now) {
            return now != null && !now.isBefore(expiresAt);
        }
    }

    /** Why a challenge could not be sent. */
    public enum Outcome {
        SENT,
        /** The same party already has a challenge out to this target. */
        ALREADY_PENDING,
        /** The challenger cannot challenge itself. */
        SELF,
        /** Something was missing (no party, no target). */
        INVALID
    }

    /** How long a challenge stays open before it expires. */
    public static final Duration TTL = Duration.ofSeconds(60);

    private final Map<UUID, Request> byTarget = new LinkedHashMap<>();
    private final Supplier<Instant> clock;

    public TeamDuelRequests() {
        this(Instant::now);
    }

    public TeamDuelRequests(Supplier<Instant> clock) {
        this.clock = clock == null ? Instant::now : clock;
    }

    /**
     * Opens a challenge from one party to another, replacing any pending challenge for the same
     * target party.
     */
    public synchronized Outcome send(UUID fromParty, String fromPartyName, UUID fromOwner, UUID toParty) {
        if (fromParty == null || toParty == null) {
            return Outcome.INVALID;
        }
        if (fromParty.equals(toParty)) {
            return Outcome.SELF;
        }
        sweep(clock.get());
        Request existing = byTarget.get(toParty);
        if (existing != null && existing.fromParty().equals(fromParty)) {
            return Outcome.ALREADY_PENDING;
        }
        Instant now = clock.get();
        byTarget.put(toParty, new Request(UUID.randomUUID(), fromParty, fromPartyName, fromOwner, toParty,
                now, now.plus(TTL)));
        return Outcome.SENT;
    }

    /** The live challenge waiting for this party's owner, if any. */
    public synchronized Optional<Request> pendingFor(UUID toParty) {
        if (toParty == null) {
            return Optional.empty();
        }
        sweep(clock.get());
        return Optional.ofNullable(byTarget.get(toParty));
    }

    /** Consumes the pending challenge for this party (accept). */
    public synchronized Optional<Request> accept(UUID toParty) {
        if (toParty == null) {
            return Optional.empty();
        }
        sweep(clock.get());
        return Optional.ofNullable(byTarget.remove(toParty));
    }

    /** Discards the pending challenge for this party (deny). */
    public synchronized boolean deny(UUID toParty) {
        if (toParty == null) {
            return false;
        }
        sweep(clock.get());
        return byTarget.remove(toParty) != null;
    }

    /** Cancels every challenge a party sent out (it disbanded or left the queue). */
    public synchronized int cancelFrom(UUID fromParty) {
        if (fromParty == null) {
            return 0;
        }
        int before = byTarget.size();
        byTarget.values().removeIf(request -> request.fromParty().equals(fromParty));
        return before - byTarget.size();
    }

    /** Drops expired challenges; returns how many went. */
    public synchronized int sweep(Instant now) {
        Instant reference = now == null ? clock.get() : now;
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Request> entry : byTarget.entrySet()) {
            if (entry.getValue().isExpired(reference)) {
                expired.add(entry.getKey());
            }
        }
        expired.forEach(byTarget::remove);
        return expired.size();
    }

    public synchronized int pendingCount() {
        return byTarget.size();
    }

    public synchronized void clear() {
        byTarget.clear();
    }
}
