package com.rumilance.practice.team;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Team Fight Queue — own team against a randomly drawn opponent team.
 *
 * <p>The whole matching rule lives here, with no Bukkit in sight: entries wait in FIFO order,
 * the oldest one picks an opponent at random among the parties it can actually fight (size
 * within {@link #SIZE_TOLERANCE}), and both leave the queue together. Injecting the clock and
 * the {@link Random} keeps every branch testable, including "who did it pick".</p>
 *
 * <p>Stale entries (a party that queued and then fell apart without cancelling) are dropped on
 * every poll so the queue can never hand a match to a party that no longer exists.</p>
 */
public final class TeamFightQueue {

    /** A waiting party. */
    public record Entry(UUID partyId, int size, Instant queuedAt) {
    }

    /** Two parties matched against each other. */
    public record Match(UUID partyA, UUID partyB) {
    }

    /** Entries older than this are considered abandoned and dropped. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);

    /** Maximum size difference two parties may have and still be matched. */
    public static final int SIZE_TOLERANCE = 2;

    private final Deque<Entry> waiting = new ArrayDeque<>();
    private final Supplier<Instant> clock;
    private final Random random;

    public TeamFightQueue() {
        this(Instant::now, new Random());
    }

    public TeamFightQueue(Supplier<Instant> clock, Random random) {
        this.clock = clock == null ? Instant::now : clock;
        this.random = random == null ? new Random() : random;
    }

    /**
     * Adds a party to the queue. Re-queueing replaces the previous entry (and moves the party
     * to the back of the line), so a party can never be waiting twice.
     *
     * @return true when the party was not already queued
     */
    public synchronized boolean enqueue(UUID partyId, int size) {
        if (partyId == null) {
            return false;
        }
        boolean fresh = waiting.removeIf(entry -> entry.partyId().equals(partyId));
        waiting.addLast(new Entry(partyId, Math.max(1, size), clock.get()));
        return !fresh;
    }

    /** Removes a party from the queue. */
    public synchronized boolean cancel(UUID partyId) {
        return partyId != null && waiting.removeIf(entry -> entry.partyId().equals(partyId));
    }

    public synchronized boolean isQueued(UUID partyId) {
        return partyId != null && waiting.stream().anyMatch(entry -> entry.partyId().equals(partyId));
    }

    /**
     * Matches the oldest waiting party against a random compatible opponent.
     *
     * @return the matched pair, or empty when nobody compatible is waiting
     */
    public synchronized Optional<Match> pollMatch() {
        sweep(clock.get());
        int attempts = waiting.size();
        while (!waiting.isEmpty() && attempts-- > 0) {
            Entry first = waiting.peekFirst();
            List<Entry> candidates = new ArrayList<>();
            for (Entry other : waiting) {
                if (other == first) {
                    continue;
                }
                if (Math.abs(other.size() - first.size()) <= SIZE_TOLERANCE) {
                    candidates.add(other);
                }
            }
            if (candidates.isEmpty()) {
                // The oldest party has nobody it can fight yet; keep it waiting and try the
                // next oldest so one oversized party cannot stall the whole line. Bounded by
                // the queue size, so parties that can never match just stay queued.
                waiting.removeFirst();
                waiting.addLast(first);
                continue;
            }
            Entry chosen = candidates.get(random.nextInt(candidates.size()));
            waiting.remove(first);
            waiting.remove(chosen);
            return Optional.of(new Match(first.partyId(), chosen.partyId()));
        }
        return Optional.empty();
    }

    /** Drops entries that have been waiting longer than {@link #STALE_AFTER}. */
    public synchronized int sweep(Instant now) {
        Instant reference = now == null ? clock.get() : now;
        int before = waiting.size();
        waiting.removeIf(entry -> Duration.between(entry.queuedAt(), reference).compareTo(STALE_AFTER) > 0);
        return before - waiting.size();
    }

    public synchronized int waitingCount() {
        return waiting.size();
    }

    public synchronized List<Entry> snapshot() {
        return List.copyOf(waiting);
    }

    public synchronized void clear() {
        waiting.clear();
    }

    /** True when both sides of the match are still waiting — used before starting a fight. */
    public static boolean isSane(Match match) {
        return match != null
                && Objects.nonNull(match.partyA())
                && Objects.nonNull(match.partyB())
                && !match.partyA().equals(match.partyB());
    }
}
