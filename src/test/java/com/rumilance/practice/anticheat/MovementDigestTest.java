package com.rumilance.practice.anticheat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovementDigestTest {

    private static void mirror(long[] state, double x, double y, double z, float yaw, float pitch) {
        long h = state[0];
        h = MovementDigest.mix(h, Double.doubleToRawLongBits(x));
        h = MovementDigest.mix(h, Double.doubleToRawLongBits(y));
        h = MovementDigest.mix(h, Double.doubleToRawLongBits(z));
        h = MovementDigest.mix(h, Float.floatToRawIntBits(yaw));
        h = MovementDigest.mix(h, Float.floatToRawIntBits(pitch));
        state[0] = h;
    }

    @Test
    void identicalStreamsProduceIdenticalDigests() {
        MovementDigest server = new MovementDigest();
        long[] client = {MovementDigest.FNV_OFFSET};
        // pretend both sides see the same 3 movement packets
        mirror(client, 10.5, 64.0, -3.25, 45.0f, -12.5f);
        server.record(10.5, 64.0, -3.25, 45.0f, -12.5f);
        mirror(client, 10.9, 64.0, -3.0, 45.0f, -12.5f);
        server.record(10.9, 64.0, -3.0, 45.0f, -12.5f);
        mirror(client, 10.9, 64.0, -3.0, 90.0f, 0.0f); // look-only, pos unchanged
        server.record(10.9, 64.0, -3.0, 90.0f, 0.0f);
        assertEquals(server.count(), 3);
        assertEquals(server.digestAt(3), client[0]);
        // frame-2 digest equals an independent replay of the first two frames
        MovementDigest again = new MovementDigest();
        again.record(10.5, 64.0, -3.25, 45.0f, -12.5f);
        again.record(10.9, 64.0, -3.0, 45.0f, -12.5f);
        assertEquals(server.digestAt(2), again.digestAt(2));
    }

    @Test
    void anyDivergenceChangesTheDigest() {
        MovementDigest a = new MovementDigest();
        MovementDigest b = new MovementDigest();
        a.record(1.0, 2.0, 3.0, 10f, 20f);
        b.record(1.0, 2.0, 3.0, 10f, 20f);
        a.record(4.0, 5.0, 6.0, 10f, 20f);
        b.record(4.0000000000001, 5.0, 6.0, 10f, 20f); // tampered double LSBs
        assertNotEquals(a.digestAt(2), b.digestAt(2));
    }

    @Test
    void windowAndRebaseBehave() {
        MovementDigest d = new MovementDigest();
        for (int i = 0; i < 9000; i++) {
            d.record(i, 0, 0, 0f, 0f);
        }
        assertNull(d.digestAt(1)); // slid out of the 8192 window
        assertEquals(d.digestAt(9000), d.digestAt(d.count()));
        d.rebase(9000, 123456789L);
        assertEquals(123456789L, d.digestAt(9000).longValue());
        d.record(9001, 0, 0, 0f, 0f);
        MovementDigest replay = new MovementDigest();
        replay.rebase(9000, 123456789L);
        replay.record(9001, 0, 0, 0f, 0f);
        assertEquals(d.digestAt(9001), replay.digestAt(9001));
    }
}
