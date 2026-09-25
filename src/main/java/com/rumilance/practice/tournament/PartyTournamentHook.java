package com.rumilance.practice.tournament;

import com.rumilance.practice.state.TeamColor;
import java.util.Set;
import java.util.UUID;

/**
 * Sparse glue between {@link com.rumilance.practice.match.MatchService} and
 * {@link TournamentService}. Team matches that belong to a running tournament carry a
 * {@link com.rumilance.practice.session.MatchSession#tournamentTag()} so the tournament can
 * reconnect the waiting (spectating) players the moment the match that involves them
 * finishes — nothing here re-implements the team match lifecycle.
 *
 * <p>Call order across a single card: exactly one of
 * {@link #completeMatch}/{@link #matchFailed} (result recorded the tick the match ends)
 * followed by {@link #settled} (players sent to lobby, rematch window expired) — the moment
 * it is safe to spawn the next card. All calls are skippable and best-effort: the tournament
 * must never throw into the match flow.
 */
public interface PartyTournamentHook {

    /**
     * Record the result of a finished tournament card. {@code winner} is the winning color,
     * or null on a draw / no-result. This fires while the match is still in the rematch
     * window — the bracket result is recorded here, but the next card may only start on
     * {@link #settled}.
     */
    void completeMatch(String tag, UUID matchId, TeamColor winner, boolean draw);

    /**
     * The finished card has been fully settled: its players are back in the lobby and its
     * arena is being released. Safe point to advance the bracket and issue the next card.
     */
    void settled(String tag, UUID matchId);

    /**
     * A tournament card that never properly started (prepare/fail/cancel path). Every listed
     * player was already reset to LOBBY by {@code MatchService}; the tournament must drop the
     * card without mis-scoring it and let those players rejoin the bracket flow.
     */
    void matchFailed(String tag, Set<UUID> players);
}
