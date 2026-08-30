package com.rumilance.practice.punishment;

import java.time.Duration;

/**
 * Escalating ChatBan durations for repeated spam-filter offences: 1st = 2 weeks, 2nd = 1 month,
 * 3rd and beyond = 3 months. Kept intentionally conservative so a false positive is short-lived
 * while repeat abuse ramps up quickly.
 */
public final class SpamBanDuration {

    private SpamBanDuration() {
    }

    /** @param offenseNumber 1-based count of spam ChatBans (including the one being issued). */
    public static Duration forOffense(int offenseNumber) {
        if (offenseNumber <= 1) {
            return Duration.ofDays(14);
        }
        if (offenseNumber == 2) {
            return Duration.ofDays(30);
        }
        return Duration.ofDays(90);
    }
}
