package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.RankedKitStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for ranked Glicko-2 statistics, one row per (player, kit) pair. The public
 * display value is {@code pt}; {@code deviation}/{@code volatility} carry the hidden
 * confidence state. Leaderboard queries filter out players whose deviation is still too
 * high to trust their PT.
 */
public final class RankedStatsRepository {

    private static final String COLUMNS = "id, uuid, kit, pt, deviation, volatility, wins, losses, win_streak, best_pt";

    private final DatabaseService databaseService;

    public RankedStatsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<RankedKitStats> find(UUID uuid, String kit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM "
                + databaseService.table("ranked_stats") + " WHERE uuid = ? AND kit = ?";
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

    public void upsert(RankedKitStats stats) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("ranked_stats")
                + " (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid, kit", "pt", "deviation", "volatility",
                        "wins", "losses", "win_streak", "best_pt");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, stats);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, RankedKitStats stats) throws SQLException {
        statement.setString(1, stats.id().toString());
        statement.setString(2, stats.uuid().toString());
        statement.setString(3, stats.kit());
        statement.setInt(4, stats.pt());
        statement.setDouble(5, stats.deviation());
        statement.setDouble(6, stats.volatility());
        statement.setInt(7, stats.wins());
        statement.setInt(8, stats.losses());
        statement.setInt(9, stats.winStreak());
        statement.setInt(10, stats.bestPt());
    }

    /**
     * On turning an existing kit into a folder, the original kit becomes its default child. Copy
     * each player's ranked stats to that child so their existing PT/W-L carry over. Old records
     * stay as historical backups; a rerun never overwrites stats earned under the child id.
     */
    public int copyForKit(String fromKit, String toKit) throws SQLException {
        if (fromKit == null || toKit == null || fromKit.equalsIgnoreCase(toKit)) {
            return 0;
        }
        String table = databaseService.table("ranked_stats");
        String select = "SELECT uuid, pt, deviation, volatility, wins, losses, win_streak, best_pt FROM " + table
                + " WHERE kit = ?";
        String insert = "INSERT INTO " + table
                + " (" + COLUMNS + ") "
                + "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM " + table
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
                        write.setInt(4, rows.getInt("pt"));
                        write.setDouble(5, rows.getDouble("deviation"));
                        write.setDouble(6, rows.getDouble("volatility"));
                        write.setInt(7, rows.getInt("wins"));
                        write.setInt(8, rows.getInt("losses"));
                        write.setInt(9, rows.getInt("win_streak"));
                        write.setInt(10, rows.getInt("best_pt"));
                        write.setString(11, uuid);
                        write.setString(12, toKit);
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

    /** Raw top-N for one kit (admin views); public rankings use {@link #topEligibleByKit}. */
    public List<RankedKitStats> topByKit(String kit, int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM "
                + databaseService.table("ranked_stats") + " WHERE kit = ? ORDER BY pt DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kit);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    /**
     * Public PT ranking for one kit: only players with at least one ranked match whose rating
     * deviation is already trusted enough (below the leaderboard gate). Uncertain players stay
     * off the board until their deviation settles.
     */
    public List<RankedKitStats> topEligibleByKit(String kit, int limit, double maxDeviation) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + databaseService.table("ranked_stats")
                + " WHERE kit = ? AND wins + losses >= 1 AND deviation <= ? ORDER BY pt DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kit);
            statement.setDouble(2, maxDeviation);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    public List<RankedKitStats> findAllForPlayer(UUID uuid) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM "
                + databaseService.table("ranked_stats") + " WHERE uuid = ? ORDER BY pt DESC";
        List<RankedKitStats> result = new ArrayList<>();
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

    public List<RankedKitStats> findAllOrderedByWinStreak(int limit) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM "
                + databaseService.table("ranked_stats")
                + " ORDER BY win_streak DESC, pt DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
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

    /** Cross-kit public PT ranking: one match minimum and a trusted (low) deviation. */
    public List<RankedKitStats> findTopPtOverall(int limit, double maxDeviation) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM "
                + databaseService.table("ranked_stats")
                + " WHERE wins + losses >= 1 AND deviation <= ? ORDER BY pt DESC LIMIT ?";
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, maxDeviation);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(map(resultSet));
                }
            }
        }
        return result;
    }

    /** Every ranked-stats row (all players, all kits) — used by the tier snapshot. */
    public List<RankedKitStats> findAll() throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + databaseService.table("ranked_stats");
        List<RankedKitStats> result = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(map(resultSet));
            }
        }
        return result;
    }

    public int deleteForPlayer(UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + databaseService.table("ranked_stats") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate();
        }
    }

    public int deleteAll() throws SQLException {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM " + databaseService.table("ranked_stats"))) {
            return statement.executeUpdate();
        }
    }

    private RankedKitStats map(ResultSet resultSet) throws SQLException {
        return new RankedKitStats(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("kit"),
                resultSet.getInt("pt"),
                resultSet.getDouble("deviation"),
                resultSet.getDouble("volatility"),
                resultSet.getInt("wins"),
                resultSet.getInt("losses"),
                resultSet.getInt("win_streak"),
                resultSet.getInt("best_pt")
        );
    }
}
