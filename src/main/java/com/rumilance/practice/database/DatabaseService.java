package com.rumilance.practice.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Owns the HikariCP connection pool for either SQLite (default, file-based, zero-setup) or
 * MariaDB/MySQL (for larger networks), configured entirely from {@code database.yml}.
 */
public final class DatabaseService implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DatabaseType type;
    private final String tablePrefix;

    public DatabaseService(FileConfiguration databaseConfig, File dataFolder) {
        Objects.requireNonNull(databaseConfig, "databaseConfig");
        Objects.requireNonNull(dataFolder, "dataFolder");

        this.type = DatabaseType.from(databaseConfig.getString("storage.type", "SQLITE"));
        this.tablePrefix = databaseConfig.getString("schema.table-prefix", "rp_");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(databaseConfig.getString("pool.pool-name", "RumilancePracticePool"));
        hikariConfig.setMinimumIdle(databaseConfig.getInt("pool.minimum-idle", 2));
        hikariConfig.setConnectionTimeout(databaseConfig.getLong("pool.connection-timeout-ms", 10_000L));
        hikariConfig.setIdleTimeout(databaseConfig.getLong("pool.idle-timeout-ms", 600_000L));
        hikariConfig.setMaxLifetime(databaseConfig.getLong("pool.max-lifetime-ms", 1_800_000L));
        long leakThreshold = databaseConfig.getLong("pool.leak-detection-threshold-ms", 0L);
        if (leakThreshold > 0) {
            hikariConfig.setLeakDetectionThreshold(leakThreshold);
        }

        switch (type) {
            case SQLITE -> configureSqlite(hikariConfig, databaseConfig, dataFolder);
            case MARIADB -> configureMariaDb(hikariConfig, databaseConfig);
        }

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    private void configureSqlite(HikariConfig hikariConfig, FileConfiguration databaseConfig, File dataFolder) {
        String relativePath = databaseConfig.getString("storage.sqlite.file", "data/rumilance-practice.db");
        File dbFile = new File(dataFolder, relativePath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create SQLite data directory: " + parent);
        }
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        // SQLite only supports a single writer at a time; a larger pool just causes lock contention.
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setMinimumIdle(1);
    }

    private void configureMariaDb(HikariConfig hikariConfig, FileConfiguration databaseConfig) {
        String host = databaseConfig.getString("storage.mariadb.host", "127.0.0.1");
        int port = databaseConfig.getInt("storage.mariadb.port", 3306);
        String database = databaseConfig.getString("storage.mariadb.database", "rumilance_practice");
        String parameters = databaseConfig.getString("storage.mariadb.parameters", "");
        boolean useSsl = databaseConfig.getBoolean("storage.mariadb.use-ssl", false);

        StringBuilder url = new StringBuilder("jdbc:mariadb://")
                .append(host).append(':').append(port).append('/').append(database)
                .append("?useSSL=").append(useSsl);
        if (parameters != null && !parameters.isBlank()) {
            url.append('&').append(parameters);
        }

        hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
        hikariConfig.setJdbcUrl(url.toString());
        hikariConfig.setUsername(databaseConfig.getString("storage.mariadb.username", "rumilance"));
        hikariConfig.setPassword(databaseConfig.getString("storage.mariadb.password", ""));
        hikariConfig.setMaximumPoolSize(Math.max(1, databaseConfig.getInt("pool.maximum-pool-size", 10)));
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DatabaseType type() {
        return type;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public String table(String name) {
        return tablePrefix + name;
    }

    /**
     * Builds an "upsert" clause appropriate for the currently configured backend, to be
     * appended after an {@code INSERT INTO ... VALUES (...)} statement:
     * <ul>
     *   <li>SQLite: {@code ON CONFLICT (conflictColumns) DO UPDATE SET col = excluded.col, ...}</li>
     *   <li>MariaDB/MySQL: {@code ON DUPLICATE KEY UPDATE col = VALUES(col), ...}</li>
     * </ul>
     * {@code conflictColumns} must name a column covered by a {@code PRIMARY KEY} or
     * {@code UNIQUE} constraint (required by SQLite's syntax; ignored by MariaDB's).
     */
    public String upsertClause(String conflictColumns, String... updatedColumns) {
        StringBuilder builder = new StringBuilder();
        if (type == DatabaseType.MARIADB) {
            builder.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updatedColumns.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                String column = updatedColumns[i];
                builder.append(column).append(" = VALUES(").append(column).append(')');
            }
        } else {
            builder.append("ON CONFLICT (").append(conflictColumns).append(") DO UPDATE SET ");
            for (int i = 0; i < updatedColumns.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                String column = updatedColumns[i];
                builder.append(column).append(" = excluded.").append(column);
            }
        }
        return builder.toString();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
