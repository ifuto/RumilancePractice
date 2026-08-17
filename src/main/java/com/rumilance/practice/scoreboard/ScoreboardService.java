package com.rumilance.practice.scoreboard;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.stats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central scoreboard updater (10-20 tick interval), not per-player timers.
 *
 * <p>v1.2.1: dense, bright layout. Server branding on top, per-player ranked stats
 * (Elo / W-L / K-D / streak / matches / kits) cached for 3s, full match context
 * (teams + Elos, series score, kit, mode, kills, time) while fighting, queue info
 * while queued, and the server IP pinned to the very bottom.</p>
 */
public final class ScoreboardService {

    private static final long STATS_CACHE_MS = 3000L;

    private record CachedStats(int bestElo, int wins, int losses, int bestStreak, int matches, int kits) {
        static final CachedStats EMPTY = new CachedStats(1000, 0, 0, 0, 0, 0);

        double kd() {
            return (double) wins / Math.max(1, losses);
        }
    }

    private final Plugin plugin;
    private final PluginSettings settings;
    private final PlayerStateManager stateManager;
    private final QueueService queueService;
    private final MatchRegistry matchRegistry;
    private final RankedStatsRepository rankedStatsRepository;
    private final SettingsService settingsService;
    private final ConcurrentMap<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> statsCacheAt = new ConcurrentHashMap<>();
    private BukkitTask task;

    public ScoreboardService(
            Plugin plugin,
            PluginSettings settings,
            PlayerStateManager stateManager,
            QueueService queueService,
            MatchRegistry matchRegistry,
            RankedStatsRepository rankedStatsRepository,
            SettingsService settingsService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.stateManager = stateManager;
        this.queueService = queueService;
        this.matchRegistry = matchRegistry;
        this.rankedStatsRepository = rankedStatsRepository;
        this.settingsService = settingsService;
    }

