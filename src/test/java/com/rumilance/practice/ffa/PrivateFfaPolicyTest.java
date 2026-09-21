package com.rumilance.practice.ffa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Private / Team FFA — 招待した人だけが入れる FFA と、閉じたあとのクールダウン。
 */
public class PrivateFfaPolicyTest {

    private static final Instant T0 = Instant.parse("2026-09-21T00:00:00Z");

    /** 一度も閉じていなければいつでも開ける。 */
    @Test
    void firstOpeningHasNoCooldown() {
        assertTrue(PrivateFfaPolicy.canOpen(T0, null, null));
        assertTrue(PrivateFfaPolicy.canOpen(null, T0, null));
        assertEquals(0L, PrivateFfaPolicy.remainingSeconds(T0, null, null));
    }

    /** 閉じてからクールダウン中は開けない。残り秒数は切り上げ。 */
    @Test
    void cooldownBlocksReopeningUntilItElapses() {
        Instant closedAt = T0;
        assertFalse(PrivateFfaPolicy.canOpen(closedAt.plusSeconds(30), closedAt, null));
        assertEquals(30L, PrivateFfaPolicy.remainingSeconds(closedAt.plusSeconds(30), closedAt, null));
        // 端数秒は切り上げ(残り 0.5 秒でも 1 秒と案内する)
        assertEquals(1L, PrivateFfaPolicy.remainingSeconds(
                closedAt.plus(PrivateFfaPolicy.CLOSE_COOLDOWN).minusMillis(500), closedAt, null));
        assertFalse(PrivateFfaPolicy.canOpen(
                closedAt.plus(PrivateFfaPolicy.CLOSE_COOLDOWN).minusMillis(1), closedAt, null));
        assertTrue(PrivateFfaPolicy.canOpen(closedAt.plus(PrivateFfaPolicy.CLOSE_COOLDOWN), closedAt, null));
        assertEquals(0L, PrivateFfaPolicy.remainingSeconds(
                closedAt.plus(PrivateFfaPolicy.CLOSE_COOLDOWN), closedAt, null));
    }

    /** クールダウンは上書きでき、0 以下で無効化できる。 */
    @Test
    void cooldownIsConfigurableAndCanBeDisabled() {
        assertTrue(PrivateFfaPolicy.canOpen(T0.plusSeconds(1), T0, Duration.ZERO));
        assertTrue(PrivateFfaPolicy.canOpen(T0.plusSeconds(1), T0, Duration.ofSeconds(-5)));
        assertTrue(PrivateFfaPolicy.canOpen(T0.plusSeconds(5), T0, Duration.ofSeconds(5)));
        assertFalse(PrivateFfaPolicy.canOpen(T0.plusSeconds(5), T0, Duration.ofSeconds(10)));
    }

    /** 公開 FFA は誰でも、非公開は招待された人だけ。 */
    @Test
    void onlyInvitedPlayersMayJoinAPrivateFfa() {
        assertTrue(PrivateFfaPolicy.mayJoin(false, true));
        assertTrue(PrivateFfaPolicy.mayJoin(true, true));
        assertTrue(PrivateFfaPolicy.mayJoin(true, false));
        assertFalse(PrivateFfaPolicy.mayJoin(false, false));
    }

    /** 招待リストは重複と null を落とし、上限で打ち切る。 */
    @Test
    void inviteListIsDeduplicatedAndCapped() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        Set<UUID> invites = PrivateFfaPolicy.normalizeInvites(Arrays.asList(id, id, null));
        assertEquals(1, invites.size());

        List<UUID> many = IntStream.range(0, PrivateFfaPolicy.MAX_INVITES + 10)
                .mapToObj(i -> new UUID(0L, i))
                .collect(Collectors.toList());
        assertEquals(PrivateFfaPolicy.MAX_INVITES, PrivateFfaPolicy.normalizeInvites(many).size());
        assertTrue(PrivateFfaPolicy.normalizeInvites(null).isEmpty());
    }
}
