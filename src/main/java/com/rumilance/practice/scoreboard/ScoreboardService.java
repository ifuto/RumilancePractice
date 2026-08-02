package com.rumilance.practice.scoreboard;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Central scoreboard updater (10-20 tick interval), not per-player timers.
 */
public final class ScoreboardService {

    private final Plugin plugin;
    private final PluginSettings settings;
    private final SessionManager sessionManager;
    private final PlayerStateManager stateManager;
    private final QueueService queueService;
    private final MatchRegistry matchRegistry;
    private final RankedStatsRepository rankedStatsRepository;
    private final SettingsService settingsService;
    private BukkitTask task;

    public ScoreboardService(
            Plugin plugin,
            PluginSettings settings,
            SessionManager sessionManager,
            PlayerStateManager stateManager,
            QueueService queueService,
            MatchRegistry matchRegistry,
            RankedStatsRepository rankedStatsRepository,
            SettingsService settingsService
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.sessionManager = sessionManager;
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
                        Component.text(settings.scoreboardServerName(), NamedTextColor.WHITE),
                        Component.text(settings.scoreboardServerIp() + "  |  Online: "
                                + Bukkit.getOnlinePlayers().size(), NamedTextColor.GRAY)
                );
            }
        }
    }

    private void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("rp", Criteria.DUMMY,
                Component.text(settings.scoreboardServerName(), NamedTextColor.WHITE));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerState state = stateManager.getState(player.getUniqueId());
        int line = 15;
        // Minimal layout: server branding + player counts, then match context only.
        objective.getScore("§f" + settings.scoreboardServerName()).setScore(line--);
        objective.getScore("§7" + settings.scoreboardServerIp()).setScore(line--);
        objective.getScore("§7Online: §f" + Bukkit.getOnlinePlayers().size()).setScore(line--);
        objective.getScore("§7Queue: §f" + queueService.totalWaiting()).setScore(line--);
        objective.getScore("§r").setScore(line--);

        Optional<MatchSession> match = matchRegistry.byPlayer(player.getUniqueId());
        if (match.isPresent()) {
            MatchSession session = match.get();
            // Team banner: "- YOU -" / "- OPPONENT -" with each name in its team color.
            // Colors are stable for the whole rematch chain (participant order never changes).
            UUID me = player.getUniqueId();
            if (session.isParticipant(me) && session.participants().size() >= 2) {
                UUID opponent = session.opponentOf(me);
                TeamColor myColor = session.teamColor(me);
                String myCode = myColor == TeamColor.RED ? "§c" : "§9";
                String oppCode = myColor == TeamColor.RED ? "§9" : "§c";
                objective.getScore("§7- YOU -").setScore(line--);
                objective.getScore(myCode + player.getName()).setScore(line--);
                if (opponent != null) {
                    objective.getScore("§7- OPPONENT -").setScore(line--);
                    objective.getScore(oppCode + StatsService.nameOf(opponent)).setScore(line--);
                }
            }
            objective.getScore("§fKit: §7" + session.kitName()).setScore(line--);
            objective.getScore("§fMode: §7" + session.mode()).setScore(line--);
            // Rematch-chain score: own wins (own color) - opponent wins (opponent color).
            // Fresh matches show 0-0; only rematch-confirmed matches carry the score over.
            if (session.isParticipant(me)) {
                TeamColor myColor = session.teamColor(me);
                String myCode = myColor == TeamColor.RED ? "§c" : "§9";
                String oppCode = myColor == TeamColor.RED ? "§9" : "§c";
                int myWins = session.seriesWinsOf(me);
                int oppWins = session.opponentOf(me) == null ? 0 : session.seriesWinsOf(session.opponentOf(me));
                objective.getScore(myCode + myWins + " §7- " + oppCode + oppWins).setScore(line--);
            }
            if (session.startedAt() != null) {
                long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
                objective.getScore("§7Time: §f" + secs + "s").setScore(line--);
            }
        } else if (state == PlayerState.QUEUED_RANKED || state == PlayerState.QUEUED_UNRANKED) {
            queueService.get(player.getUniqueId()).ifPresent(entry ->
                    objective.getScore("§fQueued: §7" + entry.kitId()).setScore(10));
        }

        player.setScoreboard(board);
    }
}
