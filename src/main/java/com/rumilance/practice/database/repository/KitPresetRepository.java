package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Stores which named preset variant a player last used per official kit. */
public final class KitPresetRepository {

    private final DatabaseService databaseService;

    public KitPresetRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<String> find(UUID uuid, String kit) throws SQLException {
        String sql = "SELECT preset FROM " + databaseService.table("player_kit_presets")
                + " WHERE uuid = ? AND kit = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("preset"));
            }
        }
    }

    public void upsert(UUID uuid, String kit, String preset) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_kit_presets")
                + " (uuid, kit, preset) VALUES (?, ?, ?) "
                + databaseService.upsertClause("uuid, kit", "preset");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, kit);
            statement.setString(3, preset == null ? "" : preset);
            statement.executeUpdate();
        }
    }
}
