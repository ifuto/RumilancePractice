package com.rumilance.practice.ffa;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JST midnight rollover of FFA kill streaks. The clock is a mutable fixed clock so the
 * test can step a synthetic midnight without sleeping.
 */
class FfaDailyStreaksTest {

    /** Mutable clock: starts at a given instant, can be shifted forward. */
    private static final class ShiftClock extends Clock {
        private final ZoneId zone;
        private Instant instant;

        ShiftClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ShiftClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static ShiftClock atJst(int year, int month, int day, int hour, int minute) {
        // Build the instant that corresponds to the given JST wall time.
        ZoneId jst = FfaDailyStreaks.RESET_ZONE;
        Instant instant = java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, jst).toInstant();
        return new ShiftClock(instant, jst);
    }

    @Test
    void streakResetsExactlyAtMidnightJst() {
        // 2026-09-30 23:59 JST: build a streak...
        ShiftClock clock = atJst(2026, 9, 30, 23, 59);
        FfaDailyStreaks streaks = new FfaDailyStreaks(clock);
        UUID player = UUID.randomUUID();
        streaks.bump(player);
        streaks.bump(player);
        assertEquals(2, streaks.current(player));

        // ... step over 00:00 JST and it is gone.
        clock.advanceSeconds(70);
        assertEquals(0, streaks.current(player));
        assertTrue(streaks.snapshotPositive().isEmpty());

        // A kill in the new day starts a fresh streak (not 3).
        clock.advanceSeconds(3600);
        assertEquals(1, streaks.bump(player));
    }

    @Test
    void midnightUsesTokyoNotUtc() {
        // 2026-10-01 00:30 JST — still 15:30 UTC on 2026-09-30. A UTC-based reset would
        // NOT roll over yet; the spec is explicitly JST.
        ShiftClock clock = atJst(2026, 10, 1, 0, 30);
        FfaDailyStreaks streaks = new FfaDailyStreaks(clock);
        UUID player = UUID.randomUUID();
        streaks.bump(player);

        // 23:00 JST same day: UTC would have rolled at 09:00; streak must still be alive.
        clock.advanceSeconds(22L * 3600L + 30L * 60L); // to 2026-10-01 23:00 JST
        assertEquals(1, streaks.current(player));
    }

    @Test
    void onlyDeathOrMidnightCutsTheStreak() {
        // Leave/relog/arena-reset no longer clear the streak (no per-player remove API
        // besides reset); the death-reset returns it to zero but the day stamp stays.
        ShiftClock clock = atJst(2026, 9, 30, 12, 0);
        FfaDailyStreaks streaks = new FfaDailyStreaks(clock);
        UUID player = UUID.randomUUID();
        streaks.bump(player);
        streaks.bump(player);
        streaks.bump(player);
        assertEquals(3, streaks.current(player));
        streaks.reset(player); // death
        assertEquals(0, streaks.current(player));
        assertTrue(streaks.snapshotPositive().isEmpty());
        assertEquals(1, streaks.bump(player));
    }

    @Test
    void staleRowsAreDroppedAtRolloverButReadsWereAlreadyZero() {
        ShiftClock clock = atJst(2026, 9, 30, 23, 59);
        FfaDailyStreaks streaks = new FfaDailyStreaks(clock);
        UUID player = UUID.randomUUID();
        streaks.bump(player);
        clock.advanceSeconds(120);
        assertEquals(0, streaks.current(player));
        streaks.dropStale();
        assertTrue(streaks.snapshotPositive().isEmpty());
        // And bumping after rollover starts from 1, so the stale row cannot double-count.
        assertEquals(1, streaks.bump(player));
    }

    @Test
    void snapshotOnlyListsPositiveTodayEntries() {
        ShiftClock clock = atJst(2026, 9, 30, 10, 0);
        FfaDailyStreaks streaks = new FfaDailyStreaks(clock);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        streaks.bump(a);
        streaks.bump(a);
        streaks.bump(b);
        streaks.reset(c); // died at 0: not listed
        assertEquals(2, streaks.snapshotPositive().size());
        assertEquals(2, streaks.snapshotPositive().stream()
                .filter(e -> e.getKey().equals(a)).findFirst().orElseThrow().getValue().intValue());
        assertEquals(1, streaks.snapshotPositive().stream()
                .filter(e -> e.getKey().equals(b)).findFirst().orElseThrow().getValue().intValue());
    }

    @Test
    void serverDefaultUsesTokyoZone() {
        assertEquals(ZoneId.of("Asia/Tokyo"), FfaDailyStreaks.RESET_ZONE);
        assertNotNull(FfaDailyStreaks.serverDefault());
    }
}
