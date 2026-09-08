package com.rumilance.practice.anticheat;

/**
 * Server-side replay of the client's outgoing-movement digest (pair of the mod's
 * {@code OutgoingDigest}). For every {@code PlayerMoveEvent} the server fires, the same
 * 64-bit FNV-1a frame (x,y,z double bits + yaw,pitch float bits) is mixed, so at any packet
 * count {@code c} the server knows what the honest mod's digest MUST be.
 *
 * <p>Consistency contract shared with the client tap (both sides must stay one-to-one):
 * on-ground-only/unchanged packets fire no Bukkit event and are skipped client-side;
 * vehicle movement fires no {@code PlayerMoveEvent} and is excluded client-side; TCP is
 * ordered and lossless, so legitimate clients always land in-window.</p>
 */
final class MovementDigest {

    static final long FNV_OFFSET = 0xcbf29ce484222325L;
    static final long FNV_PRIME = 0x100000001b3L;
    /** Rolling history window (power of two). ~7 minutes of headroom at 20 moves/s. */
    private static final int WINDOW = 8192;
    private static final int MASK = WINDOW - 1;

    private final long[] window = new long[WINDOW];
    /** Total frames recorded so far (== count the client reports if both sides agree). */
    private long count;
    private long hash = FNV_OFFSET;

    static long mix(long h, long v) {
        h ^= v;
        h *= FNV_PRIME;
        return h;
    }

    /** Records one server-observed move frame. */
    synchronized void record(double x, double y, double z, float yaw, float pitch) {
        long h = hash;
        h = mix(h, Double.doubleToRawLongBits(x));
        h = mix(h, Double.doubleToRawLongBits(y));
        h = mix(h, Double.doubleToRawLongBits(z));
        h = mix(h, Float.floatToRawIntBits(yaw));
        h = mix(h, Float.floatToRawIntBits(pitch));
        hash = h;
        window[(int) (count & MASK)] = h;
        count++;
    }

    /** Digest expected after {@code c} frames, or {@code null} when outside the window. */
    synchronized Long digestAt(long c) {
        if (c <= 0 || c > count) {
            return c == 0 ? FNV_OFFSET : null;
        }
        long oldest = count - Math.min(count, WINDOW);
        if (c < oldest) {
            return null;
        }
        return window[(int) ((c - 1) & MASK)];
    }

    /** Resets after a drift event (resync); both sides re-anchor at the given frame. */
    synchronized void rebase(long newCount, long newHash) {
        count = newCount;
        hash = newHash;
        if (newCount > 0) {
            window[(int) ((newCount - 1) & MASK)] = newHash;
        }
    }

    synchronized long count() {
        return count;
    }
}
