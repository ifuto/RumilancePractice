package com.rumilance.practice.match.result;

import com.rumilance.practice.config.PluginSettings;
import com.rumilance.practice.database.repository.DailyRankedStatsRepository;
import com.rumilance.practice.database.repository.MatchHistoryRepository;
import com.rumilance.practice.database.repository.RankedStatsRepository;
import com.rumilance.practice.glicko.GlickoCalculator;
import com.rumilance.practice.glicko.GlickoRating;
import com.rumilance.practice.glicko.GlickoUpdateResult;
import com.rumilance.practice.glicko.MatchOutcome;
import com.rumilance.practice.model.MatchHistoryEntry;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.session.MatchSession;
import com.rumilance.practice.state.MatchMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Updates Glicko-2 ratings, ranked kit stats and ranked match history. Never used for unranked.
 * One game = one rating period, which is the standard way live servers apply Glicko-2: after
 * every match both players get their new rating/deviation/volatility immediately.
 */
public final class RankedResultProcessor implements MatchResultProcessor {

    private final RankedStatsRepository rankedStatsRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final DailyRankedStatsRepository dailyRankedStatsRepository;
    private final GlickoCalculator glickoCalculator;
    private final PluginSettings settings;
    private final boolean drawCountsAsLoss;
    private com.rumilance.practice.database.repository.AnnualStreakRepository annualStreakRepository;

    public void setAnnualStreakRepository(
            com.rumilance.practice.database.repository.AnnualStreakRepository annualStreakRepository) {
        this.annualStreakRepository = annualStreakRepository;
    }

    public RankedResultProcessor(
            RankedStatsRepository rankedStatsRepository,
            MatchHistoryRepository matchHistoryRepository,
            DailyRankedStatsRepository dailyRankedStatsRepository,
            GlickoCalculator glickoCalculator,
            PluginSettings settings,
            boolean drawCountsAsLoss
    ) {
        this.rankedStatsRepository = rankedStatsRepository;
        this.matchHistoryRepository = matchHistoryRepository;
        this.dailyRankedStatsRepository = dailyRankedStatsRepository;
        this.glickoCalculator = glickoCalculator;
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
                .orElseGet(() -> newStarting(playerA, session.kitName()));
        RankedKitStats statsB = rankedStatsRepository.find(playerB, session.kitName())
                .orElseGet(() -> newStarting(playerB, session.kitName()));

        MatchOutcome outcome = draw
                ? MatchOutcome.DRAW
                : (winnerId != null && winnerId.equals(playerA) ? MatchOutcome.WIN : MatchOutcome.LOSS);

        GlickoUpdateResult update = glickoCalculator.applyMatch(
                toRating(statsA),
                toRating(statsB),
                outcome
        );

        int newPtA = (int) Math.round(update.newA().rating());
        int newPtB = (int) Math.round(update.newB().rating());
        RankedKitStats newA;
        RankedKitStats newB;
        if (draw) {
            newA = statsA.withDraw(newPtA, update.newA().deviation(), update.newA().volatility(), drawCountsAsLoss);
            newB = statsB.withDraw(newPtB, update.newB().deviation(), update.newB().volatility(), drawCountsAsLoss);
        } else if (winnerId != null && winnerId.equals(playerA)) {
            newA = statsA.withWin(newPtA, update.newA().deviation(), update.newA().volatility());
            newB = statsB.withLoss(newPtB, update.newB().deviation(), update.newB().volatility());
        } else {
            newA = statsA.withLoss(newPtA, update.newA().deviation(), update.newA().volatility());
            newB = statsB.withWin(newPtB, update.newB().deviation(), update.newB().volatility());
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
        recordAnnualStreaks(winnerId, playerA, playerB, draw);
    }

    /** New unrated row using the configured Glicko-2 starting values (1500/350/0.06 default). */
    private RankedKitStats newStarting(UUID uuid, String kit) {
        return RankedKitStats.starting(uuid, kit,
                settings.rankedStartingPt(),
                settings.rankedStartingDeviation(),
                settings.rankedStartingVolatility());
    }

    private static GlickoRating toRating(RankedKitStats stats) {
        return new GlickoRating(stats.pt(), stats.deviation(), stats.volatility());
    }

    /**
     * Annual max-win-streak bookkeeping: winner extends the streak, loser resets theirs.
     * Draws touch nothing. Stat failures must never break result processing.
     */
    private void recordAnnualStreaks(UUID winnerId, UUID playerA, UUID playerB, boolean draw) {
        var streaks = annualStreakRepository;
        if (streaks == null || draw || winnerId == null) {
            return;
        }
        UUID loserId = winnerId.equals(playerA) ? playerB : playerA;
        try {
            streaks.recordWin(winnerId);
        } catch (Throwable ignored) {
        }
        try {
            streaks.recordLoss(loserId);
        } catch (Throwable ignored) {
        }
    }
}
