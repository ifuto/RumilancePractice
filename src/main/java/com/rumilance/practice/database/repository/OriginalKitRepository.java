package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.OriginalKitSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for a player's original (pre-practice) inventory/armor snapshot.
 */
public final class OriginalKitRepository {

    private final DatabaseService databaseService;

    public OriginalKitRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<OriginalKitSnapshot> find(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, item_data, armor_data, saved_at FROM "
                + databaseService.table("original_kits") + " WHERE uuid = ?";
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

    public void upsert(OriginalKitSnapshot snapshot) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("original_kits")
                + " (uuid, item_data, armor_data, saved_at) VALUES (?, ?, ?, ?) "
                + databaseService.upsertClause("uuid", "item_data", "armor_data", "saved_at");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.uuid().toString());
            statement.setString(2, snapshot.itemDataBase64());
            statement.setString(3, snapshot.armorDataBase64());
            statement.setTimestamp(4, Timestamp.from(snapshot.savedAt()));
            statement.executeUpdate();
        }
    }

    public void delete(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("original_kits") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        }
    }

    private OriginalKitSnapshot map(ResultSet resultSet) throws SQLException {
        return new OriginalKitSnapshot(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("item_data"),
                resultSet.getString("armor_data"),
                resultSet.getTimestamp("saved_at").toInstant()
        );
    }
}
