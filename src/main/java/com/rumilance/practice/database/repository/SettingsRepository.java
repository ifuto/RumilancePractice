package com.rumilance.practice.database.repository;

import com.rumilance.practice.database.DatabaseService;
import com.rumilance.practice.model.PlayerSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence for per-player configurable preferences.
 */
public final class SettingsRepository {

    private final DatabaseService databaseService;

    public SettingsRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<PlayerSettings> findByUuid(UUID uuid) throws SQLException {
        String sql = "SELECT uuid, sounds_enabled, scoreboard_enabled, arrow_effect, spectate_visible, "
                + "accept_duel_requests, auto_requeue, hide_other_chat, chat_whitelist, locale FROM "
                + databaseService.table("player_settings") + " WHERE uuid = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(resultSet));
            }
        }
    }

    public PlayerSettings findOrDefault(UUID uuid, PlayerSettings defaults) throws SQLException {
        return findByUuid(uuid).orElse(defaults);
    }

    public void upsert(PlayerSettings settings) throws SQLException {
        String sql = "INSERT INTO " + databaseService.table("player_settings")
                + " (uuid, sounds_enabled, scoreboard_enabled, arrow_effect, spectate_visible, "
                + "accept_duel_requests, auto_requeue, hide_other_chat, chat_whitelist, locale) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + databaseService.upsertClause("uuid", "sounds_enabled", "scoreboard_enabled", "arrow_effect",
                "spectate_visible", "accept_duel_requests", "auto_requeue", "hide_other_chat",
                "chat_whitelist", "locale");
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, settings.uuid().toString());
            statement.setInt(2, settings.soundsEnabled() ? 1 : 0);
            statement.setInt(3, settings.scoreboardEnabled() ? 1 : 0);
            statement.setString(4, settings.arrowEffect());
            statement.setInt(5, settings.spectateVisible() ? 1 : 0);
            statement.setInt(6, settings.acceptDuelRequests() ? 1 : 0);
            statement.setInt(7, settings.autoRequeue() ? 1 : 0);
            statement.setInt(8, settings.hideOtherChat() ? 1 : 0);
            statement.setString(9, String.join(",", settings.chatWhitelist()));
            statement.setString(10, settings.locale());
            statement.executeUpdate();
        }
    }

    private PlayerSettings map(ResultSet resultSet) throws SQLException {
        String whitelistRaw = resultSet.getString("chat_whitelist");
        Set<String> whitelist = whitelistRaw == null || whitelistRaw.isBlank()
                ? Set.of()
                : Arrays.stream(whitelistRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new PlayerSettings(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getInt("sounds_enabled") != 0,
                resultSet.getInt("scoreboard_enabled") != 0,
                resultSet.getString("arrow_effect"),
                resultSet.getInt("spectate_visible") != 0,
                resultSet.getInt("accept_duel_requests") != 0,
                resultSet.getInt("auto_requeue") != 0,
                resultSet.getInt("hide_other_chat") != 0,
                whitelist,
                resultSet.getString("locale")
        );
    }
}
