package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent FFA K/D, isolated from ranked Elo/stats.
 */
public final class FfaStatsRepository {

    public record FfaRow(UUID playerId, String arenaId, int kills, int deaths) {
    }

    private final DatabaseService databaseService;

    public FfaStatsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<FfaRow> find(UUID playerId, String arenaId) throws SQLException {
        String sql = "SELECT player_uuid, arena_id, kills, deaths FROM "
                + databaseService.table("ffa_stats") + " WHERE player_uuid = ? AND arena_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, arenaId.toLowerCase());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FfaRow(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("arena_id"),
                        rs.getInt("kills"),
                        rs.getInt("deaths")
                ));
            }
        }
    }

    public void addKill(UUID playerId, String arenaId) throws SQLException {
        bump(playerId, arenaId, 1, 0);
    }

    public void addDeath(UUID playerId, String arenaId) throws SQLException {
        bump(playerId, arenaId, 0, 1);
    }

    private void bump(UUID playerId, String arenaId, int kills, int deaths) throws SQLException {
        String id = arenaId.toLowerCase();
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("ffa_stats")
                            + " (player_uuid, arena_id, kills, deaths) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setString(2, id);
                insert.setInt(3, kills);
                insert.setInt(4, deaths);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("ffa_stats")
                                + " SET kills = kills + ?, deaths = deaths + ? "
                                + "WHERE player_uuid = ? AND arena_id = ?")) {
                    update.setInt(1, kills);
                    update.setInt(2, deaths);
                    update.setString(3, playerId.toString());
                    update.setString(4, id);
                    update.executeUpdate();
                }
            }
        }
    }
}
