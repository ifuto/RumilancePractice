package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for practice-room ANKER layout overrides ({@code practice_layouts}).
 */
public final class PracticeLayoutRepository {

    private final DatabaseService databaseService;

    public PracticeLayoutRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<LayoutRow> find(UUID uuid, String practiceId, String layoutKey) throws SQLException {
        String sql = "SELECT uuid, practice_id, layout_key, contents, last_used FROM "
                + databaseService.table("practice_layouts")
                + " WHERE uuid = ? AND practice_id = ? AND layout_key = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, practiceId);
            statement.setString(3, layoutKey);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public Optional<LayoutRow> findLastUsed(UUID uuid, String practiceId) throws SQLException {
        String sql = "SELECT uuid, practice_id, layout_key, contents, last_used FROM "
                + databaseService.table("practice_layouts")
                + " WHERE uuid = ? AND practice_id = ? ORDER BY last_used DESC LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, practiceId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void upsert(UUID uuid, String practiceId, String layoutKey, String contentsBase64) throws SQLException {
        Instant now = Instant.now();
        String sql = "INSERT INTO " + databaseService.table("practice_layouts")
                + " (uuid, practice_id, layout_key, contents, last_used) VALUES (?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, practice_id, layout_key", "contents", "last_used");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, practiceId);
            statement.setString(3, layoutKey);
            statement.setString(4, contentsBase64);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    public void touch(UUID uuid, String practiceId, String layoutKey) throws SQLException {
        String sql = "UPDATE " + databaseService.table("practice_layouts")
                + " SET last_used = ? WHERE uuid = ? AND practice_id = ? AND layout_key = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, uuid.toString());
            statement.setString(3, practiceId);
            statement.setString(4, layoutKey);
            statement.executeUpdate();
        }
    }

    /** Deletes rows whose {@code last_used} is older than 7 days. */
    public int purgeOlderThanSevenDays() throws SQLException {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        String sql = "DELETE FROM " + databaseService.table("practice_layouts") + " WHERE last_used < ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(cutoff));
            return statement.executeUpdate();
        }
    }

    private static LayoutRow map(ResultSet rs) throws SQLException {
        return new LayoutRow(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("practice_id"),
                rs.getString("layout_key"),
                rs.getString("contents"),
                rs.getTimestamp("last_used").toInstant()
        );
    }

    public record LayoutRow(UUID uuid, String practiceId, String layoutKey, String contentsBase64, Instant lastUsed) {
    }
}
