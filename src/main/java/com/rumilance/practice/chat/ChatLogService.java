package com.rumilance.practice.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A rolling in-memory buffer of recent chat lines, so a report can be resolved to its
 * surroundings <em>at review time</em> instead of snapshotting them when it was filed.
 *
 * <p>Each line gets a monotonically increasing id; the id is what the chat click event carries
 * and what a report stores. When the buffer has scrolled past an id the line is simply gone —
 * the report then shows what it still has (sender, timestamp) and says the context expired.
 * That is deliberate: nothing was copied at report time, so nothing can go stale.</p>
 *
 * <p>Pure (no Bukkit) and synchronised on the buffer, so JUnit can drive it directly.</p>
 */
public final class ChatLogService {

    /** One recorded chat line. */
    public record ChatLine(long id, UUID senderId, String senderName, String message,
                           long timestampMillis) {
    }

    private final int capacity;
    private final Deque<ChatLine> lines = new ArrayDeque<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public ChatLogService(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records a line and returns the id a report should store. */
    public synchronized long record(UUID senderId, String senderName, String message,
                                    long timestampMillis) {
        long id = nextId.getAndIncrement();
        lines.addLast(new ChatLine(id, senderId, senderName, message, timestampMillis));
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
        return id;
    }

    public synchronized Optional<ChatLine> find(long id) {
        for (ChatLine line : lines) {
            if (line.id() == id) {
                return Optional.of(line);
            }
        }
        return Optional.empty();
    }

    /**
     * The {@code before} lines preceding and {@code after} lines following {@code id},
     * oldest first, including the line itself. Missing edges simply yield fewer lines.
     */
    public synchronized List<ChatLine> context(long id, int before, int after) {
        List<ChatLine> snapshot = new ArrayList<>(lines);
        int index = -1;
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).id() == id) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return List.of();
        }
        int from = Math.max(0, index - Math.max(0, before));
        int to = Math.min(snapshot.size(), index + Math.max(0, after) + 1);
        return List.copyOf(snapshot.subList(from, to));
    }

    public synchronized int size() {
        return lines.size();
    }
}
