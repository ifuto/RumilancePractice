package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence for the per-player block list behind {@code /block} (alias {@code /ignore}).
 *
 * <p>A row only records one direction ({@code blocker -> blocked}); the queue treats the pair
 * as blocked when either direction exists, so both players staying apart needs just one row.</p>
 */
public final class BlockRepository {

    private final DatabaseService databaseService;

    public BlockRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    /** Everyone {@code blocker} has blocked. Empty when the table is unreachable. */
    public Set<UUID> findBlocked(UUID blocker) throws SQLException {
        String sql = "SELECT blocked_uuid FROM " + databaseService.table("player_blocks")
                + " WHERE blocker_uuid = ?";
        Set<UUID> blocked = new HashSet<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blocker.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    blocked.add(UUID.fromString(resultSet.getString("blocked_uuid")));
                }
            }
        }
        return blocked;
    }

    public void insert(UUID blocker, UUID blocked, Instant now) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_blocks")
                + " (blocker_uuid, blocked_uuid, created_ts) VALUES (?, ?, ?) "
                + databaseService.upsertClause("blocker_uuid, blocked_uuid", "created_ts");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blocker.toString());
            statement.setString(2, blocked.toString());
            statement.setLong(3, now.toEpochMilli());
            statement.executeUpdate();
        }
    }

    public void delete(UUID blocker, UUID blocked) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("player_blocks")
                + " WHERE blocker_uuid = ? AND blocked_uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, blocker.toString());
            statement.setString(2, blocked.toString());
            statement.executeUpdate();
        }
    }
}
