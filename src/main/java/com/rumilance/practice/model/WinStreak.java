package com.rumilance.practice.model;

import java.util.UUID;

/**
 * Ranked and unranked share one win-streak row per player. {@code monthBest} is the highest
 * streak reached during {@code monthKey} (yyyy-MM).
 */
public record WinStreak(
        UUID playerId,
        String username,
        int currentStreak,
        int bestStreak,
        String monthKey,
        int monthBest
) {
    public WinStreak {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId");
        }
        username = username == null ? "" : username;
        monthKey = monthKey == null ? "" : monthKey;
        if (currentStreak < 0 || bestStreak < 0 || monthBest < 0) {
            throw new IllegalArgumentException("streaks must not be negative");
        }
    }

    public static WinStreak empty(UUID playerId, String username, String monthKey) {
        return new WinStreak(playerId, username == null ? "" : username, 0, 0, monthKey, 0);
    }
}
