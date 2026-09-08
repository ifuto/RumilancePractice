package com.rumilance.practice.tier;

import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.util.AsyncExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto skill tiering ({@code HT1} … {@code HT5}, {@code LT1} … {@code LT5}) computed from
 * <b>real player-vs-player</b> results — not bots. The server's ranked ELO tables are the
 * combat record: every ranked duel is a sample of two players' moves, aim, trading and
 * builds, distilled into ELO by actual humans fighting each other.
 *
 * <h3>Model</h3>
 * <ul>
 *   <li>Per player: composite score = best-kit ELO (top kit only, so one strong kit is a
 *       genuine signal and one weak kit never dilutes it).</li>
 *   <li>Placement gate: at least {@link #MIN_MATCHES} ranked matches across all kits —
 *       fights are against whatever various players the queue provides, which is the
 *       anti-cheese property (no single opponent can hand you a tier).</li>
 *   <li>Tiers are rarity bands over the eligible population, so the ladder self-calibrates
 *       to the real player base: {@code HT1} is the top 0.1% ("1 in 1000"), {@code HT5} is
 *       the top-10% shell of regulars — the "beginner who already plays some PvP" rung.</li>
 * </ul>
 *
 * <p>The snapshot refreshes from the DB on a timer (async) and is persisted to
 * {@code tiers.yml} as a warm-start cache — config.yml stays untouched.</p>
 */
public final class TierService {

    public enum Tier {
        HT1("HT1", org.bukkit.ChatColor.GOLD),
        HT2("HT2", org.bukkit.ChatColor.GOLD),
        HT3("HT3", org.bukkit.ChatColor.YELLOW),
        HT4("HT4", org.bukkit.ChatColor.YELLOW),
        HT5("HT5", org.bukkit.ChatColor.LIGHT_PURPLE),
        LT1("LT1", org.bukkit.ChatColor.LIGHT_PURPLE),
        LT2("LT2", org.bukkit.ChatColor.AQUA),
        LT3("LT3", org.bukkit.ChatColor.GREEN),
        LT4("LT4", org.bukkit.ChatColor.GRAY),
        LT5("LT5", org.bukkit.ChatColor.DARK_GRAY),
        UNRANKED("\u2014", org.bukkit.ChatColor.DARK_GRAY);

        private final String label;
        private final org.bukkit.ChatColor color;

        Tier(String label, org.bukkit.ChatColor color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public org.bukkit.ChatColor color() {
            return color;
        }
    }

    private static final String FILE = "tiers.yml";
    /** Ranked matches (all kits summed) required before a player is placed at all. */
    static final int MIN_MATCHES = 20;
    /**
     * Rarity bands: cumulative share of the eligible population the tier covers, from the
     * strongest player down. HT1 is "1 in 1,000" (needs a population that large to exist at
     * all); HT5 is the top-10% shell — the rung of a beginner who already plays some PvP.
     */
    private static final Tier[] BAND_TIERS = {
            Tier.HT1, Tier.HT2, Tier.HT3, Tier.HT4, Tier.HT5,
            Tier.LT1, Tier.LT2, Tier.LT3, Tier.LT4, Tier.LT5
    };
    private static final double[] BAND_CEIL = {
            0.001, 0.003, 0.01, 0.03, 0.10,
            0.20, 0.35, 0.50, 0.70, 1.00
    };
    private static final long REFRESH_TICKS = 20L * 60L * 10L; // 10 minutes

    private final Plugin plugin;
    private final RankedStatsRepository rankedStatsRepository;
    private final AsyncExecutor asyncExecutor;
    /** uuid -> standing snapshot (async-computed, main-thread + async safe reads). */
    private final Map<UUID, Standing> standings = new ConcurrentHashMap<>();
    private volatile int eligibleCount;
    private volatile boolean refreshInFlight;

    public TierService(Plugin plugin, RankedStatsRepository rankedStatsRepository,
                       AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.rankedStatsRepository = rankedStatsRepository;
        this.asyncExecutor = asyncExecutor;
    }

    /** One player's placed standing in the latest snapshot. */
    public record Standing(Tier tier, int rank, int population, double percentile,
                           int bestElo, String topKit, int matches) {
    }

    // ------------------------------------------------------------------ refresh

    /** Starts the periodic refresh task (call on enable). */
    public void start() {
        loadAll();
        refresh();
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
                REFRESH_TICKS, REFRESH_TICKS);
    }

    /** Recomputes the whole ladder from the ranked stats table (async DB, main-thread swap). */
    public void refresh() {
        if (refreshInFlight) {
            return;
        }
        refreshInFlight = true;
        asyncExecutor.runAsync(() -> {
            try {
                List<RankedKitStats> rows = rankedStatsRepository.findAll();
                Computation computation = compute(rows);
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    standings.clear();
                    standings.putAll(computation.standings());
                    eligibleCount = computation.eligibleCount();
                    refreshInFlight = false;
                });
            } catch (Exception e) {
                refreshInFlight = false;
                plugin.getLogger().warning("[Tier] refresh failed: " + e.getMessage());
            }
        });
    }

    /** Package-private so the computation can be unit-tested. */
    record Computation(Map<UUID, Standing> standings, int eligibleCount) {
    }

    /** Pure ladder arithmetic, unit-test friendly (no Bukkit). See class javadoc. */
    static Computation compute(List<RankedKitStats> rows) {
        Map<UUID, List<RankedKitStats>> byPlayer = new HashMap<>();
        for (RankedKitStats row : rows) {
            byPlayer.computeIfAbsent(row.uuid(), id -> new ArrayList<>(4)).add(row);
        }
        record Raw(int score, int matches, String topKit) {
        }
        Map<UUID, Raw> raw = new HashMap<>();
        for (Map.Entry<UUID, List<RankedKitStats>> entry : byPlayer.entrySet()) {
            int best = 0;
            int matches = 0;
            String topKit = "";
            for (RankedKitStats row : entry.getValue()) {
                matches += row.wins() + row.losses();
                if (row.elo() > best) {
                    best = row.elo();
                    topKit = row.kit();
                }
            }
            if (matches >= MIN_MATCHES && best > 0) {
                raw.put(entry.getKey(), new Raw(best, matches, topKit));
            }
        }
        List<Map.Entry<UUID, Raw>> order = new ArrayList<>(raw.entrySet());
        order.sort(Map.Entry.<UUID, Raw>comparingByValue(
                Comparator.comparingInt(Raw::score).reversed()));
        int population = order.size();
        Map<UUID, Standing> out = new HashMap<>(population * 2);
        int rank = 0;
        for (Map.Entry<UUID, Raw> entry : order) {
            rank++;
            double percentile = rank / (double) population;
            Tier tier = bandOf(percentile);
            out.put(entry.getKey(), new Standing(tier, rank, population, percentile,
                    entry.getValue().score(), entry.getValue().topKit(), entry.getValue().matches()));
        }
        return new Computation(out, population);
    }

    /** Maps a cumulative population share (0..1) to its tier band. */
    static Tier bandOf(double percentile) {
        for (int i = 0; i < BAND_TIERS.length; i++) {
            if (percentile <= BAND_CEIL[i] + 1e-9d) {
                return BAND_TIERS[i];
            }
        }
        return Tier.LT5;
    }

    // ------------------------------------------------------------------ queries

    public Tier tierOf(UUID playerId) {
        Standing s = standings.get(playerId);
        return s != null ? s.tier() : Tier.UNRANKED;
    }

    public Optional<Standing> standingOf(UUID playerId) {
        return Optional.ofNullable(standings.get(playerId));
    }

    public int eligibleCount() {
        return eligibleCount;
    }

    public int minMatches() {
        return MIN_MATCHES;
    }

    // ------------------------------------------------------------------ persistence (warm-start cache)

    public void loadAll() {
        standings.clear();
        eligibleCount = 0;
        File file = dataFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        eligibleCount = Math.max(0, yaml.getInt("eligible-count"));
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Tier tier;
            try {
                tier = Tier.valueOf(players.getString(key + ".tier", "UNRANKED"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            standings.put(id, new Standing(tier,
                    players.getInt(key + ".rank"),
                    players.getInt(key + ".population"),
                    players.getDouble(key + ".percentile"),
                    players.getInt(key + ".best-elo"),
                    players.getString(key + ".top-kit", ""),
                    players.getInt(key + ".matches")));
        }
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("eligible-count", eligibleCount);
        for (Map.Entry<UUID, Standing> entry : standings.entrySet()) {
            String base = "players." + entry.getKey();
            Standing s = entry.getValue();
            yaml.set(base + ".tier", s.tier().name());
            yaml.set(base + ".rank", s.rank());
            yaml.set(base + ".population", s.population());
            yaml.set(base + ".percentile", s.percentile());
            yaml.set(base + ".best-elo", s.bestElo());
            yaml.set(base + ".top-kit", s.topKit());
            yaml.set(base + ".matches", s.matches());
        }
        File file = dataFile();
        file.getParentFile().mkdirs();
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Tier] Failed to save tiers.yml: " + e.getMessage());
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), FILE);
    }
}
