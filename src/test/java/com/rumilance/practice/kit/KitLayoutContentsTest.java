package com.rumilance.practice.kit;

import com.rumilance.practice.guard.PracticeGuards;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLayoutContentsTest {

    @Test
    void retainOrLoadKeepsSessionLayoutInsteadOfReloading() {
        ItemStack[] session = new ItemStack[41];
        ItemStack[] loaded = new ItemStack[41];
        assertSame(session, KitLayoutContents.retainOrLoad(session, loaded));
    }

    @Test
    void retainOrLoadFallsBackWhenSessionMissingOrShort() {
        ItemStack[] loaded = new ItemStack[41];
        assertSame(loaded, KitLayoutContents.retainOrLoad(null, loaded));
        assertSame(loaded, KitLayoutContents.retainOrLoad(new ItemStack[8], loaded));
        assertSame(loaded, KitLayoutContents.retainOrLoad(new ItemStack[40], loaded));
    }

    @Test
    void stripPlaceholdersNoopsOnNullAndEmpty() {
        KitLayoutContents.stripPlaceholders(null);
        KitLayoutContents.stripPlaceholders(new ItemStack[0]);
        ItemStack[] slots = new ItemStack[2];
        KitLayoutContents.stripPlaceholders(slots);
        assertSame(null, slots[0]);
        assertSame(null, slots[1]);
    }

    @Test
    void placeholderNullIsNotPlaceholder() {
        assertFalse(KitLayoutContents.isPlaceholder(null));
    }

    @Test
    void sameContentsTreatsAllNullAsEqual() {
        ItemStack[] a = new ItemStack[41];
        ItemStack[] b = new ItemStack[41];
        assertTrue(KitLayoutContents.sameContents(a, b));
    }

    @Test
    void sameContentsNullArraysBothEmpty() {
        assertTrue(KitLayoutContents.sameContents(null, null));
    }

    @Test
    void guardsKitLayoutUnchangedEmptyBaseline() {
        ItemStack[] baseline = new ItemStack[41];
        ItemStack[] edited = new ItemStack[41];
        assertTrue(PracticeGuards.kitLayoutUnchanged(baseline, edited));
    }
}
