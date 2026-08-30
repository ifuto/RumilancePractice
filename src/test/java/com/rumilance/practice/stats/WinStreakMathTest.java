package com.rumilance.practice.stats;

import com.rumilance.practice.model.WinStreak;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WinStreakMathTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void monthKeyIsYearMonth() {
        assertEquals("2026-08", WinStreakMath.monthKey(LocalDate.of(2026, 8, 22)));
    }

    @Test
    void winsIncrementSharedStreakAndMonthBest() {
        WinStreak first = WinStreakMath.apply(null, ID, "Alice", true, "2026-08");
        WinStreak second = WinStreakMath.apply(first, ID, "Alice", true, "2026-08");
        assertEquals(2, second.currentStreak());
        assertEquals(2, second.bestStreak());
        assertEquals(2, second.monthBest());
    }

    @Test
    void lossResetsCurrentButKeepsBestAndMonthBest() {
        WinStreak won = WinStreakMath.apply(null, ID, "Alice", true, "2026-08");
        won = WinStreakMath.apply(won, ID, "Alice", true, "2026-08");
        WinStreak lost = WinStreakMath.apply(won, ID, "Alice", false, "2026-08");
        assertEquals(0, lost.currentStreak());
        assertEquals(2, lost.bestStreak());
        assertEquals(2, lost.monthBest());
    }

    @Test
    void newMonthClearsMonthBestThenRecordsFreshWin() {
        WinStreak july = WinStreakMath.apply(null, ID, "Alice", true, "2026-07");
        july = WinStreakMath.apply(july, ID, "Alice", true, "2026-07");
        WinStreak august = WinStreakMath.apply(july, ID, "Alice", true, "2026-08");
        assertEquals(3, august.currentStreak());
        assertEquals(3, august.bestStreak());
        assertEquals(3, august.monthBest());
        assertEquals("2026-08", august.monthKey());
    }

    @Test
    void lossInNewMonthDoesNotKeepOldMonthBest() {
        WinStreak july = WinStreakMath.apply(null, ID, "Alice", true, "2026-07");
        WinStreak augustLoss = WinStreakMath.apply(july, ID, "Alice", false, "2026-08");
        assertEquals(0, augustLoss.currentStreak());
        assertEquals(1, augustLoss.bestStreak());
        assertEquals(0, augustLoss.monthBest());
        assertEquals("2026-08", augustLoss.monthKey());
    }
}
