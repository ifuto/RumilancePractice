package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.PlayerReport;

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
 * Persistence for player-submitted reports. Pending reports occupy a reporter's "slots"
 * (max 2 distinct targets); resolving/dismissing frees a slot.
 */
public final class PlayerReportRepository {

    private final DatabaseService databaseService;

    public PlayerReportRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(PlayerReport report) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_reports")
                + " (id, reporter_uuid, reporter_name, target_uuid, target_name, match_id, reason, kit, mode, status, evidence_path, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, report.id().toString());
            statement.setString(2, report.reporterUuid().toString());
            statement.setString(3, report.reporterName());
            statement.setString(4, report.targetUuid().toString());
            statement.setString(5, report.targetName());
            statement.setString(6, report.matchId().toString());
            statement.setString(7, report.reason());
            statement.setString(8, report.kit());
            statement.setString(9, report.mode());
            statement.setString(10, report.status());
            statement.setString(11, report.evidencePath());
            statement.setTimestamp(12, Timestamp.from(report.createdAt()));
            statement.executeUpdate();
        }
    }

    public void updateStatus(UUID id, String status) throws SQLException {
        String sql = "UPDATE " + databaseService.table("player_reports") + " SET status = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, id.toString());
            statement.executeUpdate();
        }
    }

    public void delete(UUID id) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("player_reports") + " WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    public Optional<PlayerReport> findById(UUID id) throws SQLException {
        String sql = baseSelect() + " WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public List<PlayerReport> findPendingByReporter(UUID reporterUuid) throws SQLException {
        String sql = baseSelect() + " WHERE reporter_uuid = ? AND status = 'PENDING'";
        List<PlayerReport> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reporterUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<PlayerReport> findAllPending() throws SQLException {
        String sql = baseSelect() + " WHERE status = 'PENDING' ORDER BY created_at ASC";
        List<PlayerReport> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(map(resultSet));
            }
        }
        return result;
    }

    private String baseSelect() {
        return "SELECT id, reporter_uuid, reporter_name, target_uuid, target_name, match_id, reason, "
                + "kit, mode, status, evidence_path, created_at FROM " + databaseService.table("player_reports");
    }

    private PlayerReport map(ResultSet resultSet) throws SQLException {
        return new PlayerReport(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("reporter_uuid")),
                resultSet.getString("reporter_name"),
                UUID.fromString(resultSet.getString("target_uuid")),
                resultSet.getString("target_name"),
                UUID.fromString(resultSet.getString("match_id")),
                resultSet.getString("reason"),
                resultSet.getString("kit"),
                resultSet.getString("mode"),
                resultSet.getString("status"),
                resultSet.getString("evidence_path"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
