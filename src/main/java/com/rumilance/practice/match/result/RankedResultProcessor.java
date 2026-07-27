package com.rumilance.practice.match.result;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.elo.EloCalculator;
import com.rumilance.practice.elo.EloRating;
import com.rumilance.practice.elo.EloUpdateResult;
import com.rumilance.practice.elo.MatchOutcome;
import com.rumilance.practice.model.MatchHistoryEntry;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchMode;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Updates Elo, ranked kit stats and ranked match history. Never used for unranked.
 */
public final class RankedResultProcessor implements MatchResultProcessor {

    private final RankedStatsRepository rankedStatsRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final DailyRankedStatsRepository dailyRankedStatsRepository;
    private final EloCalculator eloCalculator;
    private final PluginSettings settings;
    private final boolean drawCountsAsLoss;

    public RankedResultProcessor(
            RankedStatsRepository rankedStatsRepository,
            MatchHistoryRepository matchHistoryRepository,
            DailyRankedStatsRepository dailyRankedStatsRepository,
            EloCalculator eloCalculator,
            PluginSettings settings,
            boolean drawCountsAsLoss
    ) {
        this.rankedStatsRepository = rankedStatsRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.dailyRankedStatsRepository = dailyRankedStatsRepository;
        this.eloCalculator = eloCalculator;
        this.settings = settings;
        this.drawCountsAsLoss = drawCountsAsLoss;
    }

    @Override
    public void process(MatchSession session, UUID winnerId, boolean draw) throws Exception {
        if (session.mode() != MatchMode.RANKED) {
            throw new IllegalArgumentException("RankedResultProcessor only accepts RANKED matches");
        }
        if (!session.tryMarkResultApplied()) {
            return;
        }
        if (matchHistoryRepository.findById(session.id()).isPresent()) {
            return;
        }

        UUID playerA = session.participants().get(0);
        UUID playerB = session.participants().get(1);
        RankedKitStats statsA = rankedStatsRepository.find(playerA, session.kitName())
                .orElseGet(() -> RankedKitStats.starting(playerA, session.kitName()));
        RankedKitStats statsB = rankedStatsRepository.find(playerB, session.kitName())
                .orElseGet(() -> RankedKitStats.starting(playerB, session.kitName()));

        boolean topA = isTopPercent(statsA);
        boolean topB = isTopPercent(statsB);
        MatchOutcome outcome = draw
                ? MatchOutcome.DRAW
                : (winnerId != null && winnerId.equals(playerA) ? MatchOutcome.WIN : MatchOutcome.LOSS);

        EloUpdateResult update = eloCalculator.applyMatch(
                new EloRating(statsA.elo(), statsA.gamesPlayed()), topA,
                new EloRating(statsB.elo(), statsB.gamesPlayed()), topB,
                outcome
        );

        RankedKitStats newA;
        RankedKitStats newB;
        if (draw) {
            newA = statsA.withDraw(update.newRatingA(), drawCountsAsLoss);
            newB = statsB.withDraw(update.newRatingB(), drawCountsAsLoss);
        } else if (winnerId != null && winnerId.equals(playerA)) {
            newA = statsA.withWin(update.newRatingA());
            newB = statsB.withLoss(update.newRatingB());
        } else {
            newA = statsA.withLoss(update.newRatingA());
            newB = statsB.withWin(update.newRatingB());
        }

        rankedStatsRepository.upsert(newA);
        rankedStatsRepository.upsert(newB);
        matchHistoryRepository.insert(new MatchHistoryEntry(
                session.id(),
                playerA,
                playerB,
                session.kitName(),
                MatchMode.RANKED,
                draw ? null : winnerId,
                true,
                session.startedAt() == null ? Instant.now() : session.startedAt(),
                session.endedAt() == null ? Instant.now() : session.endedAt()
        ));
        dailyRankedStatsRepository.increment(playerA, (!draw && winnerId != null && winnerId.equals(playerA)) ? 1 : 0, 1);
        dailyRankedStatsRepository.increment(playerB, (!draw && winnerId != null && winnerId.equals(playerB)) ? 1 : 0, 1);
    }

    private boolean isTopPercent(RankedKitStats stats) throws Exception {
        List<RankedKitStats> all = rankedStatsRepository.topByKit(stats.kit(), Integer.MAX_VALUE);
        List<RankedKitStats> ranked = all.stream()
                .filter(s -> s.gamesPlayed() >= 1)
                .sorted(Comparator.comparingInt(RankedKitStats::elo).reversed())
                .toList();
        if (ranked.isEmpty()) {
            return false;
        }
        int rank = 1;
        for (RankedKitStats entry : ranked) {
            if (entry.uuid().equals(stats.uuid())) {
                return EloCalculator.isWithinTopPercent(rank, ranked.size(), settings.rankedTopPercentFraction());
            }
            rank++;
        }
        return false;
    }
}
