package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.WinStreak;
import com.rumilance.practice.stats.WinStreakMath;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One shared win-streak row per player (ranked and unranked combined).
 */
public final class WinStreakRepository {

    private final DatabaseService databaseService;

    public WinStreakRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<WinStreak> find(UUID playerId) throws SQLException {
        String sql = "SELECT uuid, username, current_streak, best_streak, month_key, month_best FROM "
                + databaseService.table("win_streaks") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public WinStreak record(UUID playerId, String username, boolean win) throws SQLException {
        String month = WinStreakMath.currentMonthKey();
        WinStreak previous = find(playerId).orElse(null);
        WinStreak next = WinStreakMath.apply(previous, playerId, username, win, month);
        upsert(next);
        return next;
    }

    /** Kit-scoped streak: KitA win then KitB win then KitA win ↁEKitA current = 2. */
    public WinStreak recordKit(UUID playerId, String username, String kit, boolean win) throws SQLException {
        if (kit == null || kit.isBlank()) {
            return record(playerId, username, win);
        }
        String kitKey = kit.toLowerCase(java.util.Locale.ROOT);
        String month = WinStreakMath.currentMonthKey();
        WinStreak previous = findKit(playerId, kitKey).orElse(null);
        WinStreak next = WinStreakMath.apply(previous, playerId, username, win, month);
        upsertKit(kitKey, next);
        return next;
    }

    public Optional<WinStreak> findKit(UUID playerId, String kit) throws SQLException {
        String sql = "SELECT uuid, username, current_streak, best_streak, month_key, month_best FROM "
                + databaseService.table("kit_win_streaks") + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, kit.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void upsertKit(String kit, WinStreak streak) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("kit_win_streaks")
                + " (uuid, kit, username, current_streak, best_streak, month_key, month_best) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, kit",
                "username", "current_streak", "best_streak", "month_key", "month_best");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, streak.playerId().toString());
            statement.setString(2, kit.toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, streak.username());
            statement.setInt(4, streak.currentStreak());
            statement.setInt(5, streak.bestStreak());
            statement.setString(6, streak.monthKey());
            statement.setInt(7, streak.monthBest());
            statement.executeUpdate();
        }
    }

    /** Top current kit streaks for queue hover (active streak first). */
    public List<WinStreak> topCurrentForKit(String kit, int limit) throws SQLException {
        String sql = "SELECT uuid, username, current_streak, best_streak, month_key, month_best FROM "
                + databaseService.table("kit_win_streaks")
                + " WHERE kit = ? AND current_streak > 0 "
                + "ORDER BY current_streak DESC, best_streak DESC LIMIT ?";
        List<WinStreak> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kit.toLowerCase(java.util.Locale.ROOT));
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void upsert(WinStreak streak) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("win_streaks")
                + " (uuid, username, current_streak, best_streak, month_key, month_best) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid",
                "username", "current_streak", "best_streak", "month_key", "month_best");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, streak.playerId().toString());
            statement.setString(2, streak.username());
            statement.setInt(3, streak.currentStreak());
            statement.setInt(4, streak.bestStreak());
            statement.setString(5, streak.monthKey());
            statement.setInt(6, streak.monthBest());
            statement.executeUpdate();
        }
    }

    public List<WinStreak> topBest(int limit) throws SQLException {
        String sql = "SELECT uuid, username, current_streak, best_streak, month_key, month_best FROM "
                + databaseService.table("win_streaks")
                + " WHERE best_streak > 0 ORDER BY best_streak DESC, current_streak DESC LIMIT ?";
        return query(sql, limit);
    }

    public List<WinStreak> topMonth(int limit) throws SQLException {
        String month = WinStreakMath.currentMonthKey();
        String sql = "SELECT uuid, username, current_streak, best_streak, month_key, month_best FROM "
                + databaseService.table("win_streaks")
                + " WHERE month_key = ? AND month_best > 0 ORDER BY month_best DESC, current_streak DESC LIMIT ?";
        List<WinStreak> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, month);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public int deleteForPlayer(UUID playerId) throws SQLException {
        int n = 0;
        String sql = "DELETE FROM " + databaseService.table("win_streaks") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            n += statement.executeUpdate();
        }
        String kitSql = "DELETE FROM " + databaseService.table("kit_win_streaks") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(kitSql)) {
            statement.setString(1, playerId.toString());
            n += statement.executeUpdate();
        }
        return n;
    }

    public int deleteAll() throws SQLException {
        int n = 0;
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + databaseService.table("win_streaks"))) {
            n += statement.executeUpdate();
        }
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + databaseService.table("kit_win_streaks"))) {
            n += statement.executeUpdate();
        }
        return n;
    }

    private List<WinStreak> query(String sql, int limit) throws SQLException {
        List<WinStreak> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    private static WinStreak map(ResultSet rs) throws SQLException {
        return new WinStreak(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getInt("current_streak"),
                rs.getInt("best_streak"),
                rs.getString("month_key"),
                rs.getInt("month_best")
        );
    }
}
