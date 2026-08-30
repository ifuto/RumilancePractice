/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.rumilance.practice.database.DatabaseService
 *  com.rumilance.practice.database.SchemaMigrator$Migration
 */
package com.rumilance.practice.database;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.database.SchemaMigrator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class SchemaMigrator {
    private final DatabaseService databaseService;
    private final Logger logger;

    public SchemaMigrator(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    /*
     * Loose catch block
     */
    public int migrate() throws SQLException {
        try (Connection connection = this.databaseService.getConnection();){
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                this.ensureVersionTable(connection);
                int currentVersion = this.readCurrentVersion(connection);
                int applied = 0;
                for (Migration migration : this.migrations()) {
                    if (migration.version() <= currentVersion) continue;
                    this.logger.info(() -> "Applying database migration v" + migration.version() + ": " + migration.description());
                    try (Statement statement = connection.createStatement();){
                        for (String sql : migration.statements()) {
                            statement.executeUpdate(sql);
                        }
                    }
                    this.recordVersion(connection, migration.version());
                    ++applied;
                }
                connection.commit();
                int n = applied;
                return n;
            }
            catch (SQLException e) {
                connection.rollback();
                throw e;
            }
            finally {
                connection.setAutoCommit(previousAutoCommit);
            }
            {
                catch (Throwable throwable) {
                    throw throwable;
                }
            }
        }
    }

    private void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();){
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("schema_version") + " (version INTEGER PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private int readCurrentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();){
            int n;
            block16: {
                ResultSet resultSet;
                block14: {
                    int n2;
                    block15: {
                        resultSet = statement.executeQuery("SELECT MAX(version) AS current_version FROM " + this.databaseService.table("schema_version"));
                        try {
                            if (!resultSet.next()) break block14;
                            int value = resultSet.getInt("current_version");
                            int n3 = n2 = resultSet.wasNull() ? 0 : value;
                            if (resultSet == null) break block15;
                        }
                        catch (Throwable throwable) {
                            if (resultSet != null) {
                                try {
                                    resultSet.close();
                                }
                                catch (Throwable throwable2) {
                                    throwable.addSuppressed(throwable2);
                                }
                            }
                            throw throwable;
                        }
                        resultSet.close();
                    }
                    return n2;
                }
                n = 0;
                if (resultSet == null) break block16;
                resultSet.close();
            }
            return n;
        }
    }

    private void recordVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + this.databaseService.table("schema_version") + " (version) VALUES (?)");){
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    private List<Migration> migrations() {
        ArrayList<Migration> migrations = new ArrayList<Migration>();
        migrations.add(new Migration(1, "create players table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("players") + " (uuid CHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL, first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP, locale VARCHAR(16))")));
        migrations.add(new Migration(2, "create player_settings table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("player_settings") + " (uuid CHAR(36) PRIMARY KEY, sounds_enabled INTEGER NOT NULL DEFAULT 1, scoreboard_enabled INTEGER NOT NULL DEFAULT 1, arrow_effect VARCHAR(32) NOT NULL DEFAULT 'none', spectate_visible INTEGER NOT NULL DEFAULT 1, accept_duel_requests INTEGER NOT NULL DEFAULT 1, locale VARCHAR(16))")));
        migrations.add(new Migration(3, "create ranked_stats table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("ranked_stats") + " (id CHAR(36) PRIMARY KEY, uuid CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, elo INTEGER NOT NULL DEFAULT 1000, wins INTEGER NOT NULL DEFAULT 0, losses INTEGER NOT NULL DEFAULT 0, win_streak INTEGER NOT NULL DEFAULT 0, best_elo INTEGER NOT NULL DEFAULT 1000, CONSTRAINT uq_ranked_stats_uuid_kit UNIQUE (uuid, kit))")));
        migrations.add(new Migration(4, "create match_history table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("match_history") + " (id CHAR(36) PRIMARY KEY, player_a CHAR(36) NOT NULL, player_b CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, mode VARCHAR(32) NOT NULL, winner CHAR(36), ranked INTEGER NOT NULL DEFAULT 0, started_at TIMESTAMP, ended_at TIMESTAMP)")));
        migrations.add(new Migration(5, "create punishments table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("punishments") + " (id CHAR(36) PRIMARY KEY, target_uuid CHAR(36) NOT NULL, staff_uuid CHAR(36), type VARCHAR(32) NOT NULL, reason VARCHAR(256), issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, expires_at TIMESTAMP, revoked INTEGER NOT NULL DEFAULT 0)")));
        migrations.add(new Migration(6, "create audit_log table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("audit_log") + " (id CHAR(36) PRIMARY KEY, actor_uuid CHAR(36), action VARCHAR(64) NOT NULL, details VARCHAR(512), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")));
        migrations.add(new Migration(7, "create kit_layouts table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("kit_layouts") + " (id CHAR(36) PRIMARY KEY, uuid CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, item_data TEXT NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uq_kit_layouts_uuid_kit UNIQUE (uuid, kit))")));
        migrations.add(new Migration(8, "create original_kits table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("original_kits") + " (uuid CHAR(36) PRIMARY KEY, item_data TEXT NOT NULL, armor_data TEXT, saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")));
        migrations.add(new Migration(9, "extend player_settings columns", List.of("ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN auto_requeue INTEGER NOT NULL DEFAULT 0", "ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN hide_other_chat INTEGER NOT NULL DEFAULT 0", "ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN chat_whitelist TEXT")));
        migrations.add(new Migration(10, "create daily_ranked_stats table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("daily_ranked_stats") + " (player_uuid CHAR(36) NOT NULL, stat_date CHAR(10) NOT NULL, kills INTEGER NOT NULL DEFAULT 0, matches INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (player_uuid, stat_date))")));
        migrations.add(new Migration(11, "create objections table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("objections") + " (id CHAR(36) PRIMARY KEY, chatban_id CHAR(36) NOT NULL, player_uuid CHAR(36) NOT NULL, reason VARCHAR(512) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, staff_uuid CHAR(36), staff_note VARCHAR(512))")));
        migrations.add(new Migration(12, "create ffa_stats table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("ffa_stats") + " (player_uuid CHAR(36) NOT NULL, arena_id VARCHAR(64) NOT NULL, kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (player_uuid, arena_id))")));
        migrations.add(new Migration(13, "create per-slot original kits table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("original_kit_slots") + " (uuid CHAR(36) NOT NULL, slot INTEGER NOT NULL, item_data TEXT NOT NULL, armor_data TEXT, saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (uuid, slot))", "INSERT INTO " + this.databaseService.table("original_kit_slots") + " (uuid, slot, item_data, armor_data, saved_at) SELECT uuid, 22, item_data, armor_data, saved_at FROM " + this.databaseService.table("original_kits") + " WHERE item_data IS NOT NULL AND item_data <> ''")));
        migrations.add(new Migration(14, "add selected_title column to player_settings", List.of("ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN selected_title VARCHAR(64) NOT NULL DEFAULT 'none'")));
        migrations.add(new Migration(15, "add show_match_report column to player_settings", List.of("ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN show_match_report INTEGER NOT NULL DEFAULT 0")));
        migrations.add(new Migration(16, "add OpenSkill mu/sigma to ranked_stats", List.of("ALTER TABLE " + this.databaseService.table("ranked_stats") + " ADD COLUMN skill_mu DOUBLE NOT NULL DEFAULT 1000", "ALTER TABLE " + this.databaseService.table("ranked_stats") + " ADD COLUMN skill_sigma DOUBLE NOT NULL DEFAULT 250", "UPDATE " + this.databaseService.table("ranked_stats") + " SET skill_mu = elo, skill_sigma = CASE WHEN (wins + losses) = 0 THEN 250 WHEN (wins + losses) < 8 THEN 200 WHEN (wins + losses) < 20 THEN 140 WHEN (wins + losses) < 40 THEN 90 ELSE 60 END")));
        migrations.add(new Migration(17, "create player kit preset preferences", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("player_kit_presets") + " (uuid CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, preset VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, kit))")));
        migrations.add(new Migration(18, "create unified win_streaks table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("win_streaks") + " (uuid CHAR(36) PRIMARY KEY, username VARCHAR(16) NOT NULL, current_streak INTEGER NOT NULL DEFAULT 0, best_streak INTEGER NOT NULL DEFAULT 0, month_key CHAR(7) NOT NULL, month_best INTEGER NOT NULL DEFAULT 0)")));
        migrations.add(new Migration(19, "create player_reports table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("player_reports") + " (id CHAR(36) PRIMARY KEY, reporter_uuid CHAR(36) NOT NULL, reporter_name VARCHAR(16) NOT NULL, target_uuid CHAR(36) NOT NULL, target_name VARCHAR(16) NOT NULL, match_id CHAR(36) NOT NULL, reason VARCHAR(64) NOT NULL, kit VARCHAR(64), mode VARCHAR(32), status VARCHAR(32) NOT NULL DEFAULT 'PENDING', evidence_path VARCHAR(256), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")));
        migrations.add(new Migration(20, "add block_tell to punishments", List.of("ALTER TABLE " + this.databaseService.table("punishments") + " ADD COLUMN block_tell INTEGER NOT NULL DEFAULT 0")));
        migrations.add(new Migration(21, "create spam_detections table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("spam_detections") + " (player_uuid CHAR(36) PRIMARY KEY, detection_count INTEGER NOT NULL DEFAULT 0, auto_ban_count INTEGER NOT NULL DEFAULT 0, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)")));
        migrations.add(new Migration(22, "create kit-scoped win_streaks table", List.of("CREATE TABLE IF NOT EXISTS " + this.databaseService.table("kit_win_streaks") + " (uuid CHAR(36) NOT NULL, kit VARCHAR(64) NOT NULL, username VARCHAR(16) NOT NULL, current_streak INTEGER NOT NULL DEFAULT 0, best_streak INTEGER NOT NULL DEFAULT 0, month_key CHAR(7) NOT NULL, month_best INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, kit))")));
        migrations.add(new Migration(23, "add team_colored_armor column to player_settings", List.of("ALTER TABLE " + this.databaseService.table("player_settings") + " ADD COLUMN team_colored_armor INTEGER NOT NULL DEFAULT 0")));
        return migrations;
    }
}
