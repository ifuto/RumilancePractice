package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chat reports filed by players from the chat hover/click ({@code chat_reports} table).
 *
 * <p>Only the <em>reference</em> to the chat line is stored — the reported text and the lines
 * around it are read back from {@code ChatLogService} when a reviewer opens the report, so the
 * evidence is whatever the chat buffer still holds rather than a copy frozen at report time.</p>
 */
public final class ChatReportRepository {

    private final DatabaseService databaseService;

    public ChatReportRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void insert(long chatLineId, UUID reporterId, UUID reportedId, String reportedName,
                       long reportedAt) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("chat_reports")
                + " (chat_line_id, reporter_uuid, reported_uuid, reported_name, reported_ts, status) "
                + "VALUES (?, ?, ?, ?, ?, 'OPEN') "
                + databaseService.upsertClause("chat_line_id, reporter_uuid", "status");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatLineId);
            statement.setString(2, reporterId.toString());
            statement.setString(3, reportedId.toString());
            statement.setString(4, reportedName);
            statement.setLong(5, reportedAt);
            statement.executeUpdate();
        }
    }

    /** Open reports, oldest first, capped at {@code limit}. */
    public List<Row> findOpen(int limit) throws SQLException {
        return query("WHERE status = 'OPEN' ORDER BY reported_ts ASC LIMIT " + Math.max(1, limit));
    }

    /** The most recent reports regardless of status, newest first. */
    public List<Row> findRecent(int limit) throws SQLException {
        return query("ORDER BY reported_ts DESC LIMIT " + Math.max(1, limit));
    }

    public void setStatus(long chatLineId, UUID reporterId, String status) throws SQLException {
        String sql = "UPDATE " + databaseService.table("chat_reports")
                + " SET status = ? WHERE chat_line_id = ? AND reporter_uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, chatLineId);
            statement.setString(3, reporterId.toString());
            statement.executeUpdate();
        }
    }

    public void delete(long chatLineId, UUID reporterId) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("chat_reports")
                + " WHERE chat_line_id = ? AND reporter_uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatLineId);
            statement.setString(2, reporterId.toString());
            statement.executeUpdate();
        }
    }

    private List<Row> query(String whereAndOrder) throws SQLException {
        String sql = "SELECT chat_line_id, reporter_uuid, reported_uuid, reported_name, "
                + "reported_ts, status FROM " + databaseService.table("chat_reports") + " "
                + whereAndOrder;
        List<Row> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new Row(
                        rs.getLong("chat_line_id"),
                        UUID.fromString(rs.getString("reporter_uuid")),
                        UUID.fromString(rs.getString("reported_uuid")),
                        rs.getString("reported_name"),
                        rs.getLong("reported_ts"),
                        rs.getString("status")));
            }
        }
        return rows;
    }

    /** One stored report. Text and context come from {@code ChatLogService}, not from here. */
    public record Row(long chatLineId, UUID reporterId, UUID reportedId, String reportedName,
                      long reportedTs, String status) {
    }
}
