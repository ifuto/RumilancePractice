package com.rumilance.practice.util;

/**
 * Standard competition ranking ("1224" ranking): equal values share one rank and the next
 * distinct value jumps ahead by the number of tied entries — 1st, 2nd, 2nd, 4th.
 *
 * <p>Input must already be sorted best-first. The LIST ORDER inside a tie is the caller's
 * tie-break (the leaderboard lists earlier achievers above later ones); only the displayed
 * rank numbers come from here.</p>
 */
public final class CompetitionRanks {

    private CompetitionRanks() {
    }

    /** @param values best-first sorted values; @return parallel array of 1-based ranks */
    public static int[] ranks(long[] values) {
        int[] out = new int[values.length];
        long last = 0;
        int rank = 0;
        for (int i = 0; i < values.length; i++) {
            if (i == 0 || values[i] != last) {
                rank = i + 1;
                last = values[i];
            }
            out[i] = rank;
        }
        return out;
    }
}
