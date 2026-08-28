package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Lifetime spam-filter detection counter per player. Detection count drives escalating ChatBans;
 * auto_ban_count tracks how many spam ChatBans were issued so durations can grow.
 */
public final class SpamDetectionRepository {

    private final DatabaseService databaseService;

    public SpamDetectionRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public static final class Counts {
        public final int detections;
        public final int autoBans;

        public Counts(int detections, int autoBans) {
            this.detections = detections;
            this.autoBans = autoBans;
        }
    }

    /** Increments the detection counter (creating the row) and returns the new totals. */
    public Counts incrementDetection(UUID uuid) throws SQLException {
        // A read-modify-write within one connection keeps this backend-agnostic (the generic
        // upsert clause cannot express detection_count = existing + 1).
        try (Connection connection = databaseService.getConnection()) {
            Counts existing = readWithin(connection, uuid);
            if (existing == null) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO "
                        + databaseService.table("spam_detections")
                        + " (player_uuid, detection_count, auto_ban_count, updated_at) VALUES (?, 1, 0, CURRENT_TIMESTAMP)")) {
                    insert.setString(1, uuid.toString());
                    insert.executeUpdate();
                }
                return new Counts(1, 0);
            }
            int next = existing.detections + 1;
            try (PreparedStatement update = connection.prepareStatement("UPDATE "
                    + databaseService.table("spam_detections")
                    + " SET detection_count = ?, updated_at = CURRENT_TIMESTAMP WHERE player_uuid = ?")) {
                update.setInt(1, next);
                update.setString(2, uuid.toString());
                update.executeUpdate();
            }
            return new Counts(next, existing.autoBans);
        }
    }

    public void incrementAutoBan(UUID uuid) throws SQLException {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE "
                     + databaseService.table("spam_detections")
                     + " SET auto_ban_count = auto_ban_count + 1, updated_at = CURRENT_TIMESTAMP WHERE player_uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    public Counts get(UUID uuid) throws SQLException {
        try (Connection connection = databaseService.getConnection()) {
            Counts counts = readWithin(connection, uuid);
            return counts == null ? new Counts(0, 0) : counts;
        }
    }

    private Counts readWithin(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT detection_count, auto_ban_count FROM "
                + databaseService.table("spam_detections") + " WHERE player_uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new Counts(resultSet.getInt("detection_count"), resultSet.getInt("auto_ban_count"));
            }
        }
    }
}
