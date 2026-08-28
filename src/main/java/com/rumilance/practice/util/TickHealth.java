package com.rumilance.practice.util;

/**
 * Main-thread tick health. When MSPT rises the server is late, not the client: treating that
 * as a packet burst rubberbands fighters. Background work should yield until ticks recover.
 */
public final class TickHealth {

    /** A healthy tick is 50ms. Above this we treat the server as lagging. */
    public static final double LAG_MSPT = 55.0d;
    private static final double EMA_ALPHA = 0.3d;

    private static volatile double emaMspt = 50.0d;
    private static volatile long lastTickNanos = 50_000_000L;
    private static volatile boolean lagging;

    private TickHealth() {
    }

    /** {@code durationMs} is {@code ServerTickEndEvent#getTickDuration()} (milliseconds). */
    public static void record(double durationMs) {
        if (durationMs < 0.0d || Double.isNaN(durationMs)) {
            return;
        }
        double clamped = Math.min(500.0d, durationMs);
        emaMspt = emaMspt + EMA_ALPHA * (clamped - emaMspt);
        lastTickNanos = (long) (clamped * 1_000_000.0d);
        lagging = emaMspt >= LAG_MSPT || clamped >= LAG_MSPT;
    }

    public static boolean lagging() {
        return lagging;
    }

    public static long lastTickNanos() {
        return lastTickNanos;
    }

    public static double emaMspt() {
        return emaMspt;
    }

    static void resetForTest() {
        emaMspt = 50.0d;
        lastTickNanos = 50_000_000L;
        lagging = false;
    }
}
