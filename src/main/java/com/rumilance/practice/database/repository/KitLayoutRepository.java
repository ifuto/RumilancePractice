package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.KitLayoutSnapshot;

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
 * Persistence for a player's personal per-kit inventory layout overrides.
 */
public final class KitLayoutRepository {

    private final DatabaseService databaseService;

    public KitLayoutRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<KitLayoutSnapshot> find(UUID uuid, String kit) throws SQLException {
        String sql = "SELECT id, uuid, kit, item_data, updated_at FROM "
                + databaseService.table("kit_layouts") + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public void upsert(KitLayoutSnapshot snapshot) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("kit_layouts")
                + " (id, uuid, kit, item_data, updated_at) VALUES (?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, kit", "item_data", "updated_at");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.id().toString());
            statement.setString(2, snapshot.uuid().toString());
            statement.setString(3, snapshot.kit());
            statement.setString(4, snapshot.itemDataBase64());
            statement.setTimestamp(5, Timestamp.from(snapshot.updatedAt()));
            statement.executeUpdate();
        }
    }

    public void delete(UUID uuid, String kit) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("kit_layouts") + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            statement.executeUpdate();
        }
    }

    /** Deletes every saved layout for one player. @return rows removed. */
    public int deleteAllForPlayer(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("kit_layouts") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate();
        }
    }

    /** Deletes every saved layout of every player. @return rows removed. */
    public int deleteAll() throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("kit_layouts");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return statement.executeUpdate();
        }
    }

    public List<KitLayoutSnapshot> findAllForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT id, uuid, kit, item_data, updated_at FROM "
                + databaseService.table("kit_layouts") + " WHERE uuid = ?";
        List<KitLayoutSnapshot> result = new ArrayList<>();
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

    private KitLayoutSnapshot map(ResultSet resultSet) throws SQLException {
        return new KitLayoutSnapshot(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("kit"),
                resultSet.getString("item_data"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
