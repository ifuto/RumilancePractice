package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.AuditLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the plugin's administrative audit trail.
 */
public final class AuditLogRepository {

    private final DatabaseService databaseService;

    public AuditLogRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(AuditLogEntry entry) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("audit_log")
                + " (id, actor_uuid, action, details, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.id().toString());
            statement.setString(2, entry.actorUuid() != null ? entry.actorUuid().toString() : null);
            statement.setString(3, entry.action());
            statement.setString(4, entry.details());
            statement.setTimestamp(5, Timestamp.from(entry.createdAt()));
            statement.executeUpdate();
        }
    }

    public List<AuditLogEntry> findRecent(int limit) throws SQLException {
        String sql = "SELECT id, actor_uuid, action, details, created_at FROM "
                + databaseService.table("audit_log") + " ORDER BY created_at DESC LIMIT ?";
        List<AuditLogEntry> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String actorUuid = resultSet.getString("actor_uuid");
                    result.add(new AuditLogEntry(
                            UUID.fromString(resultSet.getString("id")),
                            actorUuid != null ? UUID.fromString(actorUuid) : null,
                            resultSet.getString("action"),
                            resultSet.getString("details"),
                            resultSet.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        }
        return result;
    }
}
