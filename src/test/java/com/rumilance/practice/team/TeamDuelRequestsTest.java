package com.rumilance.practice.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rumilance.practice.team.TeamDuelRequests.Outcome;
import com.rumilance.practice.team.TeamDuelRequests.Request;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Team Duel Request — Team Owner が相手 Owner に Team vs Team を申し込む流れ。
 */
public class TeamDuelRequestsTest {

    private static final Instant T0 = Instant.parse("2026-09-21T00:00:00Z");
    private static final UUID PARTY_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PARTY_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-00000000001a");

    /** クロックを進められるように、時刻を外から差し込む。 */
    private static final class MutableClock {
        Instant now = T0;
    }

    private static TeamDuelRequests requests(MutableClock clock) {
        return new TeamDuelRequests(() -> clock.now);
    }

    /** 申し込むと相手のオーナー宛に1件ぶら下がる。 */
    @Test
    void challengeBecomesPendingForTheTargetOwner() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);

        assertEquals(Outcome.SENT, requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B));
        Optional<Request> pending = requests.pendingFor(PARTY_B);
        assertTrue(pending.isPresent());
        assertEquals(PARTY_A, pending.get().fromParty());
        assertEquals("Crimson", pending.get().fromPartyName());
        assertEquals(1, requests.pendingCount());
    }

    /** 同じ相手への重複申し込みは弾く。自分自身への申し込みも不可。 */
    @Test
    void duplicateAndSelfChallengesAreRefused() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);

        assertEquals(Outcome.SENT, requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B));
        assertEquals(Outcome.ALREADY_PENDING, requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B));
        assertEquals(Outcome.SELF, requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_A));
        assertEquals(Outcome.INVALID, requests.send(null, "Crimson", OWNER_A, PARTY_B));
        assertEquals(1, requests.pendingCount());
    }

    /** Accept は1回きり(取り出して消える)。Deny も消す。 */
    @Test
    void acceptConsumesAndDenyDiscards() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);
        requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B);

        assertTrue(requests.accept(PARTY_B).isPresent());
        assertTrue(requests.pendingFor(PARTY_B).isEmpty());
        assertTrue(requests.accept(PARTY_B).isEmpty());

        requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B);
        assertTrue(requests.deny(PARTY_B));
        assertFalse(requests.deny(PARTY_B));
        assertEquals(0, requests.pendingCount());
    }

    /** 別パーティーからの申し込みは上書きされる(オーナーの選択は常に1件)。 */
    @Test
    void newerChallengeReplacesTheOldOne() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);
        UUID partyC = UUID.fromString("00000000-0000-0000-0000-00000000000c");

        requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B);
        assertEquals(Outcome.SENT, requests.send(partyC, "Azure", OWNER_A, PARTY_B));
        assertEquals(1, requests.pendingCount());
        assertEquals(partyC, requests.pendingFor(PARTY_B).orElseThrow().fromParty());
    }

    /** TTL を過ぎたら自動で消える。 */
    @Test
    void challengeExpiresAfterItsTtl() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);
        requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B);

        clock.now = T0.plus(TeamDuelRequests.TTL).minus(Duration.ofSeconds(1));
        assertTrue(requests.pendingFor(PARTY_B).isPresent());

        clock.now = T0.plus(TeamDuelRequests.TTL).plus(Duration.ofSeconds(1));
        assertTrue(requests.pendingFor(PARTY_B).isEmpty());
        assertTrue(requests.accept(PARTY_B).isEmpty());
        assertEquals(0, requests.pendingCount());
    }

    /** 解散したパーティーが出した申し込みはまとめて取り消せる。 */
    @Test
    void disbandingCancelsOutgoingChallenges() {
        MutableClock clock = new MutableClock();
        TeamDuelRequests requests = requests(clock);
        UUID partyC = UUID.fromString("00000000-0000-0000-0000-00000000000c");
        requests.send(PARTY_A, "Crimson", OWNER_A, PARTY_B);
        requests.send(PARTY_A, "Crimson", OWNER_A, partyC);

        assertEquals(2, requests.cancelFrom(PARTY_A));
        assertEquals(0, requests.pendingCount());
        assertEquals(0, requests.cancelFrom(PARTY_A));
    }
}
