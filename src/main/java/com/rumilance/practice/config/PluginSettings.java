package com.rumilance.practice.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Strongly-typed, immutable snapshot of {@code config.yml}. A new snapshot is built every
 * time the configuration is (re)loaded; nothing here mutates in place.
 */
public record PluginSettings(
        String defaultLocale,
        boolean debug,
        int executorMinThreads,
        int executorMaxThreads,
        int executorCoreThreads,
        long executorKeepAliveSeconds,
        int matchCountdownSeconds,
        int matchMaxDurationSeconds,
        int reconnectGraceSeconds,
        boolean regenerateArena,
        int endTeleportDelaySeconds,
        int queueMaxWaitNotifySeconds,
        int queueInitialEloRange,
        int queueEloRangeGrowthPerInterval,
        int queueGrowthIntervalSeconds,
        int queueMaxRankedPingMs,
        int rankedStartingElo,
        int rankedProvisionalGames,
        int rankedProvisionalK,
        int rankedStandardK,
        int rankedTopPercentK,
        double rankedTopPercentFraction,
        boolean spectatorAllowFlight,
        boolean spectatorHideFromPlayers,
        String lobbyWorld,
        boolean lobbyReturnOnDeath,
        boolean faweEnabled,
        int faweOperationTimeoutSeconds,
        boolean placeholderApiHook,
        boolean scoreboardEnabled,
        int scoreboardUpdateIntervalTicks,
        boolean tabHeaderFooterEnabled,
        String scoreboardServerName,
        String scoreboardServerIp,
        int particleLimitPerPlayer,
        boolean maintenanceMode
) {

    public static PluginSettings from(FileConfiguration config) {
        return new PluginSettings(
                config.getString("plugin.default-locale", "en_us"),
                config.getBoolean("plugin.debug", false),
                config.getInt("executor.min-threads", 1),
                config.getInt("executor.max-threads", 4),
                config.getInt("executor.core-threads", 2),
                config.getLong("executor.keep-alive-seconds", 60L),
                config.getInt("match.countdown-seconds", 5),
                config.getInt("match.max-duration-seconds", 0),
                config.getInt("match.reconnect-grace-seconds", 30),
                config.getBoolean("match.regenerate-arena", true),
                config.getInt("match.end-teleport-delay-seconds", 5),
                config.getInt("queue.max-wait-notify-seconds", 60),
                config.getInt("queue.initial-elo-range", 75),
                config.getInt("queue.elo-range-growth-per-interval", 25),
                config.getInt("queue.growth-interval-seconds", 15),
                config.getInt("queue.max-ranked-ping-ms", 0),
                config.getInt("ranking.starting-elo", 1000),
                config.getInt("ranking.k-factor.provisional-games", 20),
                config.getInt("ranking.k-factor.provisional-k", 64),
                config.getInt("ranking.k-factor.standard-k", 32),
                config.getInt("ranking.k-factor.top-percent-k", 26),
                config.getDouble("ranking.k-factor.top-percent-fraction", 0.10d),
                config.getBoolean("spectator.allow-flight", true),
                config.getBoolean("spectator.hide-from-players", true),
                config.getString("lobby.world", "world"),
                config.getBoolean("lobby.return-to-lobby-on-death", false),
                config.getBoolean("fawe.enabled", true),
                config.getInt("fawe.operation-timeout-seconds", 30),
                config.getBoolean("hooks.placeholderapi", true),
                config.getBoolean("scoreboard.enabled", true),
                config.getInt("scoreboard.update-interval-ticks", 20),
                config.getBoolean("scoreboard.tab-header-footer", true),
                config.getString("scoreboard.server-name", "N."),
                config.getString("scoreboard.server-ip", "play.example.com"),
                config.getInt("performance.particle-limit-per-player", 40),
                config.getBoolean("plugin.maintenance", false)
        );
    }
}
