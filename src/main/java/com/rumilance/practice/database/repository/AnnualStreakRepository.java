package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player, per-year win-streak bookkeeping for the annual max-win-streak leaderboard:
 * a win extends the current streak (and maybe the yearly best); a loss resets it to zero.
 */
public final class AnnualStreakRepository {

    public record StreakEntry(UUID playerId, int bestStreak) {
    }

    private final DatabaseService databaseService;

    public AnnualStreakRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /**
     * Extends the player's current streak by one win for the current year. {@code best_reached}
     * stamps the day the record-best streak was (re)achieved; the leaderboard breaks value
     * ties by WHO reached the value first.
     */
    public void recordWin(UUID playerId) throws SQLException {
        String year = String.valueOf(Year.now().getValue());
        String today = java.time.LocalDate.now().toString();
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("annual_streak_stats")
                            + " (player_uuid, stat_year, current_streak, best_streak, best_reached)"
                            + " VALUES (?, ?, 1, 1, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setString(2, year);
                insert.setString(3, today);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("annual_streak_stats")
                                + " SET current_streak = current_streak + 1, "
                                + "best_streak = CASE WHEN current_streak + 1 > best_streak "
                                + "THEN current_streak + 1 ELSE best_streak END, "
                                + "best_reached = CASE WHEN current_streak + 1 > best_streak "
                                + "THEN ? ELSE best_reached END "
                                + "WHERE player_uuid = ? AND stat_year = ?")) {
                    update.setString(1, today);
                    update.setString(2, playerId.toString());
                    update.setString(3, year);
                    update.executeUpdate();
                }
            }
        }
    }

    /** Resets the player's current streak after a loss (best stays untouched). */
    public void recordLoss(UUID playerId) throws SQLException {
        String year = String.valueOf(Year.now().getValue());
        String sql = "UPDATE " + databaseService.table("annual_streak_stats")
                + " SET current_streak = 0 WHERE player_uuid = ? AND stat_year = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, year);
            statement.executeUpdate();
        }
    }

    /** Best streaks of the current year, longest first. */
    public List<StreakEntry> topBestStreaks(int limit) throws SQLException {
        return topBestStreaks(String.valueOf(Year.now().getValue()), limit);
    }

    public List<StreakEntry> topBestStreaks(String year, int limit) throws SQLException {
        // Ties share one displayed rank on the leaderboard; WITHIN a tie the player who
        // reached the streak first wins. Rows predating the best_reached column (NULL) are
        // treated as the oldest: they were certainly achieved before any new record.
        String sql = "SELECT player_uuid, best_streak FROM " + databaseService.table("annual_streak_stats")
                + " WHERE stat_year = ? AND best_streak > 0"
                + " ORDER BY best_streak DESC, COALESCE(best_reached, '0000-00-00') ASC,"
                + " player_uuid ASC LIMIT ?";
        List<StreakEntry> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, year);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(resultSet.getString("player_uuid"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    result.add(new StreakEntry(uuid, resultSet.getInt("best_streak")));
                }
            }
        }
        return result;
    }
}
