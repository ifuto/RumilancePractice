package com.rumilance.practice.duel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelIdsTest {

    @TempDir
    Path temp;

    @Test
    void fiveCharRoundTripIsCaseSensitive() {
        assertEquals("00000", DuelIds.encode(0));
        assertEquals("0000A", DuelIds.encode(10));
        assertEquals(10L, DuelIds.decode("0000A"));
        assertNotEquals(DuelIds.decode("0000A"), DuelIds.decode("0000a"));
        assertEquals(5, DuelIds.encode(62 * 62).length());
        assertTrue(DuelIds.valid("aZ9kQ"));
    }

    @Test
    void packedFileFindsById() {
        DuelLogStore store = new DuelLogStore(temp.resolve("duels.rpd"));
        String id = store.append("Alice", "Bob");
        assertEquals(5, id.length());
        DuelLogStore.Entry entry = store.find(id).orElseThrow();
        assertEquals("Alice", entry.player1());
        assertEquals("Bob", entry.player2());
        assertTrue(store.find("00000").isEmpty() || store.find("00000").orElseThrow().id().equals(id));
    }
}
