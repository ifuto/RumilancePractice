package com.rumilance.practice.resourcepack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ViaVersion はリソースパックを変換しないので、古いクライアントには配らない判定。
 */
public class PackFormatPolicyTest {

    /** 0 は「チェックしない」= 今まで通り全員に配る。 */
    @Test
    void zeroThresholdDisablesTheCheck() {
        assertTrue(PackFormatPolicy.maySend(47, 0));
        assertTrue(PackFormatPolicy.maySend(PackFormatPolicy.UNKNOWN, 0));
        assertTrue(PackFormatPolicy.maySend(-1, -5));
    }

    /** 敷居より古いクライアントには配らない。 */
    @Test
    void oldClientsAreSkipped() {
        assertFalse(PackFormatPolicy.maySend(767, 769));
        assertTrue(PackFormatPolicy.tooOld(767, 769));
        assertTrue(PackFormatPolicy.maySend(769, 769));
        assertTrue(PackFormatPolicy.maySend(800, 769));
    }

    /** プロトコルが読めない場合はブロックしない(証拠がないので従来動作)。 */
    @Test
    void unknownProtocolNeverBlocks() {
        assertTrue(PackFormatPolicy.maySend(PackFormatPolicy.UNKNOWN, 769));
        assertFalse(PackFormatPolicy.tooOld(PackFormatPolicy.UNKNOWN, 769));
    }
}
