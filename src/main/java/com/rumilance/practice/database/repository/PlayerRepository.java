package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.PlayerData;

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
 * Persistence for basic player profile information (username, locale, join/seen timestamps).
 */
public final class PlayerRepository {

    private final DatabaseService databaseService;

    public PlayerRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<PlayerData> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, username, first_join, last_seen, locale FROM "
                + databaseService.table("players") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    /** Case-insensitive username lookup (exact match after trim). */
    public Optional<PlayerData> findByUsername(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT uuid, username, first_join, last_seen, locale FROM "
                + databaseService.table("players") + " WHERE LOWER(username) = LOWER(?) LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sanitizeUsername(username.trim()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public void upsert(PlayerData data) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("players")
                + " (uuid, username, first_join, last_seen, locale) VALUES (?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid", "username", "last_seen", "locale");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, data.uuid().toString());
            statement.setString(2, sanitizeUsername(data.username()));
            statement.setTimestamp(3, Timestamp.from(data.firstJoin()));
            statement.setTimestamp(4, Timestamp.from(data.lastSeen()));
            statement.setString(5, data.locale());
            statement.executeUpdate();
        }
    }

    /**
     * Floodgate Bedrock names can exceed 16 characters (leading {@code .} + Xbox gamertag).
     * Cap at 32 to match migration 17 / MariaDB column width.
     */
    public static String sanitizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.length() <= 32 ? username : username.substring(0, 32);
    }

    public void updateLastSeen(UUID uuid, Instant lastSeen) throws SQLException {
        String sql = "UPDATE " + databaseService.table("players") + " SET last_seen = ? WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(lastSeen));
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        }
    }

    public List<PlayerData> findAll(int limit) throws SQLException {
        String sql = "SELECT uuid, username, first_join, last_seen, locale FROM "
                + databaseService.table("players") + " ORDER BY last_seen DESC LIMIT ?";
        List<PlayerData> result = new ArrayList<>();
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

    private PlayerData map(ResultSet resultSet) throws SQLException {
        return new PlayerData(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("username"),
                resultSet.getTimestamp("first_join").toInstant(),
                resultSet.getTimestamp("last_seen").toInstant(),
                resultSet.getString("locale")
        );
    }
}
