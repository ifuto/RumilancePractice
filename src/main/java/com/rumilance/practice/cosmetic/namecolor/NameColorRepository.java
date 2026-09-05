package com.rumilance.practice.cosmetic.namecolor;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Persistence for {@link NameColorSelection} (VIP+ name colors). */
public final class NameColorRepository {

    private final DatabaseService databaseService;

    public NameColorRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<NameColorSelection> find(UUID uuid) throws SQLException {
        String sql = "SELECT mode, primary_color, secondary_color, changed_at FROM "
                + databaseService.table("player_name_colors") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new NameColorSelection(
                        NameColorSelection.Mode.parse(rs.getString("mode")),
                        rs.getString("primary_color"),
                        rs.getString("secondary_color"),
                        rs.getLong("changed_at")));
            }
        }
    }

    public void upsert(UUID uuid, NameColorSelection selection) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_name_colors")
                + " (uuid, mode, primary_color, secondary_color, changed_at) VALUES (?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid", "mode", "primary_color", "secondary_color", "changed_at");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, selection.mode().name().toLowerCase(java.util.Locale.ROOT));
            statement.setString(3, selection.primaryHex());
            statement.setString(4, selection.secondaryHex());
            statement.setLong(5, selection.changedAtMillis());
            statement.executeUpdate();
        }
    }
}
