package com.rumilance.practice.stats;

import com.rumilance.practice.model.WinStreak;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Pure ranked+unranked streak rules: a win increments the shared current streak, a loss
 * resets it to zero, draws are ignored by the caller. Month-best rolls over when the key changes.
 */
public final class WinStreakMath {

    private WinStreakMath() {
    }

    public static String monthKey(LocalDate date) {
        YearMonth month = YearMonth.from(date == null ? LocalDate.now() : date);
        return month.toString();
    }

    public static String currentMonthKey() {
        return monthKey(LocalDate.now());
    }

    public static WinStreak apply(WinStreak previous, UUID playerId, String username, boolean win, String monthKey) {
        String key = monthKey == null || monthKey.isBlank() ? currentMonthKey() : monthKey;
        WinStreak base = previous == null
                ? WinStreak.empty(playerId, username, key)
                : previous;
        String name = username == null || username.isBlank() ? base.username() : username;
        int monthBest = key.equals(base.monthKey()) ? base.monthBest() : 0;
        if (win) {
            int current = base.currentStreak() + 1;
            return new WinStreak(
                    base.playerId(),
                    name,
                    current,
                    Math.max(base.bestStreak(), current),
                    key,
                    Math.max(monthBest, current)
            );
        }
        return new WinStreak(base.playerId(), name, 0, base.bestStreak(), key, monthBest);
    }
}
