package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.RankedKitStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for ranked ELO statistics, one row per (player, kit) pair.
 */
public final class RankedStatsRepository {

    private final DatabaseService databaseService;

    public RankedStatsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<RankedKitStats> find(UUID uuid, String kit) throws SQLException {
        String sql = "SELECT id, uuid, kit, elo, wins, losses, win_streak, best_elo FROM "
                + databaseService.table("ranked_stats") + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public void upsert(RankedKitStats stats) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("ranked_stats")
                + " (id, uuid, kit, elo, wins, losses, win_streak, best_elo) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, kit", "elo", "wins", "losses", "win_streak", "best_elo");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stats.id().toString());
            statement.setString(2, stats.uuid().toString());
            statement.setString(3, stats.kit());
            statement.setInt(4, stats.elo());
            statement.setInt(5, stats.wins());
            statement.setInt(6, stats.losses());
            statement.setInt(7, stats.winStreak());
            statement.setInt(8, stats.bestElo());
            statement.executeUpdate();
        }
    }

    public List<RankedKitStats> topByKit(String kit, int limit) throws SQLException {
        String sql = "SELECT id, uuid, kit, elo, wins, losses, win_streak, best_elo FROM "
                + databaseService.table("ranked_stats") + " WHERE kit = ? ORDER BY elo DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kit);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<RankedKitStats> findAllForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT id, uuid, kit, elo, wins, losses, win_streak, best_elo FROM "
                + databaseService.table("ranked_stats") + " WHERE uuid = ? ORDER BY elo DESC";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<RankedKitStats> findAllOrderedByWinStreak(int limit) throws SQLException {
        String sql = "SELECT id, uuid, kit, elo, wins, losses, win_streak, best_elo FROM "
                + databaseService.table("ranked_stats")
                + " ORDER BY win_streak DESC, elo DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<RankedKitStats> findTopEloOverall(int limit) throws SQLException {
        String sql = "SELECT id, uuid, kit, elo, wins, losses, win_streak, best_elo FROM "
                + databaseService.table("ranked_stats")
                + " WHERE wins + losses >= 1 ORDER BY elo DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    private RankedKitStats map(ResultSet resultSet) throws SQLException {
        return new RankedKitStats(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("kit"),
                resultSet.getInt("elo"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                resultSet.getInt("win_streak"),
                resultSet.getInt("best_elo")
        );
    }
}
