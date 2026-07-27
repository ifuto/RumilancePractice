package com.rumilance.practice.arena;

import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.ArenaType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaSlotAllocatorTest {

    @Test
    void twoMatchesCannotReserveSameInstance() {
        SimpleArenaService service = new SimpleArenaService();
        ArenaTemplate template = new ArenaTemplate(
                UUID.randomUUID(), "pvp", ArenaType.DUEL, ArenaTerrain.FLAT, "world",
                0, 64, 0, 20, 80, 20,
                "world;0.5;65;0.5;0;0", "world;10.5;65;10.5;180;0", "", true
        );
        service.setTemplates(List.of(template));

        UUID match1 = UUID.randomUUID();
        UUID match2 = UUID.randomUUID();
        var first = service.reserve(ArenaType.DUEL, ArenaTerrain.FLAT, match1).join();
        var second = service.reserve(ArenaType.DUEL, ArenaTerrain.FLAT, match2).join();

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
    }

    @Test
    void rematchUsesDifferentInstanceWhenMultipleTemplatesExist() {
        SimpleArenaService service = new SimpleArenaService();
        ArenaTemplate a = new ArenaTemplate(
                UUID.randomUUID(), "pvp1", ArenaType.DUEL, ArenaTerrain.ANY, "world",
                0, 64, 0, 10, 80, 10,
                "world;0.5;65;0.5;0;0", "world;5.5;65;5.5;180;0", "", true
        );
        ArenaTemplate b = new ArenaTemplate(
                UUID.randomUUID(), "pvp2", ArenaType.DUEL, ArenaTerrain.ANY, "world",
                100, 64, 100, 110, 80, 110,
                "world;100.5;65;100.5;0;0", "world;105.5;65;105.5;180;0", "", true
        );
        service.setTemplates(List.of(a, b));

        var first = service.reserve(ArenaType.DUEL, ArenaTerrain.ANY, UUID.randomUUID()).join().orElseThrow();
        var second = service.reserve(ArenaType.DUEL, ArenaTerrain.ANY, UUID.randomUUID()).join().orElseThrow();
        assertNotEquals(first.id(), second.id());
        assertEquals(2, service.templates().size());
    }
}
