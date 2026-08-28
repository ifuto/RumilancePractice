package com.rumilance.practice.tnt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeTntTest {

    @Test
    void clampKeepsG1axDefault() {
        assertEquals(20, PracticeTnt.clampFuseTicks(PracticeTnt.DEFAULT_FUSE_TICKS));
    }

    @Test
    void clampRejectsNegativeAndAboveVanillaTnt() {
        assertEquals(0, PracticeTnt.clampFuseTicks(-8));
        assertEquals(80, PracticeTnt.clampFuseTicks(800));
    }

    @Test
    void tntAndEggReasonMatchBukkitNames() {
        assertTrue(PracticeTnt.isTntMaterial("TNT"));
        assertFalse(PracticeTnt.isTntMaterial("TNT_MINECART"));
        assertTrue(PracticeTnt.isSpawnerEggReason("SPAWNER_EGG"));
        assertFalse(PracticeTnt.isSpawnerEggReason("NATURAL"));
    }

    @Test
    void glassIncludesPanesAndTinted() {
        assertTrue(PracticeTnt.isGlass("GLASS"));
        assertTrue(PracticeTnt.isGlass("WHITE_STAINED_GLASS_PANE"));
        assertTrue(PracticeTnt.isGlass("TINTED_GLASS"));
        assertFalse(PracticeTnt.isGlass("GLASS_BOTTLE"));
    }

    @Test
    void creeperDelayIsRemainingSwellNotElapsed() {
        assertEquals(20, PracticeTnt.creeperExplodeDelayTicks(20, 0));
        assertEquals(1, PracticeTnt.creeperExplodeDelayTicks(20, 20));
        assertEquals(1, PracticeTnt.creeperExplodeDelayTicks(0, 0));
    }
}
