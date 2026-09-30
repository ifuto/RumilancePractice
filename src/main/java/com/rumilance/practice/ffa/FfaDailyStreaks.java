package com.rumilance.practice.ffa;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Day-scoped FFA kill streaks ("連勝は日本時間 0:00 にリセット"). Every entry carries the
 * JST day it belongs to, so the 00:00 rollover is exact even without a timed task: reads
 * simply treat any stale-day entry as zero. Arena resets, /ffa leave and relogs no longer
 * touch the streak — it only dies at midnight JST (or when the player dies).
 *
 * <p>Bukkit-free on purpose so JUnit can walk across a synthetic midnight and prove the
 * rollover (see {@code FfaDailyStreaksTest}).</p>
 */
public final class FfaDailyStreaks {

    /** The reset boundary is Japan-local midnight ("日本時間0時0分にリセットね"). */
    public static final ZoneId RESET_ZONE = ZoneId.of("Asia/Tokyo");

    private record Entry(LocalDate day, int streak) {
    }

    private final Clock clock;
    private final ConcurrentMap<UUID, Entry> streaks = new ConcurrentHashMap<>();

    public FfaDailyStreaks(Clock clock) {
        this.clock = clock;
    }

    public static FfaDailyStreaks serverDefault() {
        return new FfaDailyStreaks(Clock.system(RESET_ZONE));
    }

    /** The JST day an entry written right now would belong to. */
    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Current streak, {@code 0} when absent or from a previous JST day. */
    public int current(UUID playerId) {
        Entry entry = streaks.get(playerId);
        return entry != null && entry.day().equals(today()) ? entry.streak() : 0;
    }

    /** Death: own streak back to zero, stamped on today. */
    public void reset(UUID playerId) {
        streaks.put(playerId, new Entry(today(), 0));
    }

    /** Kill: extends today's streak atomically (a stale day counts as zero first). */
    public int bump(UUID playerId) {
        LocalDate day = today();
        return streaks.merge(playerId, new Entry(day, 1),
                (old, init) -> old.day().equals(day)
                        ? new Entry(day, old.streak() + 1)
                        : init).streak();
    }

    /** Snapshot of id → streak for entries that are alive today and positive. */
    public List<Map.Entry<UUID, Integer>> snapshotPositive() {
        LocalDate day = today();
        List<Map.Entry<UUID, Integer>> out = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : streaks.entrySet()) {
            if (entry.getValue().day().equals(day) && entry.getValue().streak() > 0) {
                out.add(Map.entry(entry.getKey(), entry.getValue().streak()));
            }
        }
        return out;
    }

    /** Drops stale-day rows (memory hygiene; reads already treat them as zero). */
    public void dropStale() {
        LocalDate day = today();
        streaks.entrySet().removeIf(e -> !e.getValue().day().equals(day));
    }

    /** Full wipe (plugin shutdown / reload). */
    public void clear() {
        streaks.clear();
    }
}
