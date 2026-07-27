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
                        Component.text("Rumilance Practice", NamedTextColor.LIGHT_PURPLE),
                        Component.text("Online: " + Bukkit.getOnlinePlayers().size(), NamedTextColor.GRAY)
                );
            }
        }
    }

    private void update(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("rp", Criteria.DUMMY,
                Component.text("Practice", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        PlayerState state = stateManager.getState(player.getUniqueId());
        int line = 15;
        objective.getScore("§7Server").setScore(line--);
        objective.getScore("§fOnline: " + Bukkit.getOnlinePlayers().size()).setScore(line--);
        objective.getScore("§fQueue: " + queueService.totalWaiting()).setScore(line--);
        objective.getScore("§fMatches: " + matchRegistry.activeCount()).setScore(line--);
        objective.getScore("§r").setScore(line--);

        Optional<MatchSession> match = matchRegistry.byPlayer(player.getUniqueId());
        if (match.isPresent()) {
            MatchSession session = match.get();
            objective.getScore("§eKit: " + session.kitName()).setScore(line--);
            objective.getScore("§eMode: " + session.mode()).setScore(line--);
            if (session.startedAt() != null) {
                long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
                objective.getScore("§eTime: " + secs + "s").setScore(line--);
            }
        } else if (state == PlayerState.QUEUED_RANKED || state == PlayerState.QUEUED_UNRANKED) {
            queueService.get(player.getUniqueId()).ifPresent(entry ->
                    objective.getScore("§bQueued: " + entry.kitId()).setScore(10));
        } else {
            objective.getScore("§aLobby").setScore(line);
        }

        player.setScoreboard(board);
    }
}
