package com.rumilance.practice.elo;

/**
 * Pure, framework-agnostic Elo rating calculator used for ranked kit statistics. Matches the
 * plugin specification exactly:
 *
 * <ul>
 *   <li>{@code ExpectedScore = 1 / (1 + 10^((opponentElo - selfElo) / 400))}</li>
 *   <li>Win = 1.0, Draw = 0.5, Loss = 0.0 (see {@link MatchOutcome}).</li>
 *   <li>The first {@code provisionalGames} (default 20) ranked matches for a kit use
 *       {@code provisionalK} (default 64).</li>
 *   <li>From the 21st ranked match onward, {@code standardK} (default 32) applies.</li>
 *   <li>Any player who is currently in the top {@code topPercentFraction} (default 10%) of Elo
 *       among players with at least one ranked match uses {@code topPercentK} (default 26),
 *       which takes priority even during their provisional games.</li>
 *   <li>Resulting ratings are always floored at 0.</li>
 * </ul>
 *
 * <p>Determining whether a player is "in the top 10%" requires a snapshot of every ranked
 * player's Elo at the moment the match starts, which is outside the scope of a pure calculator -
 * callers compute that externally (e.g. via {@link #isWithinTopPercent(int, int, double)}) and
 * pass the result in as a boolean. This class has no dependency on Bukkit/Paper so it can be
 * exercised by plain JUnit tests.</p>
 */
public final class EloCalculator {

    public static final int DEFAULT_STARTING_RATING = 1000;

    private static final int DEFAULT_PROVISIONAL_GAMES = 20;
    private static final int DEFAULT_PROVISIONAL_K = 64;
    private static final int DEFAULT_STANDARD_K = 32;
    private static final int DEFAULT_TOP_PERCENT_K = 26;
    private static final double DEFAULT_TOP_PERCENT_FRACTION = 0.10d;

    private final int provisionalGames;
    private final int provisionalK;
    private final int standardK;
    private final int topPercentK;

    public EloCalculator() {
        this(DEFAULT_PROVISIONAL_GAMES, DEFAULT_PROVISIONAL_K, DEFAULT_STANDARD_K, DEFAULT_TOP_PERCENT_K);
    }

    public EloCalculator(int provisionalGames, int provisionalK, int standardK, int topPercentK) {
        if (provisionalGames < 0) {
            throw new IllegalArgumentException("provisionalGames must not be negative");
        }
        if (provisionalK <= 0 || standardK <= 0 || topPercentK <= 0) {
            throw new IllegalArgumentException("K-factors must be strictly positive");
        }
        this.provisionalGames = provisionalGames;
        this.provisionalK = provisionalK;
        this.standardK = standardK;
        this.topPercentK = topPercentK;
    }

    /**
     * Determines the K-factor that applies to a single player, given how many rated matches they
     * have already played for this kit, their current Elo (unused by the default formula, but
     * kept in the signature per spec so callers/tests can reason about "given elo X, K is Y"),
     * and whether they are currently ranked in the top {@code topPercentFraction} of players.
     * Top-percent status always takes priority, even over the provisional K-factor.
     */
    public int kFactorFor(int gamesPlayed, int elo, boolean top10Percent) {
        if (gamesPlayed < 0) {
            throw new IllegalArgumentException("gamesPlayed must not be negative");
        }
        if (top10Percent) {
            return topPercentK;
        }
        if (gamesPlayed < provisionalGames) {
            return provisionalK;
        }
        return standardK;
    }

    /**
     * @return the expected score (win probability, in the [0, 1] range) of a player rated
     * {@code ratingSelf} against an opponent rated {@code ratingOpponent}.
     */
    public static double expectedScore(int ratingSelf, int ratingOpponent) {
        return 1.0d / (1.0d + Math.pow(10.0d, (ratingOpponent - ratingSelf) / 400.0d));
    }

    /**
     * @return {@code true} if a player ranked {@code rank} (1-based, 1 = highest Elo) out of
     * {@code totalRankedPlayers} players who have played at least one ranked match falls within
     * the top {@code topPercentFraction} (e.g. {@code 0.10} for the top 10%). At least the single
     * top-ranked player is always considered "top percent" once at least one player is ranked.
     */
    public static boolean isWithinTopPercent(int rank, int totalRankedPlayers, double topPercentFraction) {
        if (totalRankedPlayers <= 0 || rank <= 0) {
            return false;
        }
        int cutoff = Math.max(1, (int) Math.ceil(totalRankedPlayers * topPercentFraction));
        return rank <= cutoff;
    }

    /**
     * Applies a rated match between two players and returns both of their updated ratings.
     *
     * @param playerA     rating/experience of player A before the match.
     * @param topPercentA whether player A is within the top-percent Elo bracket for this match.
     * @param playerB     rating/experience of player B before the match.
     * @param topPercentB whether player B is within the top-percent Elo bracket for this match.
     * @param outcomeForA match outcome from player A's perspective.
     */
    public EloUpdateResult applyMatch(EloRating playerA, boolean topPercentA,
                                       EloRating playerB, boolean topPercentB,
                                       MatchOutcome outcomeForA) {
        if (playerA == null || playerB == null || outcomeForA == null) {
            throw new IllegalArgumentException("playerA, playerB and outcomeForA must not be null");
        }

        double expectedA = expectedScore(playerA.rating(), playerB.rating());
        double expectedB = 1.0d - expectedA;

        double actualA = outcomeForA.scoreForA();
        double actualB = outcomeForA.scoreForB();

        int kFactorA = kFactorFor(playerA.gamesPlayed(), playerA.rating(), topPercentA);
        int kFactorB = kFactorFor(playerB.gamesPlayed(), playerB.rating(), topPercentB);

        int newRatingA = floorAtZero(playerA.rating() + kFactorA * (actualA - expectedA));
        int newRatingB = floorAtZero(playerB.rating() + kFactorB * (actualB - expectedB));

        return new EloUpdateResult(
                newRatingA,
                newRatingB,
                newRatingA - playerA.rating(),
                newRatingB - playerB.rating(),
                kFactorA,
                kFactorB
        );
    }

    /**
     * Convenience overload for matches where neither player is in the top-percent bracket.
     */
    public EloUpdateResult applyMatch(EloRating playerA, EloRating playerB, MatchOutcome outcomeForA) {
        return applyMatch(playerA, false, playerB, false, outcomeForA);
    }

    private static int floorAtZero(double rawNewRating) {
        long rounded = Math.round(rawNewRating);
        return (int) Math.max(0L, rounded);
    }

    public int provisionalGames() {
        return provisionalGames;
    }

    public int provisionalK() {
        return provisionalK;
    }

    public int standardK() {
        return standardK;
    }

    public int topPercentK() {
        return topPercentK;
    }

    public static double defaultTopPercentFraction() {
        return DEFAULT_TOP_PERCENT_FRACTION;
    }
}
