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

    @FunctionalInterface
    private interface MigrationAction {
        void apply(Connection connection) throws SQLException;
    }

    private record Migration(int version, String description, List<String> statements, MigrationAction action) {
        Migration(int version, String description, List<String> statements) {
            this(version, description, statements, null);
        }

        Migration(int version, String description, MigrationAction action) {
            this(version, description, List.of(), action);
        }
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
                    if (migration.action() != null) {
                        migration.action().apply(connection);
                    } else {
                        try (Statement statement = connection.createStatement()) {
                            for (String sql : migration.statements()) {
                                statement.executeUpdate(sql);
                            }
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

        migrations.add(new Migration(13, "create per-slot original kits table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("original_kit_slots") + " ("
                        + "uuid CHAR(36) NOT NULL, "
                        + "slot INTEGER NOT NULL, "
                        + "item_data TEXT NOT NULL, "
                        + "armor_data TEXT, "
                        + "saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "PRIMARY KEY (uuid, slot)"
                        + ")",
                "INSERT INTO " + databaseService.table("original_kit_slots")
                        + " (uuid, slot, item_data, armor_data, saved_at) "
                        + "SELECT uuid, 22, item_data, armor_data, saved_at FROM "
                        + databaseService.table("original_kits")
                        + " WHERE item_data IS NOT NULL AND item_data <> ''"
        )));

        migrations.add(new Migration(14, "add selected_title column to player_settings", List.of(
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN selected_title VARCHAR(64) NOT NULL DEFAULT 'none'"
        )));

        migrations.add(new Migration(15, "add show_match_report column to player_settings", List.of(
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN show_match_report INTEGER NOT NULL DEFAULT 0"
        )));

        migrations.add(new Migration(16, "add team glow / leather armor settings", List.of(
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN team_glow INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE " + databaseService.table("player_settings")
                        + " ADD COLUMN team_colored_armor INTEGER NOT NULL DEFAULT 1"
        )));

        // Floodgate Bedrock names are ".XboxName" and can exceed Java's historic 16-char limit.
        // MariaDB enforces VARCHAR length; SQLite ignores declared length (noop UPDATE).
        if (databaseService.type() == DatabaseType.MARIADB) {
            migrations.add(new Migration(17, "widen players.username for Bedrock/Floodgate", List.of(
                    "ALTER TABLE " + databaseService.table("players")
                            + " MODIFY COLUMN username VARCHAR(32) NOT NULL"
            )));
        } else {
            migrations.add(new Migration(17, "widen players.username for Bedrock/Floodgate (sqlite noop)", List.of(
                    "UPDATE " + databaseService.table("players") + " SET username = username WHERE 0 = 1"
            )));
        }

        migrations.add(new Migration(18, "create practice_layouts table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("practice_layouts") + " ("
                        + "uuid CHAR(36) NOT NULL, "
                        + "practice_id VARCHAR(64) NOT NULL, "
                        + "layout_key VARCHAR(32) NOT NULL, "
                        + "contents TEXT NOT NULL, "
                        + "last_used TIMESTAMP NOT NULL, "
                        + "PRIMARY KEY (uuid, practice_id, layout_key)"
                        + ")"
        )));

        migrations.add(new Migration(19, "create unified win_streaks table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("win_streaks") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "username VARCHAR(32) NOT NULL, "
                        + "current_streak INTEGER NOT NULL DEFAULT 0, "
                        + "best_streak INTEGER NOT NULL DEFAULT 0, "
                        + "month_key CHAR(7) NOT NULL, "
                        + "month_best INTEGER NOT NULL DEFAULT 0"
                        + ")"
        )));

        migrations.add(new Migration(20, "create player_reports table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("player_reports") + " ("
                        + "id CHAR(36) PRIMARY KEY, "
                        + "reporter_uuid CHAR(36) NOT NULL, "
                        + "reporter_name VARCHAR(32) NOT NULL, "
                        + "target_uuid CHAR(36) NOT NULL, "
                        + "target_name VARCHAR(32) NOT NULL, "
                        + "match_id CHAR(36) NOT NULL, "
                        + "reason VARCHAR(64) NOT NULL, "
                        + "kit VARCHAR(64), "
                        + "mode VARCHAR(32), "
                        + "status VARCHAR(32) NOT NULL DEFAULT 'PENDING', "
                        + "evidence_path VARCHAR(256), "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
        )));

        migrations.add(new Migration(21, "add block_tell to punishments", List.of(
                "ALTER TABLE " + databaseService.table("punishments")
                        + " ADD COLUMN block_tell INTEGER NOT NULL DEFAULT 0"
        )));

        migrations.add(new Migration(22, "create spam_detections table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("spam_detections") + " ("
                        + "player_uuid CHAR(36) PRIMARY KEY, "
                        + "detection_count INTEGER NOT NULL DEFAULT 0, "
                        + "auto_ban_count INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ")"
        )));

        migrations.add(new Migration(23, "create kit-scoped win_streaks table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("kit_win_streaks") + " ("
                        + "uuid CHAR(36) NOT NULL, "
                        + "kit VARCHAR(64) NOT NULL, "
                        + "username VARCHAR(32) NOT NULL, "
                        + "current_streak INTEGER NOT NULL DEFAULT 0, "
                        + "best_streak INTEGER NOT NULL DEFAULT 0, "
                        + "month_key CHAR(7) NOT NULL, "
                        + "month_best INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, kit)"
                        + ")"
        )));

        migrations.add(new Migration(24, "create player_ranks table", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("player_ranks") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "rank_id VARCHAR(32) NOT NULL"
                        + ")"
        )));

        migrations.add(new Migration(25, "create player kit preset preferences", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("player_kit_presets") + " ("
                        + "uuid CHAR(36) NOT NULL, "
                        + "kit VARCHAR(64) NOT NULL, "
                        + "preset VARCHAR(64) NOT NULL, "
                        + "PRIMARY KEY (uuid, kit)"
                        + ")"
        )));

        // Repair: migration 16 may have been skipped/failed while schema_version still advanced.
        migrations.add(new Migration(26, "ensure team_glow / team_colored_armor columns", connection -> {
            String table = databaseService.table("player_settings");
            databaseService.ensureColumn(connection, table, "team_glow",
                    "INTEGER NOT NULL DEFAULT 1");
            databaseService.ensureColumn(connection, table, "team_colored_armor",
                    "INTEGER NOT NULL DEFAULT 1");
        }));

        migrations.add(new Migration(27, "add kill_effect column to player_settings", connection -> {
            String table = databaseService.table("player_settings");
            databaseService.ensureColumn(connection, table, "kill_effect",
                    "VARCHAR(64) NOT NULL DEFAULT 'none'");
        }));

        migrations.add(new Migration(28, "create player_name_colors table (VIP+ name colors)", List.of(
                "CREATE TABLE IF NOT EXISTS " + databaseService.table("player_name_colors") + " ("
                        + "uuid CHAR(36) PRIMARY KEY, "
                        + "mode VARCHAR(16) NOT NULL DEFAULT 'none', "
                        + "primary_color VARCHAR(8) NOT NULL DEFAULT '', "
                        + "secondary_color VARCHAR(8) NOT NULL DEFAULT '', "
                        + "changed_at BIGINT NOT NULL DEFAULT 0"
                        + ")"
        )));

        migrations.add(new Migration(29, "add deaths column to daily_ranked_stats", connection -> {
            String table = databaseService.table("daily_ranked_stats");
            databaseService.ensureColumn(connection, table, "deaths",
                    "INTEGER NOT NULL DEFAULT 0");
        }));

        return migrations;
    }
}
