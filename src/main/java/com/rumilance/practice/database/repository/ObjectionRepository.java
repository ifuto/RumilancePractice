package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ChatBan objection persistence ({@code objections} table).
 */
public final class ObjectionRepository {

    public record Objection(
            UUID id,
            UUID chatbanId,
            UUID playerUuid,
            String reason,
            String status,
            Instant createdAt,
            UUID staffUuid,
            String staffNote
    ) {
    }

    private final DatabaseService databaseService;

    public ObjectionRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(Objection objection) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("objections")
                + " (id, chatban_id, player_uuid, reason, status, created_at, staff_uuid, staff_note) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objection.id().toString());
            statement.setString(2, objection.chatbanId().toString());
            statement.setString(3, objection.playerUuid().toString());
            statement.setString(4, objection.reason());
            statement.setString(5, objection.status());
            statement.setTimestamp(6, Timestamp.from(objection.createdAt()));
            statement.setString(7, objection.staffUuid() == null ? null : objection.staffUuid().toString());
            statement.setString(8, objection.staffNote());
            statement.executeUpdate();
        }
    }

    public Optional<Objection> findById(UUID id) throws SQLException {
        String sql = "SELECT id, chatban_id, player_uuid, reason, status, created_at, staff_uuid, staff_note FROM "
                + databaseService.table("objections") + " WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public List<Objection> findPending(int limit) throws SQLException {
        String sql = "SELECT id, chatban_id, player_uuid, reason, status, created_at, staff_uuid, staff_note FROM "
                + databaseService.table("objections")
                + " WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT ?";
        List<Objection> result = new ArrayList<>();
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

    public void updateStatus(UUID id, String status, UUID staffUuid, String staffNote) throws SQLException {
        String sql = "UPDATE " + databaseService.table("objections")
                + " SET status = ?, staff_uuid = ?, staff_note = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, staffUuid == null ? null : staffUuid.toString());
            statement.setString(3, staffNote);
            statement.setString(4, id.toString());
            statement.executeUpdate();
        }
    }

    private static Objection map(ResultSet rs) throws SQLException {
        String staff = rs.getString("staff_uuid");
        return new Objection(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("chatban_id")),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                staff == null ? null : UUID.fromString(staff),
                rs.getString("staff_note")
        );
    }
}
