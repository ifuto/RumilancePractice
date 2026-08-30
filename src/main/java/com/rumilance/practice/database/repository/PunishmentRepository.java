package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.PunishmentRecord;

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
 * Persistence for punishments (mutes/bans/warns) issued against players.
 */
public final class PunishmentRepository {

    private final DatabaseService databaseService;

    public PunishmentRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(PunishmentRecord record) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("punishments")
                + " (id, target_uuid, staff_uuid, type, reason, issued_at, expires_at, revoked) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.id().toString());
            statement.setString(2, record.targetUuid().toString());
            statement.setString(3, record.staffUuid() != null ? record.staffUuid().toString() : null);
            statement.setString(4, record.type());
            statement.setString(5, record.reason());
            statement.setTimestamp(6, Timestamp.from(record.issuedAt()));
            statement.setTimestamp(7, record.expiresAt() != null ? Timestamp.from(record.expiresAt()) : null);
            statement.setInt(8, record.revoked() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void revoke(UUID id) throws SQLException {
        String sql = "UPDATE " + databaseService.table("punishments") + " SET revoked = 1 WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    public Optional<PunishmentRecord> findById(UUID id) throws SQLException {
        String sql = "SELECT id, target_uuid, staff_uuid, type, reason, issued_at, expires_at, revoked FROM "
                + databaseService.table("punishments") + " WHERE id = ?";
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

    public List<PunishmentRecord> findActiveForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT id, target_uuid, staff_uuid, type, reason, issued_at, expires_at, revoked FROM "
                + databaseService.table("punishments")
                + " WHERE target_uuid = ? AND revoked = 0 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
        return query(sql, uuid);
    }

    public List<PunishmentRecord> findHistoryForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT id, target_uuid, staff_uuid, type, reason, issued_at, expires_at, revoked FROM "
                + databaseService.table("punishments") + " WHERE target_uuid = ? ORDER BY issued_at DESC";
        return query(sql, uuid);
    }

    private List<PunishmentRecord> query(String sql, UUID uuid) throws SQLException {
        List<PunishmentRecord> result = new ArrayList<>();
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

    private PunishmentRecord map(ResultSet resultSet) throws SQLException {
        String staffUuid = resultSet.getString("staff_uuid");
        Timestamp expiresAt = resultSet.getTimestamp("expires_at");
        return new PunishmentRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("target_uuid")),
                staffUuid != null ? UUID.fromString(staffUuid) : null,
                resultSet.getString("type"),
                resultSet.getString("reason"),
                resultSet.getTimestamp("issued_at").toInstant(),
                expiresAt != null ? expiresAt.toInstant() : null,
                resultSet.getInt("revoked") != 0
        );
    }
}
