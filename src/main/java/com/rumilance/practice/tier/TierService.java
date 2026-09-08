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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto skill tiering in the classic tierlist order, from <b>real player-vs-player</b>
 * results — not bots. Ladder, strongest first:
 * {@code HT1 > LT1 > HT2 > LT2 > HT3 > LT3 > HT4 > LT4 > HT5 > LT5}.
 *
 * <h3>Model</h3>
 * <ul>
 *   <li><b>Tiers are per kit.</b> Every kit owns its own ladder: score = that kit's ranked
 *       ELO, population = players with enough matches in that kit. A monster sword player
 *       and a weak crystal record of the same person place independently.</li>
 *   <li>Placement gate per kit: at least {@link #MIN_MATCHES} ranked matches <em>in that
 *       kit</em> — fights come from whatever various opponents the queue provides, which is
 *       the anti-cheese property (no single partner can hand you a tier).</li>
 *   <li>Tiers are rarity bands over the kit's eligible population, so the ladder
 *       self-calibrates to the real player base: {@code HT1} is the top 0.1%
 *       ("1 in 1000"); {@code HT5} sits in the lower half shell — the rung of a casual with
 *       some PvP experience.</li>
 * </ul>
 *
 * <p>The snapshot refreshes from the DB on a timer (async) and is persisted to
 * {@code tiers.yml} as a warm-start cache — config.yml stays untouched.</p>
 */
public final class TierService {

    /** Tierlist order is the enum order: index 0 = strongest. */
    public enum Tier {
        HT1("HT1", org.bukkit.ChatColor.GOLD),
        LT1("LT1", org.bukkit.ChatColor.GOLD),
        HT2("HT2", org.bukkit.ChatColor.YELLOW),
        LT2("LT2", org.bukkit.ChatColor.YELLOW),
        HT3("HT3", org.bukkit.ChatColor.LIGHT_PURPLE),
        LT3("LT3", org.bukkit.ChatColor.LIGHT_PURPLE),
        HT4("HT4", org.bukkit.ChatColor.AQUA),
        LT4("LT4", org.bukkit.ChatColor.GREEN),
        HT5("HT5", org.bukkit.ChatColor.GRAY),
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
    /** Ranked matches within one kit required before a player places on that kit's ladder. */
    static final int MIN_MATCHES = 20;
    /**
     * Rarity bands in tierlist order: cumulative share of the kit's eligible population the
     * tier covers, from the strongest player down. HT1 is "1 in 1,000" (needs a population
     * that large to exist at all).
     */
    private static final Tier[] BAND_TIERS = {
            Tier.HT1, Tier.LT1, Tier.HT2, Tier.LT2, Tier.HT3,
            Tier.LT3, Tier.HT4, Tier.LT4, Tier.HT5, Tier.LT5
    };
    /** Cumulative band ceilings (top share) matching {@link #BAND_TIERS}. */
    static final double[] BAND_CEIL = {
            0.001, 0.003, 0.01, 0.03, 0.10,
            0.20, 0.35, 0.50, 0.70, 1.00
    };
    private static final long REFRESH_TICKS = 20L * 60L * 10L; // 10 minutes

    private final Plugin plugin;
    private final RankedStatsRepository rankedStatsRepository;
    private final AsyncExecutor asyncExecutor;
    /** uuid -> kit -> standing snapshot (swapped atomically after async recompute). */
    private volatile Map<UUID, Map<String, Standing>> standings = new ConcurrentHashMap<>();
    private volatile Map<String, Integer> kitPopulations = new LinkedHashMap<>();
    private volatile boolean refreshInFlight;

    public TierService(Plugin plugin, RankedStatsRepository rankedStatsRepository,
                       AsyncExecutor asyncExecutor) {
        this.plugin = plugin;
        this.rankedStatsRepository = rankedStatsRepository;
        this.asyncExecutor = asyncExecutor;
    }

    /** One player's standing on one kit's ladder in the latest snapshot. */
    public record Standing(Tier tier, String kit, int rank, int population, double percentile,
                           int elo, int matches) {
    }

    // ------------------------------------------------------------------ refresh

    /** Loads the warm-start cache and starts the periodic refresh task (call on enable). */
    public void start() {
        loadAll();
        refresh();
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
                REFRESH_TICKS, REFRESH_TICKS);
    }

    /** Recomputes every kit ladder from the ranked stats table (async DB, atomic swap). */
    public void refresh() {
        if (refreshInFlight) {
            return;
        }
        refreshInFlight = true;
        asyncExecutor.runAsync(() -> {
            try {
                List<RankedKitStats> rows = rankedStatsRepository.findAll();
                Computation computation = compute(rows);
                // No scheduler hop needed: volatile swap is thread-safe and placement reads
                // tolerate slightly stale snapshots by design.
                standings = computation.standings();
                kitPopulations = computation.kitPopulations();
                refreshInFlight = false;
            } catch (Exception e) {
                refreshInFlight = false;
                plugin.getLogger().warning("[Tier] refresh failed: " + e.getMessage());
            }
        });
    }

    /** Package-private so the computation can be unit-tested. */
    record Computation(Map<UUID, Map<String, Standing>> standings,
                       Map<String, Integer> kitPopulations) {
    }

    /** Pure per-kit ladder arithmetic (no Bukkit). See class javadoc. */
    static Computation compute(List<RankedKitStats> rows) {
        Map<String, List<RankedKitStats>> byKit = new HashMap<>();
        for (RankedKitStats row : rows) {
            byKit.computeIfAbsent(row.kit(), k -> new ArrayList<>(64)).add(row);
        }
        Map<UUID, Map<String, Standing>> out = new HashMap<>();
        Map<String, Integer> populations = new LinkedHashMap<>();
        for (Map.Entry<String, List<RankedKitStats>> kitRows : byKit.entrySet()) {
            String kit = kitRows.getKey();
            List<RankedKitStats> eligible = new ArrayList<>(kitRows.getValue().size());
            for (RankedKitStats row : kitRows.getValue()) {
                if (row.gamesPlayed() >= MIN_MATCHES && row.elo() > 0) {
                    eligible.add(row);
                }
            }
            eligible.sort(Comparator.comparingInt(RankedKitStats::elo).reversed());
            int population = eligible.size();
            populations.put(kit, population);
            int rank = 0;
            for (RankedKitStats row : eligible) {
                rank++;
                double percentile = rank / (double) population;
                Standing standing = new Standing(bandOf(percentile), kit, rank, population,
                        percentile, row.elo(), row.gamesPlayed());
                out.computeIfAbsent(row.uuid(), id -> new HashMap<>(4)).put(kit, standing);
            }
        }
        return new Computation(out, populations);
    }

    /** Maps a cumulative population share (0..1) to its tier band (tierlist order). */
    static Tier bandOf(double percentile) {
        for (int i = 0; i < BAND_TIERS.length; i++) {
            if (percentile <= BAND_CEIL[i] + 1e-9d) {
                return BAND_TIERS[i];
            }
        }
        return Tier.LT5;
    }

    // ------------------------------------------------------------------ queries

    /** All kit standings of a player (empty map when unplaced anywhere). */
    public Map<String, Standing> standingsOf(UUID playerId) {
        return standings.getOrDefault(playerId, new LinkedHashMap<>());
    }

    /** Eligible population per kit from the latest snapshot (kit -> player count). */
    public Map<String, Integer> kitPopulations() {
        return kitPopulations;
    }

    public int minMatches() {
        return MIN_MATCHES;
    }

    // ------------------------------------------------------------------ persistence (warm-start cache)

    public void loadAll() {
        File file = dataFile();
        Map<UUID, Map<String, Standing>> loaded = new ConcurrentHashMap<>();
        Map<String, Integer> populations = new LinkedHashMap<>();
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection kits = yaml.getConfigurationSection("kit-populations");
            if (kits != null) {
                for (String kit : kits.getKeys(false)) {
                    populations.put(kit, Math.max(0, kits.getInt(kit)));
                }
            }
            ConfigurationSection players = yaml.getConfigurationSection("players");
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    UUID id;
                    try {
                        id = UUID.fromString(key);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    ConfigurationSection playerKits = players.getConfigurationSection(key);
                    if (playerKits == null) {
                        continue;
                    }
                    Map<String, Standing> playerStandings = new HashMap<>(4);
                    for (String kit : playerKits.getKeys(false)) {
                        String base = key + "." + kit;
                        Tier tier;
                        try {
                            tier = Tier.valueOf(players.getString(base + ".tier", "UNRANKED"));
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        playerStandings.put(kit, new Standing(tier, kit,
                                players.getInt(base + ".rank"),
                                players.getInt(base + ".population"),
                                players.getDouble(base + ".percentile"),
                                players.getInt(base + ".elo"),
                                players.getInt(base + ".matches")));
                    }
                    if (!playerStandings.isEmpty()) {
                        loaded.put(id, playerStandings);
                    }
                }
            }
        }
        standings = loaded;
        kitPopulations = populations;
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        Map<String, Integer> populations = kitPopulations;
        for (Map.Entry<String, Integer> entry : populations.entrySet()) {
            yaml.set("kit-populations." + entry.getKey(), entry.getValue());
        }
        Map<UUID, Map<String, Standing>> snapshot = standings;
        for (Map.Entry<UUID, Map<String, Standing>> entry : snapshot.entrySet()) {
            for (Map.Entry<String, Standing> kitStanding : entry.getValue().entrySet()) {
                String base = "players." + entry.getKey() + "." + kitStanding.getKey();
                Standing s = kitStanding.getValue();
                yaml.set(base + ".tier", s.tier().name());
                yaml.set(base + ".rank", s.rank());
                yaml.set(base + ".population", s.population());
                yaml.set(base + ".percentile", s.percentile());
                yaml.set(base + ".elo", s.elo());
                yaml.set(base + ".matches", s.matches());
            }
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
