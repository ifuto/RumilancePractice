package com.rumilance.practice.rank;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Persistence for {@link PlayerRank}. */
public final class RankRepository {

    private final DatabaseService databaseService;

    public RankRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<PlayerRank> find(UUID uuid) throws SQLException {
        String sql = "SELECT rank_id FROM " + databaseService.table("player_ranks") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerRank rank = PlayerRank.parse(rs.getString("rank_id"));
                return Optional.of(rank == null ? PlayerRank.NORM : rank);
            }
        }
    }

    public void upsert(UUID uuid, PlayerRank rank) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_ranks")
                + " (uuid, rank_id) VALUES (?, ?) "
                + databaseService.upsertClause("uuid", "rank_id");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, rank.storageKey());
            statement.executeUpdate();
        }
    }
}
