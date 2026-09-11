package com.rumilance.practice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KitDeltaPlannerTest {

    @Test
    void untouchedLayoutProducesNoOps() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X", "Y"}, new String[]{"X", "Y"});
        assertArrayEquals(new String[]{null, null}, ops);
    }

    @Test
    void foreignItemMarksSlotF() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"POTION"}, new String[]{"X"});
        assertArrayEquals(new String[]{"F0"}, ops);
    }

    @Test
    void droppedDefaultMarksSlotE() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X", null}, new String[]{"X", "Y"});
        assertArrayEquals(new String[]{null, "E1"}, ops);
    }

    @Test
    void plainSwapProducesTwoMoves() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"Y", "X"}, new String[]{"X", "Y"});
        assertArrayEquals(new String[]{"M1>0", "M0>1"}, ops);
    }

    /** The theft regression: identical duplicate default stacks, one moved elsewhere. */
    @Test
    void duplicateDefaultsKeptPlusSiblingMovedDoesNotStealKeptDefault() {
        // defaults: X in slot 0 and slot 1. Layout: slot 0 keeps an X, slot 1 dropped,
        // slot 2 gained the moved X. The move source must be slot 1, NOT kept slot 0.
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X", null, "X"}, new String[]{"X", "X", null});
        assertNull(ops[0]);
        assertNull(ops[1]);
        org.junit.jupiter.api.Assertions.assertEquals("M1>2", ops[2]);
    }

    /** Same shape with the move target BEFORE the kept duplicate: scan order must not steal. */
    @Test
    void duplicateDefaultsMoveTargetKeepsSiblingSource() {
        // defaults: X in slots 1 and 2. Layout: slot 0 holds the moved X, slot 1 keeps an X,
        // slot 2 dropped. Keep-claims run before moves, so slot 0's move source is slot 2.
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X", "X", null}, new String[]{null, "X", "X"});
        org.junit.jupiter.api.Assertions.assertEquals("M2>0", ops[0]);
        assertNull(ops[1]);
        assertNull(ops[2]);
    }

    @Test
    void droppedDuplicateStillEmptiesWhenNothingMoved() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X", null}, new String[]{"X", "X"});
        assertArrayEquals(new String[]{null, "E1"}, ops);
    }

    @Test
    void shorterLayoutNullsOutTrailingDefaults() {
        String[] ops = KitDeltaPlanner.plan(
                new String[]{"X"}, new String[]{"X", "Y"});
        assertArrayEquals(new String[]{null, "E1"}, ops);
    }
}
