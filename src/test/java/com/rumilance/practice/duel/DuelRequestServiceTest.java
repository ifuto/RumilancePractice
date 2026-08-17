package com.rumilance.practice.duel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelRequestServiceTest {

    @Test
    void expiredRequestCannotBeAccepted() throws InterruptedException {
        DuelRequestService service = new DuelRequestService(1L, 0L);
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var request = service.create(sender, target, "nodebuff", true, 1).orElseThrow();
        Thread.sleep(1100L);
        assertFalse(service.accept(request.id()));
    }

    @Test
    void invalidateClearsOutgoingAndIncoming() {
        DuelRequestService service = new DuelRequestService(60L, 0L);
        UUID sender = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        service.create(sender, target, "nodebuff", false, 1);
        service.invalidateForPlayer(sender);
        assertTrue(service.latestForTarget(target).isEmpty());
    }
}
