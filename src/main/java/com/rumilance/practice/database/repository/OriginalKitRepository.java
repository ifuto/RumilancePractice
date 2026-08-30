package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.OriginalKitSnapshot;

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
 * Persistence for per-slot original kits ({@code rp_original_kit_slots}).
 */
public final class OriginalKitRepository {

    private final DatabaseService databaseService;

    public OriginalKitRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<OriginalKitSnapshot> find(UUID uuid, int slot) throws SQLException {
        String sql = "SELECT uuid, slot, item_data, armor_data, saved_at FROM "
                + databaseService.table("original_kit_slots") + " WHERE uuid = ? AND slot = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, slot);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public List<OriginalKitSnapshot> findAllForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, slot, item_data, armor_data, saved_at FROM "
                + databaseService.table("original_kit_slots") + " WHERE uuid = ? ORDER BY slot";
        List<OriginalKitSnapshot> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        }
        return result;
    }

    public void upsert(OriginalKitSnapshot snapshot) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("original_kit_slots")
                + " (uuid, slot, item_data, armor_data, saved_at) VALUES (?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, slot", "item_data", "armor_data", "saved_at");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.uuid().toString());
            statement.setInt(2, snapshot.slot());
            statement.setString(3, snapshot.itemDataBase64());
            statement.setString(4, snapshot.armorDataBase64());
            statement.setTimestamp(5, Timestamp.from(snapshot.savedAt()));
            statement.executeUpdate();
        }
    }

    public void delete(UUID uuid, int slot) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("original_kit_slots") + " WHERE uuid = ? AND slot = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, slot);
            statement.executeUpdate();
        }
    }

    private static OriginalKitSnapshot map(ResultSet rs) throws SQLException {
        return new OriginalKitSnapshot(
                UUID.fromString(rs.getString("uuid")),
                rs.getInt("slot"),
                rs.getString("item_data"),
                rs.getString("armor_data"),
                rs.getTimestamp("saved_at").toInstant()
        );
    }
}