    public void start() {
        int interval = Math.max(10, Math.min(20, settings.scoreboardUpdateIntervalTicks()));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        statsCache.clear();
        statsCacheAt.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!settings.scoreboardEnabled() || !settingsService.get(player).scoreboardEnabled()) {
                if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            update(player);
            if (settings.tabHeaderFooterEnabled()) {
                player.sendPlayerListHeaderAndFooter(
                        Component.text("§b✦ §f" + settings.scoreboardServerName() + " §b✦"),
                        Component.text("§b" + settings.scoreboardServerIp() + "§f  |  §7Online: §f"
                                + Bukkit.getOnlinePlayers().size())
                );
            }
        }
    }

    // ------------------------------------------------------------------ stats

    private CachedStats cachedStats(UUID uuid) {
        long now = System.currentTimeMillis();
        Long updated = statsCacheAt.get(uuid);
        if (updated != null && now - updated < STATS_CACHE_MS) {
            CachedStats cached = statsCache.get(uuid);
            if (cached != null) {
                return cached;
            }
        }
        CachedStats computed = loadStats(uuid);
        statsCache.put(uuid, computed);
        statsCacheAt.put(uuid, now);
        return computed;
    }

    private CachedStats loadStats(UUID uuid) {
        try {
            List<com.rumilance.practice.model.RankedKitStats> kits =
                    rankedStatsRepository.findAllForPlayer(uuid);
            int bestElo = 0;
            int wins = 0;
            int losses = 0;
            int bestStreak = 0;
            for (com.rumilance.practice.model.RankedKitStats stats : kits) {
                bestElo = Math.max(bestElo, stats.elo());
                wins += stats.wins();
                losses += stats.losses();
                bestStreak = Math.max(bestStreak, stats.winStreak());
            }
            return new CachedStats(bestElo, wins, losses, bestStreak, wins + losses, kits.size());
        } catch (Exception e) {
            return CachedStats.EMPTY;
        }
    }

    private static String modeLabel(MatchMode mode) {
        return switch (mode) {
            case RANKED -> "ランク";
            case UNRANKED -> "アンランク";
            case FFA -> "FFA";
            case TEAM -> "チーム";
        };
    }

    private static String fmtTime(long secs) {
        if (secs < 60) {
            return secs + "s";
        }
        return (secs / 60) + "m" + (secs % 60) + "s";
    }

    // ------------------------------------------------------------------ render

    private void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("rp", Criteria.DUMMY,
                Component.text(settings.scoreboardServerName(), NamedTextColor.WHITE));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerState state = stateManager.getState(player.getUniqueId());
        int line = 15;

        // --- header (always; one piece of information per line) ---
        objective.getScore("§b✦ §f" + settings.scoreboardServerName() + " §b✦").setScore(line--);
        objective.getScore("§7Online: §f" + Bukkit.getOnlinePlayers().size()).setScore(line--);
        objective.getScore("§r").setScore(line--);

        Optional<MatchSession> match = matchRegistry.byPlayer(player.getUniqueId());
        if (match.isPresent()) {
            line = renderMatch(player, objective, match.get(), line);
        } else if (state == PlayerState.QUEUED_RANKED || state == PlayerState.QUEUED_UNRANKED) {
            line = renderQueued(player, objective, line);
        } else {
            line = renderStats(player, objective, line);
        }

        objective.getScore("§r").setScore(2);
        objective.getScore("§b" + settings.scoreboardServerIp()).setScore(1);

        player.setScoreboard(board);
    }

    private int renderStats(Player player, Objective objective, int line) {
        CachedStats stats = cachedStats(player.getUniqueId());
        // One piece of information per line for readability.
        objective.getScore("§b┃ §fあなたの統計 §b┃").setScore(line--);
        objective.getScore("§bElo: §f" + stats.bestElo()).setScore(line--);
        objective.getScore("§aWins: §f" + stats.wins()).setScore(line--);
        objective.getScore("§cLosses: §f" + stats.losses()).setScore(line--);
        objective.getScore("§eK/D: §f" + String.format("%.2f", stats.kd())).setScore(line--);
        objective.getScore("§dStreak: §f" + stats.bestStreak()).setScore(line--);
        objective.getScore("§7Matches: §f" + stats.matches()).setScore(line--);
        return line;
    }

    private int renderQueued(Player player, Objective objective, int line) {
        var entry = queueService.get(player.getUniqueId());
        if (entry.isPresent()) {
            long waited = Math.max(0, Instant.now().getEpochSecond()
                    - entry.get().joinedAt().getEpochSecond());
            int waiting = queueService.waitingCount(entry.get().mode(), entry.get().kitId());
            // One piece of information per line.
            objective.getScore("§b┃ §fキュー中 §b┃").setScore(line--);
            objective.getScore("§7Kit: §b"
                    + com.rumilance.practice.util.KitNames.pretty(entry.get().kitId())).setScore(line--);
            objective.getScore("§7Mode: §a" + modeLabel(entry.get().mode())).setScore(line--);
            objective.getScore("§7Wait: §e" + fmtTime(waited)).setScore(line--);
            objective.getScore("§7Queue: §f" + waiting).setScore(line--);
        }
        objective.getScore("§r").setScore(line--);
        return renderStats(player, objective, line);
    }

    private int renderMatch(Player player, Objective objective, MatchSession session, int line) {
        UUID me = player.getUniqueId();
        TeamColor myColor = session.teamColor(me);
        String myCode = myColor == TeamColor.RED ? "§c" : "§9";
        String oppCode = myColor == TeamColor.RED ? "§9" : "§c";
        CachedStats myStats = cachedStats(me);

        if (session.isTeamMatch()) {
            return renderTeamMatch(player, objective, session, line, me, myColor, myCode, oppCode, myStats);
        }

        // --- 1v1 (one piece of information per line; opponent Elo stays private) ---
        UUID opponent = session.opponentOf(me);
        objective.getScore(myCode + "You: §f" + player.getName()).setScore(line--);
        objective.getScore("§7Elo: §f" + myStats.bestElo()).setScore(line--);
        if (opponent != null) {
            objective.getScore(oppCode + "Foe: §f" + StatsService.nameOf(opponent)).setScore(line--);
        }
        objective.getScore("§r").setScore(line--);

        int myWins = session.seriesWinsOf(me);
        int oppWins = opponent == null ? 0 : session.seriesWinsOf(opponent);
        objective.getScore("§7Score: " + myCode + myWins + " §7- " + oppCode + oppWins).setScore(line--);
        objective.getScore("§7Kit: §b" + com.rumilance.practice.util.KitNames.pretty(session.kitName())).setScore(line--);
        objective.getScore("§7Mode: §a" + modeLabel(session.mode())).setScore(line--);
        objective.getScore("§7Kills: §f" + session.killsOf(me)).setScore(line--);
        if (session.startedAt() != null) {
            long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
            objective.getScore("§7Time: §f" + fmtTime(secs)).setScore(line--);
        }
        return line;
    }

    private int renderTeamMatch(Player player, Objective objective, MatchSession session, int line,
                                UUID me, TeamColor myColor, String myCode, String oppCode,
                                CachedStats myStats) {
        // Team battles can hold up to 15v15 players, far beyond the sidebar's ~15 line budget,
        // so show alive/total counts per side instead of a member list. Elo stays private:
        // no other player's Elo is ever rendered.
        TeamColor enemy = myColor.opposite();
        List<UUID> mySide = session.team(myColor);
        List<UUID> enemySide = session.team(enemy);

        // One piece of information per line.
        objective.getScore(myCode + "Your Team: §f" + countAlive(mySide) + "§7/" + mySide.size())
                .setScore(line--);
        objective.getScore(oppCode + "Enemy Team: §f" + countAlive(enemySide) + "§7/" + enemySide.size())
                .setScore(line--);
        objective.getScore("§r").setScore(line--);

        objective.getScore("§7Kit: §b" + com.rumilance.practice.util.KitNames.pretty(session.kitName())).setScore(line--);
        objective.getScore("§7Mode: §a" + modeLabel(session.mode())).setScore(line--);
        objective.getScore("§7Kills: §f" + session.killsOf(me)).setScore(line--);
        if (session.startedAt() != null) {
            long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
            objective.getScore("§7Time: §f" + fmtTime(secs)).setScore(line--);
        }
        return line;
    }

    /** @return how many of the given players are online and not spectating (i.e. still fighting). */
    private static int countAlive(List<UUID> members) {
        int alive = 0;
        for (UUID member : members) {
            Player p = org.bukkit.Bukkit.getPlayer(member);
            if (p != null && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                alive++;
            }
        }
        return alive;
    }
}
