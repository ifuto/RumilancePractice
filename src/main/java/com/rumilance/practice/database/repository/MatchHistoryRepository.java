package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.MatchHistoryEntry;
import com.rumilance.practice.state.MatchMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for completed match records, used for statistics and history views.
 */
public final class MatchHistoryRepository {

    private final DatabaseService databaseService;

    public MatchHistoryRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(MatchHistoryEntry entry) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("match_history")
                + " (id, player_a, player_b, kit, mode, winner, ranked, started_at, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.playerA().toString());
            statement.setString(3, entry.playerB().toString());
            statement.setString(4, entry.kit());
            statement.setString(5, entry.mode().name());
            statement.setString(6, entry.winner() != null ? entry.winner().toString() : null);
            statement.setInt(7, entry.ranked() ? 1 : 0);
            statement.setTimestamp(8, Timestamp.from(entry.startedAt()));
            statement.setTimestamp(9, Timestamp.from(entry.endedAt()));
            statement.executeUpdate();
        }
    }

    public Optional<MatchHistoryEntry> findById(UUID id) throws SQLException {
        String sql = "SELECT id, player_a, player_b, kit, mode, winner, ranked, started_at, ended_at FROM "
                + databaseService.table("match_history") + " WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public List<MatchHistoryEntry> findRecentForPlayer(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT id, player_a, player_b, kit, mode, winner, ranked, started_at, ended_at FROM "
                + databaseService.table("match_history")
                + " WHERE player_a = ? OR player_b = ? ORDER BY ended_at DESC LIMIT ?";
        List<MatchHistoryEntry> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, uuid.toString());
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public int deleteForPlayer(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("match_history")
                + " WHERE player_a = ? OR player_b = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, uuid.toString());
            return statement.executeUpdate();
        }
    }

    public int deleteAll() throws SQLException {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + databaseService.table("match_history"))) {
            return statement.executeUpdate();
        }
    }

    private MatchHistoryEntry map(ResultSet resultSet) throws SQLException {
        String winner = resultSet.getString("winner");
        return new MatchHistoryEntry(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("player_a")),
                UUID.fromString(resultSet.getString("player_b")),
                resultSet.getString("kit"),
                MatchMode.valueOf(resultSet.getString("mode")),
                winner != null ? UUID.fromString(winner) : null,
                resultSet.getInt("ranked") != 0,
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("ended_at").toInstant()
        );
    }
}
