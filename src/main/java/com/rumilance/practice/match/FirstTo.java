package com.rumilance.practice.match;

/**
 * FT — 先取点数. How many round wins decide a duel series.
 *
 * <p>{@link #UNLIMITED} (0) means no limit: the series runs until the players stop
 * rematching, which is what queue matches always use. Duels can set it from the request
 * GUI: a left click adds 1, a shift click adds 5, and the value cycles
 * {@code 40 → ∞ → 1}.</p>
 *
 * <p>Pure on purpose — the stepping rule and the "is the series decided" test are the two
 * things worth testing, and neither needs a server.</p>
 */
public final class FirstTo {

    /** No limit: displayed as ∞, and the series never ends by score. */
    public static final int UNLIMITED = 0;
    /** Highest selectable FT. */
    public static final int MAX = 40;
    /** Left click step. */
    public static final int STEP_SMALL = 1;
    /** Shift click step. */
    public static final int STEP_LARGE = 5;

    private FirstTo() {
    }

    /**
     * One click of the FT button.
     *
     * <p>From ∞ a click starts a new count at {@code increment} (so +1 → 1, +5 → 5).
     * Past {@link #MAX} the value wraps back to ∞, which closes the
     * {@code 40 → ∞ → 1} cycle.</p>
     *
     * @param current   the current FT (0 = ∞)
     * @param increment 1 for a plain click, 5 for shift-click
     */
    public static int step(int current, int increment) {
        int delta = Math.max(1, increment);
        if (current <= UNLIMITED) {
            return Math.min(MAX, delta);
        }
        int next = Math.min(current, MAX) + delta;
        return next > MAX ? UNLIMITED : next;
    }

    /** True when {@code wins} has reached the limit — the series is decided. */
    public static boolean isComplete(int firstTo, int wins) {
        return firstTo > UNLIMITED && wins >= firstTo;
    }

    /** Human label: {@code ∞} for unlimited, otherwise the number. */
    public static String label(int firstTo) {
        return firstTo <= UNLIMITED ? "∞" : String.valueOf(firstTo);
    }

    /** Normalises a stored value (negative / over-max values cannot happen in a match). */
    public static int normalise(int firstTo) {
        if (firstTo <= UNLIMITED) {
            return UNLIMITED;
        }
        return Math.min(firstTo, MAX);
    }
}
