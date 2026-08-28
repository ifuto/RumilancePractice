package com.rumilance.practice.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCalculatorTest {

    @Test
    void equalPlayersMoveModestly() {
        SkillRating a = SkillCalculator.starting();
        SkillRating b = SkillCalculator.starting();
        SkillUpdate update = SkillCalculator.apply(a, b, true, false);
        double gain = update.playerA().mu() - a.mu();
        assertTrue(gain > 8.0d && gain < SkillCalculator.MAX_MU_DELTA + 1.0d, "gain was " + gain);
        assertEquals(gain, b.mu() - update.playerB().mu(), 1.0d);
        assertTrue(update.playerA().sigma() < a.sigma());
    }

    @Test
    void farmingMuchWeakerOpponentBarelyMovesMu() {
        SkillRating strong = new SkillRating(1600, 80, 40, 0);
        SkillRating weak = new SkillRating(900, 80, 40, 0);
        SkillUpdate farm = SkillCalculator.apply(strong, weak, true, false);
        double farmGain = farm.playerA().mu() - strong.mu();
        SkillRating equal = new SkillRating(1600, 80, 40, 0);
        SkillUpdate fair = SkillCalculator.apply(equal, new SkillRating(1600, 80, 40, 0), true, false);
        double fairGain = fair.playerA().mu() - equal.mu();
        assertTrue(farmGain < fairGain * 0.30d, "farm=" + farmGain + " fair=" + fairGain);
        assertTrue(farm.farmFactor() < 0.45d);
    }

    @Test
    void farmingDoesNotCollapseSigmaAsFastAsFairWins() {
        SkillRating strong = new SkillRating(1600, 120, 20, 0);
        SkillRating weak = new SkillRating(850, 120, 20, 0);
        SkillUpdate farm = SkillCalculator.apply(strong, weak, true, false);
        SkillUpdate fair = SkillCalculator.apply(
                new SkillRating(1600, 120, 20, 0),
                new SkillRating(1600, 120, 20, 0),
                true,
                false);
        double farmDrop = 120.0d - farm.playerA().sigma();
        double fairDrop = 120.0d - fair.playerA().sigma();
        assertTrue(farmDrop < fairDrop, "farmDrop=" + farmDrop + " fairDrop=" + fairDrop);
    }

    @Test
    void upsetGivesUnderdogALargerJumpThanAFavouriteWin() {
        SkillRating underdog = new SkillRating(900, 200, 5, 0);
        SkillRating favorite = new SkillRating(1400, 80, 40, 0);
        SkillUpdate upset = SkillCalculator.apply(underdog, favorite, true, false);
        SkillRating favouriteTwin = new SkillRating(1400, 80, 40, 0);
        SkillUpdate expectedWin = SkillCalculator.apply(favouriteTwin, new SkillRating(900, 80, 40, 0), true, false);
        double upsetGain = upset.playerA().mu() - underdog.mu();
        double favGain = expectedWin.playerA().mu() - favouriteTwin.mu();
        assertTrue(upsetGain > favGain, "upset=" + upsetGain + " fav=" + favGain);
    }

    @Test
    void winStreakAgainstWeakerDampensFurther() {
        double noStreak = SkillCalculator.farmFactor(0.90d, 0);
        double longStreak = SkillCalculator.farmFactor(0.90d, 6);
        assertTrue(longStreak < noStreak);
        assertTrue(longStreak >= SkillCalculator.MIN_FARM);
    }

    @Test
    void drawLeavesMuNearlySymmetric() {
        SkillRating a = new SkillRating(1100, 120, 10, 2);
        SkillRating b = new SkillRating(1100, 120, 10, 3);
        SkillUpdate update = SkillCalculator.apply(a, b, false, true);
        assertEquals(0, update.playerA().winStreak());
        assertEquals(0, update.playerB().winStreak());
        assertEquals(update.playerA().mu(), update.playerB().mu(), 0.01d);
    }

    @Test
    void displayOrdinalUsesConservativeEstimate() {
        assertEquals(250, SkillCalculator.displayPoints(1000.0d, 250.0d));
        assertEquals(0, SkillCalculator.displayPoints(100.0d, 250.0d));
        assertTrue(SkillCalculator.displayPoints(1600.0d, 60.0d)
                > SkillCalculator.displayPoints(1600.0d, 200.0d));
    }

    @Test
    void muDeltaIsCapped() {
        SkillRating newbie = new SkillRating(1000, 250, 0, 0);
        SkillRating settled = new SkillRating(1000, 55, 80, 0);
        SkillUpdate update = SkillCalculator.apply(newbie, settled, true, false);
        assertTrue(Math.abs(update.playerA().mu() - newbie.mu()) <= SkillCalculator.MAX_MU_DELTA + 0.01d);
    }

    @Test
    void averageSettledPlayerLandsNearHt5Lt5() {
        SkillTier tier = SkillTier.of(1000.0d, 60.0d);
        assertTrue(tier == SkillTier.HT5 || tier == SkillTier.LT5 || tier == SkillTier.LT4,
                "tier was " + tier);
    }

    @Test
    void mythicOrdinalIsHt1() {
        assertEquals(SkillTier.HT1, SkillTier.of(2200.0d, 30.0d));
        assertEquals(SkillTier.LT5, SkillTier.of(SkillCalculator.starting()));
    }
}
