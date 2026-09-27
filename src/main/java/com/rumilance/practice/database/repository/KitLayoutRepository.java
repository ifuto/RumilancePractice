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

    /**
     * Non-destructively carries every player's layout from an existing kit key to its new child
     * kit key. A row already saved under {@code toKit} always wins. Copying the encoded data is
     * safe when the child is an exact copy of the parent (the delta baseline is identical), and
     * old inner-kit personal layouts were stored as full arrays. The old rows remain as a backup;
     * rerunning after a restart cannot overwrite edits made under the new key.
     *
     * @return the number of rows copied
     */
    public int copyForKit(String fromKit, String toKit) throws SQLException {
        if (fromKit == null || toKit == null || fromKit.equalsIgnoreCase(toKit)) {
            return 0;
        }
        String table = databaseService.table("kit_layouts");
        String select = "SELECT uuid, item_data, updated_at FROM " + table + " WHERE kit = ?";
        String insert = "INSERT INTO " + table + " (id, uuid, kit, item_data, updated_at) "
                + "SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM " + table
                + " WHERE uuid = ? AND kit = ?)";
        try (Connection connection = databaseService.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement read = connection.prepareStatement(select);
                 PreparedStatement write = connection.prepareStatement(insert)) {
                read.setString(1, fromKit);
                int copied = 0;
                try (ResultSet rows = read.executeQuery()) {
                    while (rows.next()) {
                        String uuid = rows.getString("uuid");
                        write.setString(1, UUID.randomUUID().toString());
                        write.setString(2, uuid);
                        write.setString(3, toKit);
                        write.setString(4, rows.getString("item_data"));
                        write.setTimestamp(5, rows.getTimestamp("updated_at"));
                        write.setString(6, uuid);
                        write.setString(7, toKit);
                        copied += write.executeUpdate();
                    }
                }
                connection.commit();
                return copied;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
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
