package com.rumilance.practice.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applies an ordered, versioned set of DDL migrations against whichever backend
 * {@link DatabaseService} is configured for. All tables use {@code CHAR(36)} UUID primary
 * keys and plain ANSI-ish SQL so the exact same statements work unmodified on both SQLite
 * and MariaDB/MySQL.
 */
public final class SchemaMigrator {

    private record Migration(int version, String description, List<String> statements) {
    }

    private final DatabaseService databaseService;
    private final Logger logger;

    public SchemaMigrator(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    /**
     * Applies any migration whose version is greater than the highest version already recorded
     * in the {@code schema_version} table.
     *
     * @return the number of migrations that were newly applied.
     */
    public int migrate() throws SQLException {
        try (Connection connection = databaseService.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                ensureVersionTable(connection);
                int currentVersion = readCurrentVersion(connection);
                int applied = 0;
                for (Migration migration : migrations()) {
                    if (migration.version() <= currentVersion) {
                        continue;
                    }
                    logger.info(() -> "Applying database migration v" + migration.version() + ": " + migration.description());
                    try (Statement statement = connection.createStatement()) {
                        for (String sql : migration.statements()) {
                            statement.executeUpdate(sql);
                        }
                    }
                    recordVersion(connection, migration.version());
                    applied++;
                }
                connection.commit();
                return applied;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + databaseService.table("schema_version") + " ("
                    + "version INTEGER PRIMARY KEY, "
                    + "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ")");
        }
    }

    private int readCurrentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT MAX(version) AS current_version FROM " + databaseService.table("schema_version"))) {
            if (resultSet.next()) {
                int value = resultSet.getInt("current_version");
                return resultSet.wasNull() ? 0 : value;
            }
            return 0;
        }
    }

    private void recordVersion(Connection connection, int version) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO " + databaseService.table("schema_version") + " (version) VALUES (?)")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private List<Migration> migrations() {
        List<Migration> migrations = new ArrayList<>();

        migrations.add(new Migration(1, "create players table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("players") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "username VARCHAR(16) NOT NULL, "
                        + "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "locale VARCHAR(16)"
                        + ")"
        )));

        migrations.add(new Migration(2, "create player_settings table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("player_settings") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "sounds_enabled INTEGER NOT NULL DEFAULT 1, "
                        + "scoreboard_enabled INTEGER NOT NULL DEFAULT 1, "
                        + "arrow_effect VARCHAR(32) NOT NULL DEFAULT 'none', "
                        + "spectate_visible INTEGER NOT NULL DEFAULT 1, "
                        + "accept_duel_requests INTEGER NOT NULL DEFAULT 1, "
                        + "locale VARCHAR(16)"
                        + ")"
        )));

        migrations.add(new Migration(3, "create ranked_stats table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("ranked_stats") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "uuid CHAR(36) NOT NULL, "
                        + "kit VARCHAR(64) NOT NULL, "
                        + "elo INTEGER NOT NULL DEFAULT 1000, "
                        + "wins INTEGER NOT NULL DEFAULT 0, "
                        + "losses INTEGER NOT NULL DEFAULT 0, "
                        + "win_streak INTEGER NOT NULL DEFAULT 0, "
                        + "best_elo INTEGER NOT NULL DEFAULT 1000, "
                        + "CONSTRAINT uq_ranked_stats_uuid_kit UNIQUE (uuid, kit)"
                        + ")"
        )));

        migrations.add(new Migration(4, "create match_history table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("match_history") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "player_a CHAR(36) NOT NULL, "
                        + "player_b CHAR(36) NOT NULL, "
                        + "kit VARCHAR(64) NOT NULL, "
                        + "mode VARCHAR(32) NOT NULL, "
                        + "winner CHAR(36), "
                        + "ranked INTEGER NOT NULL DEFAULT 0, "
                        + "started_at TIMESTAMP, "
                        + "ended_at TIMESTAMP"
                        + ")"
        )));

        migrations.add(new Migration(5, "create punishments table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("punishments") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "target_uuid CHAR(36) NOT NULL, "
                        + "staff_uuid CHAR(36), "
                        + "type VARCHAR(32) NOT NULL, "
                        + "reason VARCHAR(256), "
                        + "issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "expires_at TIMESTAMP, "
                        + "revoked INTEGER NOT NULL DEFAULT 0"
                        + ")"
        )));

        migrations.add(new Migration(6, "create audit_log table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("audit_log") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "actor_uuid CHAR(36), "
                        + "action VARCHAR(64) NOT NULL, "
                        + "details VARCHAR(512), "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
        )));

        migrations.add(new Migration(7, "create kit_layouts table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("kit_layouts") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "uuid CHAR(36) NOT NULL, "
                        + "kit VARCHAR(64) NOT NULL, "
                        + "item_data TEXT NOT NULL, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "CONSTRAINT uq_kit_layouts_uuid_kit UNIQUE (uuid, kit)"
                        + ")"
        )));

        migrations.add(new Migration(8, "create original_kits table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("original_kits") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "item_data TEXT NOT NULL, "
                        + "armor_data TEXT, "
                        + "saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
        )));

        migrations.add(new Migration(9, "extend player_settings columns", List.of(
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN auto_requeue INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN hide_other_chat INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN chat_whitelist TEXT"
        )));

        migrations.add(new Migration(10, "create daily_ranked_stats table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("daily_ranked_stats") + " ("
                        + "player_uuid CHAR(36) NOT NULL, "
                        + "stat_date CHAR(10) NOT NULL, "
                        + "kills INTEGER NOT NULL DEFAULT 0, "
                        + "matches INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (player_uuid, stat_date)"
                        + ")"
        )));

        migrations.add(new Migration(11, "create objections table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("objections") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "chatban_id CHAR(36) NOT NULL, "
                        + "player_uuid CHAR(36) NOT NULL, "
                        + "reason VARCHAR(512) NOT NULL, "
                        + "status VARCHAR(32) NOT NULL DEFAULT 'PENDING', "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "staff_uuid CHAR(36), "
                        + "staff_note VARCHAR(512)"
                        + ")"
        )));

        migrations.add(new Migration(12, "create ffa_stats table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("ffa_stats") + " ("
                        + "player_uuid CHAR(36) NOT NULL, "
                        + "arena_id VARCHAR(64) NOT NULL, "
                        + "kills INTEGER NOT NULL DEFAULT 0, "
                        + "deaths INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (player_uuid, arena_id)"
                        + ")"
        )));

        return migrations;
    }
}
