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
            PluginSettings settings,
            PlayerStateManager stateManager,
            QueueService queueService,
            MatchRegistry matchRegistry,
            RankedStatsRepository rankedStatsRepository,
            SettingsService settingsService
    ) {
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
                        Component.text("§6✦ §f" + settings.scoreboardServerName() + " §6✦"),
                        Component.text("§b" + settings.scoreboardServerIp() + "§f  |  §eOnline: §a"
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

        // --- header (always) ---
        objective.getScore("§6✦ §f" + settings.scoreboardServerName() + " §6✦").setScore(line--);
        objective.getScore("§eOnline: §a" + Bukkit.getOnlinePlayers().size()
                + "  §eQueue: §b" + queueService.totalWaiting()
                + "  §eMatch: §d" + matchRegistry.activeCount()).setScore(line--);
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
        objective.getScore("§6┃ §fあなたの統計 §6┃").setScore(line--);
        objective.getScore("§bElo: §f" + stats.bestElo()
                + "  §7Kits: §e" + stats.kits()).setScore(line--);
        objective.getScore("§aWins: §f" + stats.wins()
                + "  §cLosses: §f" + stats.losses()).setScore(line--);
        objective.getScore("§eK/D: §f" + String.format("%.2f", stats.kd())
                + "  §dStreak: §f" + stats.bestStreak()).setScore(line--);
        objective.getScore("§bMatches: §f" + stats.matches()).setScore(line--);
        return line;
    }

    private int renderQueued(Player player, Objective objective, int line) {
        var entry = queueService.get(player.getUniqueId());
        if (entry.isPresent()) {
            long waited = Math.max(0, Instant.now().getEpochSecond()
                    - entry.get().joinedAt().getEpochSecond());
            int waiting = queueService.waitingCount(entry.get().mode(), entry.get().kitId());
            objective.getScore("§eQueued: §b" + entry.get().kitId()
                    + " §7(§a" + modeLabel(entry.get().mode()) + "§7)").setScore(line--);
            objective.getScore("§fWait: §e" + fmtTime(waited)
                    + "  §7Queue: §a" + waiting).setScore(line--);
        }
        objective.getScore("§r").setScore(line--);
        return renderStats(player, objective, line);
    }

    private int renderMatch(Player player, Objective objective, MatchSession session, int line) {
        UUID me = player.getUniqueId();
        TeamColor myColor = session.teamColor(me);
        String myCode = myColor == TeamColor.RED ? "§c" : "§9";
        String oppCode = myColor == TeamColor.RED ? "§9" : "§c";

        // --- team banner + elos ---
        objective.getScore(myCode + "▸ YOU").setScore(line--);
        CachedStats myStats = cachedStats(me);
        objective.getScore(myCode + player.getName()
                + "  §eElo: §a" + myStats.bestElo()).setScore(line--);
        UUID opponent = session.opponentOf(me);
        if (opponent != null) {
            objective.getScore(oppCode + "▸ OPPONENT").setScore(line--);
            CachedStats oppStats = cachedStats(opponent);
            objective.getScore(oppCode + StatsService.nameOf(opponent)
                    + "  §eElo: §a" + oppStats.bestElo()).setScore(line--);
        }
        objective.getScore("§r").setScore(line--);

        // --- series score (own color left, opponent color right) ---
        int myWins = session.seriesWinsOf(me);
        int oppWins = opponent == null ? 0 : session.seriesWinsOf(opponent);
        objective.getScore("§eScore: " + myCode + myWins + " §7- " + oppCode + oppWins).setScore(line--);

        // --- kit / mode / kills / kd / time ---
        objective.getScore("§fKit: §b" + session.kitName()
                + "  §fMode: §a" + modeLabel(session.mode())).setScore(line--);
        objective.getScore("§6Kills: §f" + session.killsOf(me)
                + "  §eK/D: §f" + String.format("%.2f", myStats.kd())).setScore(line--);
        if (session.startedAt() != null) {
            long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
            objective.getScore("§7Time: §f" + fmtTime(secs)).setScore(line--);
        }
        return line;
    }
}
