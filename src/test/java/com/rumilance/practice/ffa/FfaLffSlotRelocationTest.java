package com.rumilance.practice.ffa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Where the item that used to sit in the LFF slot gets moved to.
 *
 * <p>The LFF toggle owns hotbar slot 9 (index 8). A player's own edited kit may legitimately use
 * that slot, and simply overwriting it would destroy their item — so it is relocated instead.
 * The order in which slots are tried is the whole point of the feature, which is why it is
 * pinned down here rather than only in the Bukkit-dependent code path.</p>
 *
 * <p>Pure array logic — {@code ItemStack} needs a running server, so only "occupied or not" is
 * under test.</p>
 */
final class FfaLffSlotRelocationTest {

    private static boolean[] occupied(int... slots) {
        boolean[] grid = new boolean[36];
        for (int slot : slots) {
            grid[slot] = true;
        }
        return grid;
    }

    @Test
    void anEmptyInventoryOffersTheFirstHotbarSlot() {
        assertEquals(0, FfaLookingForFight.firstFreeSlot(new boolean[36]));
    }

    @Test
    void hotbarSlotsComeFirstInOrder() {
        assertEquals(1, FfaLookingForFight.firstFreeSlot(occupied(0)));
        assertEquals(3, FfaLookingForFight.firstFreeSlot(occupied(0, 1, 2)));
        assertEquals(7, FfaLookingForFight.firstFreeSlot(occupied(0, 1, 2, 3, 4, 5, 6)));
    }

    @Test
    void theLffSlotItselfIsNeverOfferedAsSomewhereToMoveTo() {
        // Slot 8 is the one being taken over, so it must not be handed back as free space.
        assertEquals(0, FfaLookingForFight.firstFreeSlot(occupied(8)));
        // ... and when it is the ONLY gap, the search must still refuse it and report "no room".
        boolean[] onlyEightFree = new boolean[36];
        for (int slot = 0; slot < 36; slot++) {
            onlyEightFree[slot] = true;
        }
        onlyEightFree[FfaLookingForFight.SLOT] = false;
        assertEquals(-1, FfaLookingForFight.firstFreeSlot(onlyEightFree));
    }

    @Test
    void storageIsUsedOnceTheWholeHotbarIsFull() {
        assertEquals(9, FfaLookingForFight.firstFreeSlot(occupied(0, 1, 2, 3, 4, 5, 6, 7)));
        assertEquals(11, FfaLookingForFight.firstFreeSlot(
                occupied(0, 1, 2, 3, 4, 5, 6, 7, 9, 10)));
    }

    @Test
    void aFullStorageRowStillFallsBackToTheHotbarWhenItHasRoom() {
        // Storage being full must not stop an empty hotbar slot from being used.
        boolean[] grid = new boolean[36];
        for (int slot = 9; slot < 36; slot++) {
            grid[slot] = true;
        }
        assertEquals(0, FfaLookingForFight.firstFreeSlot(grid));
    }

    @Test
    void aCompletelyFullInventoryReportsNoRoom() {
        boolean[] grid = new boolean[36];
        for (int slot = 0; slot < 36; slot++) {
            grid[slot] = true;
        }
        // -1 is the caller's signal to drop the item rather than shuffle it.
        assertEquals(-1, FfaLookingForFight.firstFreeSlot(grid));
    }

    @Test
    void aMissingOrShortInventoryNeverThrows() {
        assertEquals(-1, FfaLookingForFight.firstFreeSlot(null));
        assertEquals(-1, FfaLookingForFight.firstFreeSlot(new boolean[0]));
        assertEquals(2, FfaLookingForFight.firstFreeSlot(occupied(0, 1)));
    }

    @Test
    void theLffSlotIsTheNinthHotbarSlot() {
        assertEquals(8, FfaLookingForFight.SLOT);
    }
}
