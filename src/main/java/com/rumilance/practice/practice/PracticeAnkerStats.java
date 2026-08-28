package com.rumilance.practice.practice;

import java.util.ArrayList;
import java.util.List;

/**
 * Timing samples collected during an ANKER practice run.
 */
public final class PracticeAnkerStats {

    private final List<Long> clickIntervalsMs = new ArrayList<>();
    private final List<Long> explodeToPlaceMs = new ArrayList<>();
    private final List<Long> placeToChargeMs = new ArrayList<>();
    private final List<Long> chargeToExplodeMs = new ArrayList<>();

    private long lastClickMs;
    private long lastExplodeMs;
    private long lastPlaceMs;
    private long lastChargeMs;
    private int clicks;

    public void recordClick(long nowMs) {
        clicks++;
        if (lastClickMs > 0L) {
            clickIntervalsMs.add(nowMs - lastClickMs);
        }
        lastClickMs = nowMs;
    }

    public void recordPlace(long nowMs) {
        if (lastExplodeMs > 0L) {
            explodeToPlaceMs.add(nowMs - lastExplodeMs);
        }
        lastPlaceMs = nowMs;
        lastChargeMs = 0L;
    }

    public void recordCharge(long nowMs) {
        if (lastPlaceMs > 0L && lastChargeMs == 0L) {
            placeToChargeMs.add(nowMs - lastPlaceMs);
        }
        lastChargeMs = nowMs;
    }

    public void recordExplode(long nowMs) {
        if (lastChargeMs > 0L) {
            chargeToExplodeMs.add(nowMs - lastChargeMs);
        }
        lastExplodeMs = nowMs;
        lastPlaceMs = 0L;
        lastChargeMs = 0L;
    }

    public int clicks() {
        return clicks;
    }

    public double avgClickIntervalMs() {
        return average(clickIntervalsMs);
    }

    /** Rough CPS from average inter-click interval. */
    public double avgCps() {
        double avg = avgClickIntervalMs();
        return avg <= 0.0d ? 0.0d : 1000.0d / avg;
    }

    public double avgExplodeToPlaceMs() {
        return average(explodeToPlaceMs);
    }

    public double avgPlaceToChargeMs() {
        return average(placeToChargeMs);
    }

    public double avgChargeToExplodeMs() {
        return average(chargeToExplodeMs);
    }

    private static double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        long sum = 0L;
        for (Long v : values) {
            sum += v;
        }
        return (double) sum / values.size();
    }
}
