package com.rumilance.practice.practice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bot ladder mirrors the Quantum's PvP Practice map rungs (NPC, Easy, Intermediate, Hard,
 * CRAZY, MASTER, SURVIVAL MASTER) and has to stay playable from both ends: the low rungs must not
 * be able to out-damage / out-regen a beginner, and every rung must be beatable.
 */
class BotDifficultyTest {

    @Test
    void botHpIsPlayerEqualEverywhere() {
        // Quantum mech_train/common: quantumbot max_health base = 20 (= player 10 hearts).
        // Rungs differentiate via damage / cadence / reach / aim, NOT health.
        for (BotDifficulty.Preset rung : LADDER) {
            BotDifficulty d = BotDifficulty.of(rung);
            assertEquals(20.0d, d.botMaxHp(), 1e-9, rung + " hp");
        }
    }

    private static final BotDifficulty.Preset[] LADDER = {
            BotDifficulty.Preset.NPC,
            BotDifficulty.Preset.EASY,
            BotDifficulty.Preset.INTERMEDIATE,
            BotDifficulty.Preset.HARD,
            BotDifficulty.Preset.CRAZY,
            BotDifficulty.Preset.MASTER,
            BotDifficulty.Preset.SURVIVAL_MASTER
    };

    @Test
    void defaultsToTheIntermediateRung() {
        BotDifficulty d = new BotDifficulty();
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, d.preset());
        assertEquals(BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE).serialize(), d.serialize());
    }

    @Test
    void freshInstanceIsExactlyTheIntermediateRung() {
        // The field defaults ARE the default rung: a drift here silently changes every player
        // whose difficulty was never saved (and breaks the serialize round-trip).
        BotDifficulty fresh = new BotDifficulty();
        BotDifficulty rung = BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
        assertEquals(rung.preset(), fresh.preset());
        assertEquals(rung.botMaxHp(), fresh.botMaxHp(), 1e-9);
        assertEquals(rung.attackDamage(), fresh.attackDamage(), 1e-9);
        assertEquals(rung.attackIntervalMs(), fresh.attackIntervalMs());
        assertEquals(rung.moveSpeed(), fresh.moveSpeed(), 1e-9);
        assertEquals(rung.regenPerSecond(), fresh.regenPerSecond(), 1e-9);
        assertEquals(rung.comboCooldownMs(), fresh.comboCooldownMs());
        assertEquals(rung.shieldStun(), fresh.shieldStun());
        assertEquals(rung.shieldReduction(), fresh.shieldReduction(), 1e-9);
        assertEquals(rung.totemGoal(), fresh.totemGoal());
        assertEquals(rung.reachBlocks(), fresh.reachBlocks(), 1e-9);
        assertEquals(rung.aimSpreadDegrees(), fresh.aimSpreadDegrees(), 1e-9);
    }

    @Test
    void legacySavesMapOntoTheMapRungs() {
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.parsePreset("NORMAL"));
        assertEquals(BotDifficulty.Preset.CRAZY, BotDifficulty.parsePreset("EXPERT"));
        assertEquals(BotDifficulty.Preset.MASTER, BotDifficulty.parsePreset("MASTER"));
        assertEquals(BotDifficulty.Preset.SURVIVAL_MASTER, BotDifficulty.parsePreset("survival_master"));
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.parsePreset(""));
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.parsePreset(null));
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.parsePreset("WHATEVER"));
    }

    @Test
    void savedPresetsReapplyTheCurrentLadder() {
        // An old save with the pre-rebalance numbers must come back with the tuned rung values,
        // not with a 100 HP / 10 HP-per-second bot nobody can kill.
        BotDifficulty legacy = BotDifficulty.deserialize("NORMAL:100.0:5.0:900:0.24:10.0:2600:true:0.5:3");
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, legacy.preset());
        BotDifficulty tuned = BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
        assertEquals(tuned.botMaxHp(), legacy.botMaxHp(), 1e-9);
        assertEquals(tuned.regenPerSecond(), legacy.regenPerSecond(), 1e-9);
        assertEquals(tuned.attackIntervalMs(), legacy.attackIntervalMs());
        // Reach / aim were not in the old format: the rung defaults fill in.
        assertEquals(tuned.reachBlocks(), legacy.reachBlocks(), 1e-9);
        assertEquals(tuned.aimSpreadDegrees(), legacy.aimSpreadDegrees(), 1e-9);
    }

    @Test
    void customSavesKeepTheirOwnNumbers() {
        String raw = "CUSTOM:77.0:6.5:640:0.3:4.0:1900:false:0.35:4:3.3:2.0";
        BotDifficulty d = BotDifficulty.deserialize(raw);
        assertEquals(BotDifficulty.Preset.CUSTOM, d.preset());
        assertEquals(77.0d, d.botMaxHp(), 1e-9);
        assertEquals(6.5d, d.attackDamage(), 1e-9);
        assertEquals(640L, d.attackIntervalMs());
        assertEquals(0.3d, d.moveSpeed(), 1e-9);
        assertEquals(4.0d, d.regenPerSecond(), 1e-9);
        assertEquals(1900L, d.comboCooldownMs());
        assertFalse(d.shieldStun());
        assertEquals(0.35d, d.shieldReduction(), 1e-9);
        assertEquals(4, d.totemGoal());
        assertEquals(3.3d, d.reachBlocks(), 1e-9);
        assertEquals(2.0d, d.aimSpreadDegrees(), 1e-9);
        assertEquals(raw, d.serialize());
    }

    @Test
    void deserializeNeverThrowsOnGarbage() {
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.deserialize(null).preset());
        assertEquals(BotDifficulty.Preset.INTERMEDIATE, BotDifficulty.deserialize(":::").preset());
        // A corrupt CUSTOM row falls back to the default rung instead of keeping garbage.
        assertEquals(BotDifficulty.Preset.INTERMEDIATE,
                BotDifficulty.deserialize("CUSTOM:not:a:number").preset());
    }

    @Test
    void ladderGetsStrictlyHarder() {
        BotDifficulty previous = null;
        for (BotDifficulty.Preset rung : LADDER) {
            BotDifficulty d = BotDifficulty.of(rung);
            if (previous != null) {
                assertTrue(d.botMaxHp() >= previous.botMaxHp(), rung + " hp");
                assertTrue(d.attackDamage() >= previous.attackDamage(), rung + " damage");
                assertTrue(d.attackIntervalMs() <= previous.attackIntervalMs(), rung + " interval");
                assertTrue(d.moveSpeed() >= previous.moveSpeed(), rung + " speed");
                assertTrue(d.comboCooldownMs() <= previous.comboCooldownMs(), rung + " combo");
                assertTrue(d.aimSpreadDegrees() <= previous.aimSpreadDegrees(), rung + " aim");
                assertTrue(d.totemGoal() >= previous.totemGoal(), rung + " goal");
            }
            previous = d;
        }
    }

    @Test
    void noRungIsAnUnkillableTank() {
        // The old NORMAL rung regenerated 10 HP/s on a 100 HP body: a crystal combo every ~2.6s
        // could never out-damage it, so the bot could not be beaten at all. Every rung now has to
        // stay inside "one good combo window": at most ~2 seconds of regen between combos.
        for (BotDifficulty.Preset rung : LADDER) {
            BotDifficulty d = BotDifficulty.of(rung);
            double regenBetweenCombos = d.regenPerSecond() * (d.comboCooldownMs() / 1000.0d);
            assertTrue(regenBetweenCombos <= 16.0d,
                    rung + " regenerates " + regenBetweenCombos + " HP between combos");
            assertTrue(d.botMaxHp() <= 140.0d, rung + " hp " + d.botMaxHp());
        }
    }

    @Test
    void beginnerRungsCannotBurstThePlayerDown() {
        // NPC never attacks; Easy swings for 3 at most once every ~1.15s (map rung 1: hitcd 23t),
        // so a beginner with golden apples cannot be melted by the sparring partner.
        BotDifficulty npc = BotDifficulty.of(BotDifficulty.Preset.NPC);
        assertEquals(0.0d, npc.attackDamage(), 1e-9);
        BotDifficulty easy = BotDifficulty.of(BotDifficulty.Preset.EASY);
        assertTrue(easy.attackDamage() <= 3.0d);
        assertTrue(easy.attackIntervalMs() >= 1100L);
        assertTrue(easy.aimSpreadDegrees() >= 5.0d);
        assertFalse(easy.shieldStun());
    }

    @Test
    void intermediateRungMatchesTheMapRung2Timing() {
        BotDifficulty d = BotDifficulty.of(BotDifficulty.Preset.INTERMEDIATE);
        // map hitcd 15t = 750ms, reach 1.6 -> ~3.0 blocks with armor/movement slack, aim 4-5.
        assertEquals(750L, d.attackIntervalMs());
        assertEquals(3.0d, d.reachBlocks(), 1e-9);
        assertTrue(d.aimSpreadDegrees() >= 3.0d && d.aimSpreadDegrees() <= 6.0d);
        assertTrue(d.attackDamage() >= 3.0d && d.attackDamage() <= 5.0d);
        assertEquals(3, d.totemGoal());
    }

    @Test
    void manualEditsFlipToCustomAndClamp() {
        BotDifficulty d = BotDifficulty.of(BotDifficulty.Preset.HARD);
        d.setBotMaxHp(65);
        assertEquals(BotDifficulty.Preset.CUSTOM, d.preset());
        assertEquals(65.0d, d.botMaxHp(), 1e-9);
        d.setBotMaxHp(10_000);
        assertEquals(200.0d, d.botMaxHp(), 1e-9);
        d.setBotMaxHp(-5);
        assertEquals(20.0d, d.botMaxHp(), 1e-9);
        d.setAttackDamage(-3);
        assertEquals(0.0d, d.attackDamage(), 1e-9);
        d.setReachBlocks(99);
        assertEquals(4.5d, d.reachBlocks(), 1e-9);
        d.setAimSpreadDegrees(-4);
        assertEquals(0.0d, d.aimSpreadDegrees(), 1e-9);
        d.setAttackIntervalMs(1);
        assertEquals(150L, d.attackIntervalMs());
    }

    @Test
    void applyPresetLeavesCustomAlone() {
        BotDifficulty d = BotDifficulty.of(BotDifficulty.Preset.MASTER);
        d.setRegenPerSecond(1);
        d.applyPreset(BotDifficulty.Preset.CUSTOM);
        assertEquals(BotDifficulty.Preset.CUSTOM, d.preset());
        assertEquals(1.0d, d.regenPerSecond(), 1e-9);
    }
}
