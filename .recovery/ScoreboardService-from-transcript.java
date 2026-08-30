package com.rumilance.practice.scoreboard;

import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.database.repository.WinStreakRepository;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.model.WinStreak;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.TickHealth;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central scoreboard updater. Layouts, colors, and copy come from {@code scoreboard.yml}
 * via {@link ScoreboardConfig} — not hardcoded strings.
 */
public final class ScoreboardService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String[] LINE_ENTRIES = new String[16];

    static {
        ChatColor[] colors = ChatColor.values();
        for (int i = 1; i <= 15; i++) {
            LINE_ENTRIES[i] = colors[i - 1].toString() + ChatColor.RESET;
        }
    }

    private record CachedStats(int bestElo, String bestTierLabel, int wins, int losses, int bestStreak, int matches, int kits) {
        static final CachedStats EMPTY = new CachedStats(0, "LT5", 0, 0, 0, 0, 0);

        double kd() {
            return (double) wins / Math.max(1, losses);
        }
    }

    private final Plugin plugin;
    private final PlayerStateManager stateManager;
    private final QueueService queueService;
    private final MatchRegistry matchRegistry;
    private final RankedStatsRepository rankedStatsRepository;
    private final WinStreakRepository winStreakRepository;
    private final SettingsService settingsService;
    private final AsyncExecutor asyncExecutor;
    private volatile ScoreboardConfig config;
    private volatile SpectatorService spectatorService;
    private volatile FfaService ffaService;
    private final ConcurrentMap<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> statsCacheAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, BoardHandle> boards = new ConcurrentHashMap<>();
    private volatile List<WinStreak> cachedBestStreaks = List.of();
    private volatile List<WinStreak> cachedMonthStreaks = List.of();
    private volatile long streakRankAt;
    private BukkitTask task;

    private static final class BoardHandle {
        final Scoreboard board;
        final Objective objective;
        final String[] lines = new String[16];
        String lastTabSig = "";

        BoardHandle(Scoreboard board, Objective objective) {
            this.board = board;
            this.objective = objective;
        }
    }

    public ScoreboardService(
            Plugin plugin,
            ScoreboardConfig config,
            PlayerStateManager stateManager,
            QueueService queueService,
            MatchRegistry matchRegistry,
            RankedStatsRepository rankedStatsRepository,
            WinStreakRepository winStreakRepository,
            SettingsService settingsService,
            AsyncExecutor asyncExecutor
    ) {
        this.plugin = plugin;
        this.config = Objects.requireNonNull(config, "config");
        this.stateManager = stateManager;
        this.queueService = queueService;
        this.matchRegistry = matchRegistry;
        this.rankedStatsRepository = rankedStatsRepository;
        this.winStreakRepository = winStreakRepository;
        this.settingsService = settingsService;
        this.asyncExecutor = asyncExecutor;
    }

    public void setSpectatorService(SpectatorService spectatorService) {
        this.spectatorService = spectatorService;
    }

    public void setFfaService(FfaService ffaService) {
        this.ffaService = ffaService;
    }

    /** Hot-swap {@code scoreboard.yml} without restarting the tick task. */
    public void reload(ScoreboardConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
        for (BoardHandle handle : boards.values()) {
            handle.lastTabSig = "";
            Arrays.fill(handle.lines, null);
        }
        stop();
        if (config.enabled()) {
            start();
        }
    }

    public ScoreboardConfig config() {
        return config;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        int interval = config.updateIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        statsCache.clear();
        statsCacheAt.clear();
        boards.clear();
    }

    private void tick() {
        if (TickHealth.lagging()) {
            return;
        }
        ScoreboardConfig cfg = this.config;
        if (!cfg.enabled()) {
            return;
        }
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        int onlineCount = online.size();
        refreshStreakRanks(cfg);
        boolean tab = cfg.tabHeaderFooter();
        for (Player player : online) {
            if (!settingsService.get(player).scoreboardEnabled()) {
                boards.remove(player.getUniqueId());
                if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            update(player, onlineCount, cfg);
            if (tab) {
                BoardHandle handle = boards.get(player.getUniqueId());
                if (handle != null) {
                    applyTab(player, handle, onlineCount, cfg);
                }
            }
        }
        if (boards.size() > onlineCount) {
            boards.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        }
    }

    private CachedStats cachedStats(UUID uuid, ScoreboardConfig cfg) {
        long now = System.currentTimeMillis();
        CachedStats cached = statsCache.get(uuid);
        Long updated = statsCacheAt.get(uuid);
        if (cached != null && updated != null && now - updated < cfg.statsCacheMs()) {
            return cached;
        }
        if (cached == null) {
            cached = CachedStats.EMPTY;
            statsCache.put(uuid, cached);
        }
        statsCacheAt.put(uuid, now);
        asyncExecutor.execute(() -> {
            CachedStats computed = loadStats(uuid);
            statsCache.put(uuid, computed);
            statsCacheAt.put(uuid, System.currentTimeMillis());
        });
        return cached;
    }

    private void refreshStreakRanks(ScoreboardConfig cfg) {
        long now = System.currentTimeMillis();
        if (now - streakRankAt < cfg.statsCacheMs()) {
            return;
        }
        streakRankAt = now;
        int bestMax = cfg.list("best_streak_top").max();
        int monthMax = cfg.list("month_streak_top").max();
        asyncExecutor.execute(() -> {
            try {
                cachedBestStreaks = List.copyOf(winStreakRepository.topBest(Math.max(1, bestMax)));
                cachedMonthStreaks = List.copyOf(winStreakRepository.topMonth(Math.max(1, monthMax)));
            } catch (Exception ignored) {
            }
        });
    }

    private void applyTab(Player player, BoardHandle handle, int onlineCount, ScoreboardConfig cfg) {
        UUID playerId = player.getUniqueId();
        Optional<MatchSession> match = matchRegistry.byPlayer(playerId);
        Optional<UUID> spectated = spectatorService == null
                ? Optional.empty()
                : spectatorService.matchOf(playerId);
        MatchSession session = match.orElseGet(() -> spectated.flatMap(matchRegistry::get).orElse(null));

        ScoreboardContext ctx = baseContext(player, onlineCount, cfg);
        Component header;
        Component footer;
        String sig;

        if (session != null && !session.isTeamMatch()) {
            UUID me = playerId;
            if (!session.participants().contains(me) && !session.participants().isEmpty()) {
                me = session.participants().get(0);
            }
            UUID foe = session.opponentOf(me);
            String left = StatsService.nameOf(me);
            String right = foe == null ? "-" : StatsService.nameOf(foe);
            String duelId = session.publicDuelId() == null ? "" : session.publicDuelId();
            String specs = spectatorNames(session.id(), cfg);
            ctx.put("player", left)
                    .put("opponent", right)
                    .put("duel_id", duelId)
                    .put("spectators", specs);

            ScoreboardConfig.TabLayout tab = cfg.tabMatch();
            List<String> headerLines = cfg.renderTabLines(tab.header(), ctx, tab.omitHeaderIfBlank());
            header = joinLines(headerLines);
            if (specs.isBlank() && tab.footerWhenNoSpectators() != null && !tab.footerWhenNoSpectators().isBlank()) {
                footer = LEGACY.deserialize(ScoreboardText.render(tab.footerWhenNoSpectators(), ctx.vars()));
            } else {
                footer = joinLines(cfg.renderTabLines(tab.footer(), ctx, List.of()));
            }
            sig = "match|" + left + "|" + right + "|" + duelId + "|" + specs;
        } else {
            ScoreboardConfig.TabLayout tab = cfg.tabLobby();
            header = joinLines(cfg.renderTabLines(tab.header(), ctx, tab.omitHeaderIfBlank()));
            footer = joinLines(cfg.renderTabLines(tab.footer(), ctx, List.of()));
            sig = "lobby|" + onlineCount;
        }

        if (sig.equals(handle.lastTabSig)) {
            return;
        }
        player.sendPlayerListHeaderAndFooter(header, footer);
        handle.lastTabSig = sig;
    }

    private static Component joinLines(List<String> legacyLines) {
        if (legacyLines == null || legacyLines.isEmpty()) {
            return Component.empty();
        }
        List<Component> parts = new ArrayList<>(legacyLines.size());
        for (String line : legacyLines) {
            parts.add(LEGACY.deserialize(line == null ? "" : line));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    private String spectatorNames(UUID matchId, ScoreboardConfig cfg) {
        if (spectatorService == null) {
            return "";
        }
        String join = ScoreboardText.render(cfg.spectatorsJoin(), Map.of(
                "server_name", cfg.serverName(),
                "server_ip", cfg.serverIp()
        ));
        StringBuilder names = new StringBuilder();
        for (UUID id : spectatorService.spectatorsOf(matchId)) {
            if (names.length() > 0) {
                names.append(join);
            }
            names.append(StatsService.nameOf(id));
        }
        return names.toString();
    }

    private CachedStats loadStats(UUID uuid) {
        try {
            List<com.rumilance.practice.model.RankedKitStats> kits =
                    rankedStatsRepository.findAllForPlayer(uuid);
            int bestElo = 0;
            String bestTier = "LT5";
            int wins = 0;
            int losses = 0;
            for (com.rumilance.practice.model.RankedKitStats stats : kits) {
                if (stats.elo() >= bestElo) {
                    bestElo = stats.elo();
                    bestTier = stats.tier().label();
                }
                wins += stats.wins();
                losses += stats.losses();
            }
            int bestStreak = 0;
            try {
                bestStreak = winStreakRepository.find(uuid).map(WinStreak::bestStreak).orElse(0);
            } catch (Exception ignored) {
            }
            return new CachedStats(bestElo, bestTier, wins, losses, bestStreak, wins + losses, kits.size());
        } catch (Exception e) {
            return CachedStats.EMPTY;
        }
    }

    private ScoreboardContext baseContext(Player player, int onlineCount, ScoreboardConfig cfg) {
        return new ScoreboardContext()
                .put("server_name", cfg.serverName())
                .put("server_ip", cfg.serverIp())
                .put("online", onlineCount)
                .put("player", player.getName())
                .put("ping", player.getPing());
    }

    private String modeLabel(MatchMode mode, ScoreboardConfig cfg) {
        return switch (mode) {
            case RANKED -> cfg.modeLabel("ranked");
            case UNRANKED -> cfg.modeLabel("unranked");
            case FFA -> cfg.modeLabel("ffa");
            case TEAM -> cfg.modeLabel("team");
        };
    }

    private String teamColorTag(TeamColor color, ScoreboardConfig cfg) {
        return color == TeamColor.RED ? cfg.colorRed() : cfg.colorBlue();
    }

    private void update(Player player, int onlineCount, ScoreboardConfig cfg) {
        UUID playerId = player.getUniqueId();
        PlayerState state = stateManager.getState(playerId);
        Optional<MatchSession> match = matchRegistry.byPlayer(playerId);
        Optional<UUID> spectated = spectatorService == null
                ? Optional.empty()
                : spectatorService.matchOf(playerId);
        Optional<String> ffaWatch = spectatorService == null
                ? Optional.empty()
                : spectatorService.ffaArenaOf(playerId);

        ScoreboardConfig.Layout layout;
        ScoreboardContext ctx = baseContext(player, onlineCount, cfg);

        if (ffaWatch.isPresent()) {
            layout = cfg.layout("ffa_spectate");
            fillFfa(ctx, player, ffaWatch.get(), cfg);
        } else if (spectated.isPresent()) {
            MatchSession watched = matchRegistry.get(spectated.get()).orElse(null);
            if (watched != null) {
                layout = cfg.layout("spectate");
                fillSpectate(ctx, watched, cfg);
            } else {
                layout = cfg.layout("lobby");
                fillLobby(ctx, player, cfg);
            }
        } else if (match.isPresent()) {
            MatchSession session = match.get();
            if (session.isTeamMatch()) {
                layout = cfg.layout("team_match");
                fillTeamMatch(ctx, player, session, cfg);
            } else {
                layout = cfg.layout("match");
                fillMatch(ctx, player, session, cfg);
            }
        } else if (state == PlayerState.QUEUED_RANKED || state == PlayerState.QUEUED_UNRANKED) {
            layout = cfg.layout("queue");
            fillQueue(ctx, player, cfg);
        } else if (state == PlayerState.FFA) {
            layout = cfg.layout("ffa");
            String arenaId = ffaService == null ? null : ffaService.arenaOf(playerId).orElse(null);
            fillFfa(ctx, player, arenaId, cfg);
        } else {
            layout = cfg.layout("lobby");
            fillLobby(ctx, player, cfg);
        }

        List<String> expanded = cfg.expandLines(layout, ctx);
        String[] byScore = toScoreSlots(expanded, cfg.maxLines());
        Component title = LEGACY.deserialize(cfg.renderTitle(layout, ctx));
        apply(player, byScore, title);

        MatchSession visualSession = match.orElseGet(() -> spectated.flatMap(matchRegistry::get).orElse(null));
        BoardHandle handle = boards.get(player.getUniqueId());
        if (handle != null && visualSession != null) {
            com.rumilance.practice.match.MatchTeamVisuals.apply(handle.board, visualSession, Bukkit.getOnlinePlayers());
        }
    }

    private void fillLobby(ScoreboardContext ctx, Player player, ScoreboardConfig cfg) {
        CachedStats stats = cachedStats(player.getUniqueId(), cfg);
        ctx.put("wins", stats.wins())
                .put("losses", stats.losses())
                .put("kd", String.format(Locale.ROOT, "%.2f", stats.kd()))
                .put("best_elo", stats.bestElo())
                .put("best_tier", stats.bestTierLabel())
                .put("best_streak", stats.bestStreak())
                .put("matches", stats.matches())
                .put("kits", stats.kits());
        registerBestStreakDirective(ctx);
    }

    private void fillQueue(ScoreboardContext ctx, Player player, ScoreboardConfig cfg) {
        var entry = queueService.get(player.getUniqueId());
        if (entry.isPresent()) {
            long waited = Math.max(0, Instant.now().getEpochSecond()
                    - entry.get().joinedAt().getEpochSecond());
            ctx.put("kit", com.rumilance.practice.util.KitNames.pretty(entry.get().kitId()))
                    .put("wait", cfg.formatTime(waited))
                    .put("mode", modeLabel(entry.get().mode(), cfg));
        } else {
            ctx.put("kit", "").put("wait", "").put("mode", "");
        }
        registerMonthStreakDirective(ctx);
        registerBestStreakDirective(ctx);
    }

    private void fillMatch(ScoreboardContext ctx, Player player, MatchSession session, ScoreboardConfig cfg) {
        UUID me = player.getUniqueId();
        TeamColor myColor = session.teamColor(me);
        UUID opponent = session.opponentOf(me);
        ctx.put("my_color", teamColorTag(myColor, cfg))
                .put("opp_color", teamColorTag(myColor.opposite(), cfg))
                .put("opponent", opponent == null ? "" : StatsService.nameOf(opponent))
                .put("my_series", session.seriesWinsOf(me))
                .put("opp_series", opponent == null ? 0 : session.seriesWinsOf(opponent))
                .put("kit", com.rumilance.practice.util.KitNames.pretty(session.kitName()))
                .put("mode", modeLabel(session.mode(), cfg))
                .put("duel_id", session.publicDuelId() == null ? "" : session.publicDuelId());
        if (session.startedAt() != null) {
            long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
            ctx.put("time", cfg.formatTime(secs));
        } else {
            ctx.put("time", "");
        }
    }

    private void fillTeamMatch(ScoreboardContext ctx, Player player, MatchSession session, ScoreboardConfig cfg) {
        UUID me = player.getUniqueId();
        TeamColor myColor = session.teamColor(me);
        TeamColor enemy = myColor.opposite();
        List<UUID> mySide = session.team(myColor);
        List<UUID> enemySide = session.team(enemy);
        ctx.put("my_color", teamColorTag(myColor, cfg))
                .put("opp_color", teamColorTag(enemy, cfg))
                .put("ally_alive", countAlive(mySide))
                .put("ally_size", mySide.size())
                .put("enemy_alive", countAlive(enemySide))
                .put("enemy_size", enemySide.size())
                .put("kit", com.rumilance.practice.util.KitNames.pretty(session.kitName()))
                .put("mode", modeLabel(session.mode(), cfg))
                .put("my_kills", session.killsOf(me));
        if (session.startedAt() != null) {
            long secs = Instant.now().getEpochSecond() - session.startedAt().getEpochSecond();
            ctx.put("time", cfg.formatTime(secs));
        } else {
            ctx.put("time", "");
        }
    }

    private void fillSpectate(ScoreboardContext ctx, MatchSession session, ScoreboardConfig cfg) {
        List<UUID> red = session.team(TeamColor.RED);
        List<UUID> blue = session.team(TeamColor.BLUE);
        int redWins = red.isEmpty() ? 0 : session.seriesWinsOf(red.get(0));
        int blueWins = blue.isEmpty() ? 0 : session.seriesWinsOf(blue.get(0));
        ctx.put("red_series", redWins)
                .put("blue_series", blueWins)
                .put("kit", com.rumilance.practice.util.KitNames.pretty(session.kitName()))
                .put("mode", modeLabel(session.mode(), cfg))
                .put("duel_id", session.publicDuelId() == null ? "" : session.publicDuelId());
        ctx.directive("spec_red", c -> ScoreboardContext.renderSpecList(
                c.list("spec_red"),
                toSpecEntries(red),
                ctx.vars()));
        ctx.directive("spec_blue", c -> ScoreboardContext.renderSpecList(
                c.list("spec_blue"),
                toSpecEntries(blue),
                ctx.vars()));
    }

    private void fillFfa(ScoreboardContext ctx, Player player, String arenaId, ScoreboardConfig cfg) {
        FfaService.FfaStats stats = ffaService == null
                ? new FfaService.FfaStats(0, 0)
                : ffaService.stats(player.getUniqueId());
        int streak = ffaService == null ? 0 : ffaService.killStreak(player.getUniqueId());
        String arenaLabel = arenaId == null || arenaId.isBlank() ? "FFA" : arenaId;
        ctx.put("arena", arenaLabel)
                .put("ffa_kills", stats.kills())
                .put("ffa_deaths", stats.deaths())
                .put("ffa_streak", streak);
        final String arenaKey = arenaId;
        ctx.directive("ffa_streak_top", c -> {
            List<FfaService.StreakRank> top = ffaService == null
                    ? List.of()
                    : ffaService.topKillStreaks(arenaKey, c.list("ffa_streak_top").max());
            List<ScoreboardContext.StreakEntry> entries = new ArrayList<>(top.size());
            for (FfaService.StreakRank row : top) {
                entries.add(new ScoreboardContext.StreakEntry(StatsService.nameOf(row.playerId()), row.streak()));
            }
            return ScoreboardContext.renderStreakList(c.list("ffa_streak_top"), entries, ctx.vars());
        });
    }

    private void registerMonthStreakDirective(ScoreboardContext ctx) {
        ctx.directive("month_streak_top", c -> {
            List<WinStreak> top = cachedMonthStreaks;
            List<ScoreboardContext.StreakEntry> entries = new ArrayList<>();
            int max = c.list("month_streak_top").max();
            for (WinStreak row : top) {
                if (entries.size() >= max) {
                    break;
                }
                entries.add(new ScoreboardContext.StreakEntry(displayName(row), row.monthBest()));
            }
            return ScoreboardContext.renderStreakList(c.list("month_streak_top"), entries, ctx.vars());
        });
    }

    private void registerBestStreakDirective(ScoreboardContext ctx) {
        ctx.directive("best_streak_top", c -> {
            List<WinStreak> top = cachedBestStreaks;
            List<ScoreboardContext.StreakEntry> entries = new ArrayList<>();
            int max = c.list("best_streak_top").max();
            for (WinStreak row : top) {
                if (entries.size() >= max) {
                    break;
                }
                entries.add(new ScoreboardContext.StreakEntry(displayName(row), row.bestStreak()));
            }
            return ScoreboardContext.renderStreakList(c.list("best_streak_top"), entries, ctx.vars());
        });
    }

    private static List<ScoreboardContext.SpecEntry> toSpecEntries(List<UUID> side) {
        List<ScoreboardContext.SpecEntry> out = new ArrayList<>(side.size());
        for (UUID id : side) {
            out.add(new ScoreboardContext.SpecEntry(StatsService.nameOf(id), heartsOf(id), totemsOf(id)));
        }
        return out;
    }

    private static String displayName(WinStreak row) {
        if (row.username() != null && !row.username().isBlank()) {
            return row.username();
        }
        return StatsService.nameOf(row.playerId());
    }

    /** Packs expanded lines into score slots 15→1 (top to bottom). */
    private static String[] toScoreSlots(List<String> lines, int maxLines) {
        String[] byScore = new String[16];
        int limit = Math.min(maxLines, 15);
        int count = Math.min(lines.size(), limit);
        int score = 15;
        for (int i = 0; i < count && score >= 1; i++) {
            byScore[score] = lines.get(i);
            score--;
        }
        return byScore;
    }

    private void apply(Player player, String[] next, Component title) {
        UUID id = player.getUniqueId();
        BoardHandle handle = boards.get(id);
        if (handle != null && Arrays.equals(handle.lines, next)) {
            handle.objective.displayName(title);
            if (player.getScoreboard() != handle.board) {
                player.setScoreboard(handle.board);
            }
            return;
        }
        if (handle == null) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = board.registerNewObjective("rp", Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int score = 1; score <= 15; score++) {
                ensureLineTeam(board, score);
            }
            handle = new BoardHandle(board, objective);
            boards.put(id, handle);
        } else {
            handle.objective.displayName(title);
        }
        for (int score = 1; score <= 15; score++) {
            String old = handle.lines[score];
            String neu = next[score];
            if (Objects.equals(old, neu)) {
                continue;
            }
            if (old != null && !old.equals(LINE_ENTRIES[score])) {
                handle.board.resetScores(old);
            }
            Team team = ensureLineTeam(handle.board, score);
            String entry = LINE_ENTRIES[score];
            if (neu == null) {
                handle.board.resetScores(entry);
                team.prefix(Component.empty());
            } else {
                team.prefix(LEGACY.deserialize(neu));
                handle.objective.getScore(entry).setScore(score);
            }
            handle.lines[score] = neu;
        }
        if (player.getScoreboard() != handle.board) {
            player.setScoreboard(handle.board);
        }
    }

    private static Team ensureLineTeam(Scoreboard board, int score) {
        String name = "rps" + score;
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.addEntry(LINE_ENTRIES[score]);
        } else if (!team.hasEntry(LINE_ENTRIES[score])) {
            team.addEntry(LINE_ENTRIES[score]);
        }
        return team;
    }

    private static String heartsOf(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return "-/-";
        }
        double maxHp = 20.0d;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            maxHp = attr.getValue();
        }
        return formatHearts(player.getHealth() / 2.0d) + "/" + formatHearts(maxHp / 2.0d);
    }

    private static int totemsOf(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static String formatHearts(double hearts) {
        if (Math.abs(hearts - Math.rint(hearts)) < 0.05d) {
            return String.valueOf((int) Math.rint(hearts));
        }
        return String.format(Locale.ROOT, "%.1f", hearts);
    }

    private static int countAlive(List<UUID> members) {
        int alive = 0;
        for (UUID member : members) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.getGameMode() != GameMode.SPECTATOR) {
                alive++;
            }
        }
        return alive;
    }
}
