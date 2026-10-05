package com.rumilance.practice.arena;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Arena Paste Queue: how many arena copies may be pasted at the same time.
 *
 * <p>Pasting a schematic is the heaviest thing the server does outside of combat, and a burst of
 * near-simultaneous duel starts used to fire one paste per match with nothing throttling them.
 * Pastes beyond {@code maxConcurrent} wait their turn and run in the order they were asked for,
 * so the third copy of a burst starts once one of the first two finishes instead of piling on.
 *
 * <p>Pure bookkeeping — no Bukkit, no clock, no threads — so the behaviour can be pinned by a
 * unit test. The caller owns the actual pasting: {@link #tryStart(Object)} says whether to begin
 * right away, and every completion goes through {@link #onFinished()}, which hands back the next
 * queued paste (or {@code null}) to start in its place.</p>
 */
public final class ArenaPasteQueue<T> {

    private final int maxConcurrent;
    private final Deque<T> waiting = new ArrayDeque<>();

    private int active;

    /**
     * @param maxConcurrent how many pastes may run at once; anything beyond this queues.
     *                      Values below 1 are clamped to 1 (never stall the queue entirely).
     */
    public ArenaPasteQueue(int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    /** Pastes currently running. */
    public int active() {
        return active;
    }

    /** Pastes waiting for a free slot. */
    public int queued() {
        return waiting.size();
    }

    /**
     * Starts {@code paste} when a slot is free, otherwise queues it behind the ones already
     * waiting.
     *
     * @return {@code true} when the caller should start the paste immediately;
     *         {@code false} when it was queued and must not be started yet.
     */
    public boolean tryStart(T paste) {
        Objects.requireNonNull(paste, "paste");
        if (active < maxConcurrent) {
            active++;
            return true;
        }
        waiting.addLast(paste);
        return false;
    }

    /**
     * Records one paste as finished and, when that frees a slot, claims the next queued paste
     * for the caller to start.
     *
     * @return the next paste to run, or {@code null} when nothing is waiting (or every slot is
     *         still busy).
     */
    public T onFinished() {
        active = Math.max(0, active - 1);
        if (active >= maxConcurrent || waiting.isEmpty()) {
            return null;
        }
        active++;
        return waiting.pollFirst();
    }
}
