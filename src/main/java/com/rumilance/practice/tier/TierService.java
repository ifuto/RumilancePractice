package com.rumilance.practice.tier;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.rumilance.practice.practice.BotDifficulty.Preset;

/**
 * Auto skill tiering ({@code HT1} … {@code HT5}, {@code LT1} … {@code LT5}) evaluated from
 * practice fights against the seven-step bot ladder — the Quantum-style way: a tier says
 * "this player comfortably beats bots up to rung X".
 *
 * <h3>Signals & anti-fraud</h3>
 * <ul>
 *   <li>Every bot pop the player lands = one WIN at the current difficulty rung; every practice
 *       death = one LOSS there. Stored per rung.</li>
 *   <li>A rung counts toward a tier only with {@link #MIN_SAMPLES} samples and ≥60% win rate —
 *       one lucky kill never ranks you up.</li>
 *   <li>The HT bracket demands stricter thresholds and bigger samples (10 / 15 matches at the
 *       top), so killaura-ish bursts would still need many rounds <em>and</em> the losses a
 *       human collects falling for successive rungs; automated samples can additionally be
 *       invalidated later without schema changes.</li>
 *   <li>Records persist in {@code tiers.yml} (data folder), loaded on enable, saved on
 *       disable — no ConfigService coupling, so config.yml stays untouched.</li>
 * </ul>
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
        UNRANKED("—", org.bukkit.ChatColor.DARK_GRAY);

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
    /** Minimum fights at one rung before its win rate counts. */
    static final int MIN_SAMPLES = 5;
    /** HT-tier sample gates, per level (HT5..HT1 != need bigger samples toward the top). */
    private static final int[] HT_MIN_SAMPLES = {MIN_SAMPLES, 8, 10, 12, 15};
    /** Ordered ladder for evaluation: index 0 = weakest. */
    private static final Preset[] LADDER_ASC = {
            Preset.NPC, Preset.EASY, Preset.INTERMEDIATE, Preset.HARD,
            Preset.CRAZY, Preset.MASTER, Preset.SURVIVAL_MASTER
    };
    /** Bot pop above this win rate marks the rung "comfortably beaten". */
    static final double BASE_WIN_RATE = 0.60d;

    private final Plugin plugin;
    /** player id -> rung -> {wins, losses}. */
    private final Map<UUID, EnumMap<Preset, int[]>> stats = new ConcurrentHashMap<>();

    public TierService(Plugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ recording

    /** A bot pop the player earned (bot stagger, crystal pop, mace knock-out). */
    public void recordPop(UUID playerId, Preset rung) {
        record(playerId, rung, true);
    }

    /** A full defeat in the practice room (player death). */
    public void recordDeath(UUID playerId, Preset rung) {
        record(playerId, rung, false);
    }

    private void record(UUID playerId, Preset rung, boolean win) {
        if (playerId == null || rung == null || rung == Preset.CUSTOM) {
            return;
        }
        EnumMap<Preset, int[]> byRung = stats.computeIfAbsent(playerId, id -> new EnumMap<>(Preset.class));
        synchronized (byRung) {
            int[] entry = byRung.computeIfAbsent(rung, r -> new int[2]);
            entry[win ? 0 : 1]++;
        }
    }

    // ------------------------------------------------------------------ evaluation

    /** Current tier of the player from their per-rung practice record. */
    public Tier tierOf(UUID playerId) {
        EnumMap<Preset, int[]> byRung = stats.get(playerId);
        if (byRung == null) {
            return Tier.UNRANKED;
        }
        synchronized (byRung) {
            Tier tier = Tier.UNRANKED;
            for (int rung = 0; rung < LADDER_ASC.length; rung++) {
                Tier candidate = tierForRung(rung, byRung.get(LADDER_ASC[rung]));
                if (candidate == Tier.UNRANKED) {
                    continue;
                }
                if (candidate.ordinal() < tier.ordinal() || tier == Tier.UNRANKED) {
                    // Lower ordinal = stronger tier (HT1=0), so keep the strongest cleared rung.
                    tier = candidate;
                }
            }
            return tier;
        }
    }

    /** The one tier a rung record supports (UNRANKED when samples/thresholds miss). */
    private Tier tierForRung(int rungIndex, int[] entry) {
        if (entry == null) {
            return Tier.UNRANKED;
        }
        int wins = entry[0];
        int losses = entry[1];
        int samples = wins + losses;
        if (samples < MIN_SAMPLES) {
            return Tier.UNRANKED;
        }
        double rate = wins / (double) samples;
        return switch (rungIndex) {
            case 0 -> rate >= BASE_WIN_RATE ? Tier.LT5 : Tier.UNRANKED;
            case 1 -> rate >= BASE_WIN_RATE ? Tier.LT4 : Tier.UNRANKED;
            case 2 -> rate >= BASE_WIN_RATE ? Tier.LT3 : Tier.UNRANKED;
            case 3 -> rate >= BASE_WIN_RATE ? Tier.LT2 : Tier.UNRANKED;
            case 4 -> rate >= BASE_WIN_RATE ? Tier.LT1 : Tier.UNRANKED;
            case 5 -> // MASTER
                    tierByBands(rate, samples,
                            new double[]{0.65, 0.80}, new int[]{HT_MIN_SAMPLES[0], HT_MIN_SAMPLES[1]},
                            new Tier[]{Tier.HT5, Tier.HT4});
            default -> // SURVIVAL_MASTER
                    tierByBands(rate, samples,
                            new double[]{0.65, 0.80, 0.92}, new int[]{HT_MIN_SAMPLES[2], HT_MIN_SAMPLES[3], HT_MIN_SAMPLES[4]},
                            new Tier[]{Tier.HT3, Tier.HT2, Tier.HT1});
        };
    }

    /** Picks the strictest band the record qualifies for (highest winrate band & sample gate). */
    private static Tier tierByBands(double rate, int samples, double[] rates, int[] mins, Tier[] tiers) {
        for (int i = rates.length - 1; i >= 0; i--) {
            if (rate >= rates[i] && samples >= mins[i]) {
                return tiers[i];
            }
        }
        return Tier.UNRANKED;
    }

    /** Per-rung readout for {@code /tier}'s detailed view: samples({win}/{loss}), win rate. */
    public String progressLine(UUID playerId) {
        EnumMap<Preset, int[]> byRung = stats.get(playerId);
        StringBuilder out = new StringBuilder();
        for (Preset rung : LADDER_ASC) {
            int[] e = byRung == null ? null : byRung.get(rung);
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(rung.name());
            out.append(": ");
            if (e == null || e[0] + e[1] == 0) {
                out.append('-');
            } else {
                int samples = e[0] + e[1];
                int pct = (int) Math.round(100.0 * e[0] / samples);
                out.append(e[0]).append('W').append('/').append(e[1]).append('L')
                        .append(" (").append(pct).append("%)");
            }
        }
        return out.toString();
    }

    public int samples(UUID playerId) {
        EnumMap<Preset, int[]> byRung = stats.get(playerId);
        if (byRung == null) {
            return 0;
        }
        int total = 0;
        synchronized (byRung) {
            for (int[] e : byRung.values()) {
                total += e[0] + e[1];
            }
        }
        return total;
    }

    // ------------------------------------------------------------------ persistence

    public void loadAll() {
        stats.clear();
        File file = dataFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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
            ConfigurationSection rungs = players.getConfigurationSection(key);
            if (rungs == null) {
                continue;
            }
            EnumMap<Preset, int[]> byRung = new EnumMap<>(Preset.class);
            for (String rungName : rungs.getKeys(false)) {
                Preset rung = BotDifficultySafe.parse(rungName);
                if (rung == null) {
                    continue;
                }
                byRung.put(rung, new int[]{rungs.getInt(rungName + ".wins"), rungs.getInt(rungName + ".losses")});
            }
            if (!byRung.isEmpty()) {
                stats.put(id, byRung);
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, EnumMap<Preset, int[]>> entry : stats.entrySet()) {
            EnumMap<Preset, int[]> byRung = entry.getValue();
            synchronized (byRung) {
                for (Map.Entry<Preset, int[]> rung : byRung.entrySet()) {
                    String base = "players." + entry.getKey() + "." + rung.getKey().name();
                    yaml.set(base + ".wins", rung.getValue()[0]);
                    yaml.set(base + ".losses", rung.getValue()[1]);
                }
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

    /** Dead-locked parse helper so this class never imports incomplete BotDifficulty helpers. */
    private static final class BotDifficultySafe {
        static Preset parse(String raw) {
            if (raw == null) {
                return null;
            }
            try {
                return Preset.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
