package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the alt-account detection pipeline. All writes are upserts using the
 * duplicate-catch pattern shared with the other repositories so the same statements work on
 * both SQLite and MariaDB.
 */
public final class AltRepository {

    public record IpOverlap(UUID a, UUID b, String ip, long lastTs) {
    }

    public record SwitchPair(UUID a, UUID b, int switches, long lastTs) {
    }

    public record BehaviorRow(UUID playerId, long swings, String intervalBins, String hourBins, long updatedTs) {
    }

    public record FlagRow(UUID a, UUID b, double score, String level, String evidence,
                          String status, long createdTs, long updatedTs) {
    }

    private final DatabaseService databaseService;

    public AltRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public void recordLogin(UUID playerId, String ip, long ts) throws SQLException {
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("alt_ip_history")
                            + " (player_uuid, ip, first_ts, last_ts) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setString(2, ip);
                insert.setLong(3, ts);
                insert.setLong(4, ts);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("alt_ip_history")
                                + " SET last_ts = ? WHERE player_uuid = ? AND ip = ?")) {
                    update.setLong(1, ts);
                    update.setString(2, playerId.toString());
                    update.setString(3, ip);
                    update.executeUpdate();
                }
            }
        }
    }

    /** Other accounts seen from the same IP whose activity falls within {@code sinceTs}. */
    public List<UUID> othersSharingIp(String ip, UUID self, long sinceTs) throws SQLException {
        List<UUID> others = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_uuid FROM " + databaseService.table("alt_ip_history")
                             + " WHERE ip = ? AND player_uuid <> ? AND last_ts >= ?")) {
            ps.setString(1, ip);
            ps.setString(2, self.toString());
            ps.setLong(3, sinceTs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    others.add(UUID.fromString(rs.getString(1)));
                }
            }
        }
        return others;
    }

    public void bumpSwitch(String ip, UUID a, UUID b, long ts) throws SQLException {
        UUID first = a.toString().compareTo(b.toString()) <= 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("alt_ipswitch")
                            + " (ip, uuid_a, uuid_b, switches, last_ts) VALUES (?, ?, ?, 1, ?)")) {
                insert.setString(1, ip);
                insert.setString(2, first.toString());
                insert.setString(3, second.toString());
                insert.setLong(4, ts);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("alt_ipswitch")
                                + " SET switches = switches + 1, last_ts = ?"
                                + " WHERE ip = ? AND uuid_a = ? AND uuid_b = ?")) {
                    update.setLong(1, ts);
                    update.setString(2, ip);
                    update.setString(3, first.toString());
                    update.setString(4, second.toString());
                    update.executeUpdate();
                }
            }
        }
    }

    /** Accounts that share at least one IP, returned as canonical (a &lt; b) pairs. */
    public List<IpOverlap> sharedIpPairs() throws SQLException {
        List<IpOverlap> pairs = new ArrayList<>();
        String t = databaseService.table("alt_ip_history");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT a.player_uuid, b.player_uuid, a.ip,"
                             + " MAX(a.last_ts, b.last_ts) FROM " + t + " a"
                             + " JOIN " + t + " b ON a.ip = b.ip AND a.player_uuid < b.player_uuid"
                             + " GROUP BY a.ip, a.player_uuid, b.player_uuid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                pairs.add(new IpOverlap(UUID.fromString(rs.getString(1)),
                        UUID.fromString(rs.getString(2)), rs.getString(3), rs.getLong(4)));
            }
        }
        return pairs;
    }

    public List<SwitchPair> switchPairs(int minSwitches) throws SQLException {
        List<SwitchPair> pairs = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid_a, uuid_b, switches, MAX(last_ts) FROM "
                             + databaseService.table("alt_ipswitch")
                             + " GROUP BY uuid_a, uuid_b HAVING SUM(switches) >= ?")) {
            ps.setInt(1, minSwitches);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pairs.add(new SwitchPair(UUID.fromString(rs.getString(1)),
                            UUID.fromString(rs.getString(2)), rs.getInt(3), rs.getLong(4)));
                }
            }
        }
        return pairs;
    }

    public List<BehaviorRow> loadBehaviors(long activeSinceTs) throws SQLException {
        List<BehaviorRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT player_uuid, swings, interval_bins, hour_bins, updated_ts FROM "
                             + databaseService.table("alt_behavior") + " WHERE updated_ts >= ?")) {
            ps.setLong(1, activeSinceTs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BehaviorRow(UUID.fromString(rs.getString(1)), rs.getLong(2),
                            rs.getString(3), rs.getString(4), rs.getLong(5)));
                }
            }
        }
        return rows;
    }

    public void saveBehavior(UUID playerId, long swings, String intervalBins, String hourBins,
                             long ts) throws SQLException {
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("alt_behavior")
                            + " (player_uuid, swings, interval_bins, hour_bins, updated_ts)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setLong(2, swings);
                insert.setString(3, intervalBins);
                insert.setString(4, hourBins);
                insert.setLong(5, ts);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("alt_behavior")
                                + " SET swings = ?, interval_bins = ?, hour_bins = ?, updated_ts = ?"
                                + " WHERE player_uuid = ?")) {
                    update.setLong(1, swings);
                    update.setString(2, intervalBins);
                    update.setString(3, hourBins);
                    update.setLong(4, ts);
                    update.setString(5, playerId.toString());
                    update.executeUpdate();
                }
            }
        }
    }

    public void upsertFlag(UUID a, UUID b, double score, String level, String evidence,
                           long now) throws SQLException {
        UUID first = a.toString().compareTo(b.toString()) <= 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        try (Connection connection = databaseService.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + databaseService.table("alt_flags")
                            + " (uuid_a, uuid_b, score, level, evidence, status, created_ts, updated_ts)"
                            + " VALUES (?, ?, ?, ?, ?, 'NEW', ?, ?)")) {
                insert.setString(1, first.toString());
                insert.setString(2, second.toString());
                insert.setDouble(3, score);
                insert.setString(4, level);
                insert.setString(5, evidence);
                insert.setLong(6, now);
                insert.setLong(7, now);
                insert.executeUpdate();
            } catch (SQLException duplicate) {
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE " + databaseService.table("alt_flags")
                                + " SET score = ?, level = ?, evidence = ?, updated_ts = ?"
                                + " WHERE uuid_a = ? AND uuid_b = ? AND status <> 'DISMISSED'")) {
                    update.setDouble(1, score);
                    update.setString(2, level);
                    update.setString(3, evidence);
                    update.setLong(4, now);
                    update.setString(5, first.toString());
                    update.setString(6, second.toString());
                    update.executeUpdate();
                }
            }
        }
    }

    /** Flags that still restrict matching (not dismissed by an admin). */
    public List<FlagRow> loadActiveFlags() throws SQLException {
        List<FlagRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid_a, uuid_b, score, level, evidence, status, created_ts, updated_ts"
                             + " FROM " + databaseService.table("alt_flags")
                             + " WHERE status <> 'DISMISSED' ORDER BY score DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new FlagRow(UUID.fromString(rs.getString(1)),
                        UUID.fromString(rs.getString(2)), rs.getDouble(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8)));
            }
        }
        return rows;
    }

    public void dismissFlag(UUID a, UUID b) throws SQLException {
        UUID first = a.toString().compareTo(b.toString()) <= 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        try (Connection connection = databaseService.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + databaseService.table("alt_flags")
                             + " SET status = 'DISMISSED' WHERE uuid_a = ? AND uuid_b = ?")) {
            ps.setString(1, first.toString());
            ps.setString(2, second.toString());
            ps.executeUpdate();
        }
    }
}
