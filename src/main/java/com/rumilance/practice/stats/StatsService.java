package com.rumilance.practice.stats;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.model.MatchHistoryEntry;
import com.rumilance.practice.model.RankedKitStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ranked-only statistics and profile formatting. Unranked is never included.
 */
public final class StatsService {

    private final RankedStatsRepository rankedStatsRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final DailyRankedStatsRepository dailyRankedStatsRepository;
    private final ConfigService configService;

    public StatsService(
            RankedStatsRepository rankedStatsRepository,
            MatchHistoryRepository matchHistoryRepository,
            DailyRankedStatsRepository dailyRankedStatsRepository,
            ConfigService configService
    ) {
        this.rankedStatsRepository = rankedStatsRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.dailyRankedStatsRepository = dailyRankedStatsRepository;
        this.configService = configService;
    }

    public Optional<RankedKitStats> kitStats(UUID uuid, String kit) throws Exception {
        return rankedStatsRepository.find(uuid, kit);
    }

    public List<RankedKitStats> allKits(UUID uuid) throws Exception {
        return rankedStatsRepository.findAllForPlayer(uuid);
    }

    public List<RankedKitStats> topElo(String kit, int limit) throws Exception {
        return rankedStatsRepository.topByKit(kit, limit);
    }

    public List<RankedKitStats> topEloOverall(int limit) throws Exception {
        return rankedStatsRepository.findTopEloOverall(limit);
    }

    public List<RankedKitStats> topWinStreak(int limit) throws Exception {
        return rankedStatsRepository.findAllOrderedByWinStreak(limit);
    }

    public List<DailyRankedStatsRepository.DailyEntry> topDailyKills(int limit) throws Exception {
        return dailyRankedStatsRepository.topKillsToday(limit);
    }

    public List<DailyRankedStatsRepository.DailyEntry> topDailyMatches(int limit) throws Exception {
        return dailyRankedStatsRepository.topMatchesToday(limit);
    }

    public List<MatchHistoryEntry> recentRanked(UUID uuid, int limit) throws Exception {
        return matchHistoryRepository.findRecentForPlayer(uuid, limit).stream()
                .filter(MatchHistoryEntry::ranked)
                .toList();
    }

    public String winRateLabel(RankedKitStats stats) {
        if (stats.gamesPlayed() < 21) {
            return "計測中 " + stats.gamesPlayed() + "/21";
        }
        return String.format("%.1f%%", stats.winRate() * 100.0d);
    }

    public double kd(RankedKitStats stats) {
        int deaths = Math.max(1, stats.losses());
        return (double) stats.wins() / deaths;
    }

    public Component profileMessage(UUID uuid, String name) throws Exception {
        List<RankedKitStats> kits = allKits(uuid);
        int matches = kits.stream().mapToInt(RankedKitStats::gamesPlayed).sum();
        int wins = kits.stream().mapToInt(RankedKitStats::wins).sum();
        int losses = kits.stream().mapToInt(RankedKitStats::losses).sum();
        int bestStreak = kits.stream().mapToInt(RankedKitStats::winStreak).max().orElse(0);
        String bestKit = kits.stream().max(Comparator.comparingInt(RankedKitStats::wins))
                .map(RankedKitStats::kit).orElse("-");
        double winRate = matches == 0 ? 0 : (double) wins / matches;
        FileConfiguration profile = configService.profile();
        String barChar = profile.getString("progress.char", "=");
        int barLen = profile.getInt("progress.length", 20);
        int filled = (int) Math.round(winRate * barLen);
        String bar = barChar.repeat(Math.max(0, filled)) + "-".repeat(Math.max(0, barLen - filled));
        double kd = losses == 0 ? wins : (double) wins / losses;

        return Component.text("· · ·\n", NamedTextColor.DARK_GRAY)
                .append(Component.text("MCID : " + name + "\n", NamedTextColor.WHITE))
                .append(Component.text("───ランク戦戦闘記録───\n", NamedTextColor.GOLD))
                .append(Component.text("総試合数 : " + matches + "\n", NamedTextColor.GRAY))
                .append(Component.text("勝利 : " + wins + "\n", NamedTextColor.GREEN))
                .append(Component.text("敗北 : " + losses + "\n", NamedTextColor.RED))
                .append(Component.text("勝率 : " + (matches < 21 ? ("計測中 " + matches + "/21")
                        : String.format("%.1f%%", winRate * 100)) + "\n", NamedTextColor.AQUA))
                .append(Component.text("[" + bar + "]\n", NamedTextColor.YELLOW))
                .append(Component.text(String.format("K/Dレート : %.1f\n", kd), NamedTextColor.AQUA))
                .append(Component.text("最高連勝 : " + bestStreak + "\n", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("得意Kit : " + bestKit + "\n", NamedTextColor.GOLD))
                .append(Component.text("· · ·", NamedTextColor.DARK_GRAY));
    }

    public static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return Bukkit.getOfflinePlayer(uuid).getName() == null
                ? uuid.toString().substring(0, 8)
                : Bukkit.getOfflinePlayer(uuid).getName();
    }
}
