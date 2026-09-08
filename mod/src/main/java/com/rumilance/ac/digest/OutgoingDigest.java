package com.rumilance.ac.digest;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Rolling digest of the movement packets THIS client has actually put on the wire.
 *
 * <h3>Why this exists (the "theoretically unbypassable" part)</h3>
 * Attestation alone ("a mod is present") can be forged by an oracle: run the official mod
 * in a clean JVM and relay only its handshake. Digest binding closes that: the server
 * independently rebuilds the same digest from the move events IT receives, and any cheat
 * that injects, drops or rewrites movement packets (reach-relevant position spoof, blink,
 * timer...) makes the two streams diverge. A forger would have to present gameplay
 * IDENTICAL to a clean client — i.e. don't cheat.
 *
 * <h3>Zero-false-positive invariants (Sodium & friends safe)</h3>
 * <ul>
 *   <li>Only {@link PlayerMoveC2SPacket} subclasses enter the digest. Everything else
 *       (keep-alives, plugin messages, chat, swings) is ignored on both sides.</li>
 *   <li>Packets with unchanged position AND rotation are skipped, because the server does
 *       not fire its move event for them — mirroring keeps the two streams ONE-TO-ONE.</li>
 *   <li>Vehicle move packets are excluded, because the server move event equally does not
 *       fire while riding.</li>
 *   <li>Inputs are raw IEEE-754 bits of x/y/z/yaw/pitch exactly as sent (TCP is ordered and
 *       lossless), so legitimate optimisation mods that never touch movement serialisation
 *       (Sodium, Iris, Lithium, ShieldStats…) produce byte-identical digests.</li>
 * </ul>
 *
 * Digest: 64-bit FNV-1a over a 5-field frame per packet. The plugin re-implements the same
 * function; both sides agree bit-for-bit by construction (see docs/ANTICHEAT.md).
 */
public final class OutgoingDigest {

    public static final long FNV_OFFSET = 0xcbf29ce484222325L;
    public static final long FNV_PRIME = 0x100000001b3L;

    private static long hash = FNV_OFFSET;
    private static long count;

    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastPitch;

    private OutgoingDigest() {
    }

    /** FNV-1a 64 mixing step, shared formula with the server. */
    public static long mix(long h, long v) {
        h ^= v;
        h *= FNV_PRIME;
        return h;
    }

    /** Called (HEAD of ClientConnection#send) for every outgoing packet. */
    public static void note(Object packet) {
        if (!(packet instanceof PlayerMoveC2SPacket move)) {
            return;
        }
        if (packet instanceof net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket) {
            return; // vehicles: no matching server move event — excluded both sides
        }
        double x = move.getX(lastX);
        double y = move.getY(lastY);
        double z = move.getZ(lastZ);
        float yaw = move.getYaw(lastYaw);
        float pitch = move.getPitch(lastPitch);
        boolean posChanged = packet instanceof PlayerMoveC2SPacket.Full
                || packet instanceof PlayerMoveC2SPacket.PositionAndOnGround;
        boolean lookChanged = packet instanceof PlayerMoveC2SPacket.Full
                || packet instanceof PlayerMoveC2SPacket.LookAndOnGround;
        if (!posChanged && !lookChanged) {
            return; // OnGroundOnly with no change: server fires no event — skip
        }
        if (x == lastX && y == lastY && z == lastZ && yaw == lastYaw && pitch == lastPitch) {
            return; // server-side only fires on change — keep streams 1:1
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        long h = hash;
        h = mix(h, Double.doubleToRawLongBits(x));
        h = mix(h, Double.doubleToRawLongBits(y));
        h = mix(h, Double.doubleToRawLongBits(z));
        h = mix(h, Float.floatToRawIntBits(yaw));
        h = mix(h, Float.floatToRawIntBits(pitch));
        hash = h;
        count++;
    }

    /** Current packet count and digest for the VERIFY reply. */
    public static synchronized long[] snapshot() {
        return new long[]{count, hash};
    }

    /**
     * Resets on server disconnect: the server builds a fresh per-join stream, so without
     * this the first pong after a legit relog would always look divergent.
     */
    public static synchronized void reset() {
        hash = FNV_OFFSET;
        count = 0L;
        lastX = 0;
        lastY = 0;
        lastZ = 0;
        lastYaw = 0f;
        lastPitch = 0f;
    }
}
