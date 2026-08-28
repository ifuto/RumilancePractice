package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Daily ranked kill/match counters for {@code /ranking kill|matches}.
 */
public final class DailyRankedStatsRepository {

    public record DailyEntry(UUID playerId, int kills, int matches) {
    }

    private final DatabaseService databaseService;

    public DailyRankedStatsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void increment(UUID playerId, int killDelta, int matchDelta) throws SQLException {
        String date = LocalDate.now().toString();
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("daily_ranked_stats")
                            + " (player_uuid, stat_date, kills, matches) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setString(2, date);
                insert.setInt(3, Math.max(0, killDelta));
                insert.setInt(4, Math.max(0, matchDelta));
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("daily_ranked_stats")
                                + " SET kills = kills + ?, matches = matches + ? "
                                + "WHERE player_uuid = ? AND stat_date = ?")) {
                    update.setInt(1, Math.max(0, killDelta));
                    update.setInt(2, Math.max(0, matchDelta));
                    update.setString(3, playerId.toString());
                    update.setString(4, date);
                    update.executeUpdate();
                }
            }
        }
    }

    public List<DailyEntry> topKillsToday(int limit) throws SQLException {
        return top("kills", limit);
    }

    public List<DailyEntry> topMatchesToday(int limit) throws SQLException {
        return top("matches", limit);
    }

    public int deleteForPlayer(UUID playerId) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("daily_ranked_stats") + " WHERE player_uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            return statement.executeUpdate();
        }
    }

    public int deleteAll() throws SQLException {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + databaseService.table("daily_ranked_stats"))) {
            return statement.executeUpdate();
        }
    }

    private List<DailyEntry> top(String column, int limit) throws SQLException {
        String date = LocalDate.now().toString();
        String sql = "SELECT player_uuid, kills, matches FROM " + databaseService.table("daily_ranked_stats")
                + " WHERE stat_date = ? ORDER BY " + column + " DESC LIMIT ?";
        List<DailyEntry> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new DailyEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("kills"),
                            rs.getInt("matches")
                    ));
                }
            }
        }
        return result;
    }
}
