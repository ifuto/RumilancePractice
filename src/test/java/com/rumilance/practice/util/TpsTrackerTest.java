package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic behind {@code /tps}: the 20-minute average turned into a percentage, and the
 * dip history used to answer "did TPS drop to 17 or below in the last 24 hours, and when".
 * {@link TpsTracker} takes the clock as an argument, so no server is involved.
 */
final class TpsTrackerTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long STEP = 20_000L;

    @Test
    void aPerfectServerReadsOneHundredPercent() {
        TpsTracker tracker = new TpsTracker();
        for (int i = 0; i < 60; i++) {
            tracker.sample(20.0d, T0 + i * STEP);
        }
        assertEquals(20.0d, tracker.averageTps(), 1e-9);
        assertEquals(100.0d, tracker.averagePercent(), 1e-9);
    }

    @Test
    void theAverageCoversTheLastTwentyMinutesOnly() {
        TpsTracker tracker = new TpsTracker();
        // 60 samples of 10 TPS, then 60 of 20 TPS: the old ones must fall out of the window.
        for (int i = 0; i < 60; i++) {
            tracker.sample(10.0d, T0 + i * STEP);
        }
        assertEquals(10.0d, tracker.averageTps(), 1e-9);
        for (int i = 60; i < 120; i++) {
            tracker.sample(20.0d, T0 + i * STEP);
        }
        assertEquals(20.0d, tracker.averageTps(), 1e-9, "the 10 TPS samples are older than 20 minutes");
        assertEquals(100.0d, tracker.averagePercent(), 1e-9);
    }

    @Test
    void msptIsConvertedTheSameWayTickHealthDoesIt() {
        assertEquals(20.0d, TpsTracker.tpsFromMspt(50.0d), 1e-9);
        assertEquals(20.0d, TpsTracker.tpsFromMspt(0.0d), 1e-9, "a missing reading counts as healthy");
        assertEquals(10.0d, TpsTracker.tpsFromMspt(100.0d), 1e-9);
    }

    @Test
    void aDipRecordsTheSpanItLasted() {
        TpsTracker tracker = new TpsTracker();
        tracker.sample(20.0d, T0);
        tracker.sample(15.0d, T0 + STEP);
        tracker.sample(16.0d, T0 + 2 * STEP);
        tracker.sample(20.0d, T0 + 3 * STEP);

        List<TpsTracker.Dip> dips = tracker.recentDips(T0 + 3 * STEP);
        assertEquals(1, dips.size());
        assertEquals(T0 + STEP, dips.get(0).startMillis());
        assertEquals(T0 + 3 * STEP, dips.get(0).endMillis());
        assertTrue(!dips.get(0).ongoing(), "the dip closed when TPS recovered");
    }

    @Test
    void exactlySeventeenCountsAsADipAndJustAboveDoesNot() {
        TpsTracker tracker = new TpsTracker();
        tracker.sample(17.0d, T0);
        assertEquals(1, tracker.recentDips(T0).size(), "17.0 or below counts");

        TpsTracker healthy = new TpsTracker();
        healthy.sample(17.01d, T0);
        assertTrue(healthy.recentDips(T0).isEmpty(), "just above the threshold does not");
    }

    @Test
    void aDipStillRunningIsReportedAsOngoing() {
        TpsTracker tracker = new TpsTracker();
        tracker.sample(12.0d, T0);
        tracker.sample(12.0d, T0 + STEP);

        List<TpsTracker.Dip> dips = tracker.recentDips(T0 + STEP);
        assertEquals(1, dips.size(), "one continuous dip, not two samples' worth");
        assertTrue(dips.get(0).ongoing());
    }

    @Test
    void historyOlderThanADayIsForgotten() {
        TpsTracker tracker = new TpsTracker();
        tracker.sample(10.0d, T0);
        tracker.sample(20.0d, T0 + STEP);

        // A whole day past the end of the dip, not exactly on the boundary: something that
        // finished precisely 24 hours ago is still "within 24 hours" and is kept.
        long dayLater = T0 + TpsTracker.HISTORY_MS + 2 * STEP;
        assertTrue(tracker.recentDips(dayLater).isEmpty(),
                "a dip from more than 24 hours ago is not reported");
        tracker.sample(20.0d, dayLater);
        assertTrue(tracker.recentDips(dayLater).isEmpty());
    }
}
