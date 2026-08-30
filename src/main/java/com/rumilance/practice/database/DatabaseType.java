package com.rumilance.practice.database;

import java.util.Locale;

/**
 * Supported relational storage backends. {@code MYSQL} is accepted as a configuration alias
 * for {@link #MARIADB} since the two are wire-protocol compatible for our purposes.
 */
public enum DatabaseType {
    SQLITE,
    MARIADB;

    public static DatabaseType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SQLITE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("MYSQL")) {
            return MARIADB;
        }
        return DatabaseType.valueOf(normalized);
    }
}
