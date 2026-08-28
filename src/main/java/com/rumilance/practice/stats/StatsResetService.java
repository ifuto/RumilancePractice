package com.rumilance.practice.stats;

import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.FfaStatsRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.database.repository.WinStreakRepository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Wipes ranked OpenSkill rows, FFA K/D, daily ranked counters, and match history so the next
 * game recreates {@link com.rumilance.practice.model.RankedKitStats#starting} defaults.
 */
public final class StatsResetService {

    private final RankedStatsRepository rankedStatsRepository;
    private final FfaStatsRepository ffaStatsRepository;
    private final DailyRankedStatsRepository dailyRankedStatsRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final WinStreakRepository winStreakRepository;

    public StatsResetService(
            RankedStatsRepository rankedStatsRepository,
            FfaStatsRepository ffaStatsRepository,
            DailyRankedStatsRepository dailyRankedStatsRepository,
            MatchHistoryRepository matchHistoryRepository,
            WinStreakRepository winStreakRepository
    ) {
        this.rankedStatsRepository = Objects.requireNonNull(rankedStatsRepository);
        this.ffaStatsRepository = Objects.requireNonNull(ffaStatsRepository);
        this.dailyRankedStatsRepository = Objects.requireNonNull(dailyRankedStatsRepository);
        this.matchHistoryRepository = Objects.requireNonNull(matchHistoryRepository);
        this.winStreakRepository = Objects.requireNonNull(winStreakRepository);
    }

    public void resetAll() throws SQLException {
        rankedStatsRepository.deleteAll();
        ffaStatsRepository.deleteAll();
        dailyRankedStatsRepository.deleteAll();
        matchHistoryRepository.deleteAll();
        winStreakRepository.deleteAll();
    }

    public void resetPlayer(UUID uuid) throws SQLException {
        Objects.requireNonNull(uuid, "uuid");
        rankedStatsRepository.deleteForPlayer(uuid);
        ffaStatsRepository.deleteForPlayer(uuid);
        dailyRankedStatsRepository.deleteForPlayer(uuid);
        matchHistoryRepository.deleteForPlayer(uuid);
        winStreakRepository.deleteForPlayer(uuid);
    }
}
