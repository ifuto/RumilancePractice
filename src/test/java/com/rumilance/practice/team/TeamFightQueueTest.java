package com.rumilance.practice.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rumilance.practice.team.TeamFightQueue.Match;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Team Fight Queue — 自チーム vs ランダムな相手チーム の組み合わせ規則。
 */
public class TeamFightQueueTest {

    private static final Instant T0 = Instant.parse("2026-09-21T00:00:00Z");
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static TeamFightQueue queue(Random random) {
        return new TeamFightQueue(() -> T0, random);
    }

    /** 2組揃ったら試合が成立し、両方とも待ち行列から消える。 */
    @Test
    void twoPartiesMatchAndLeaveTheQueue() {
        TeamFightQueue q = queue(new Random(1));
        assertTrue(q.enqueue(A, 4));
        assertTrue(q.enqueue(B, 4));
        assertEquals(2, q.waitingCount());

        Optional<Match> match = q.pollMatch();
        assertTrue(match.isPresent());
        assertEquals(Set.of(A, B), Set.of(match.get().partyA(), match.get().partyB()));
        assertEquals(0, q.waitingCount());
        assertTrue(q.pollMatch().isEmpty());
    }

    /** 同じパーティーは二重に並ばない(再登録は列の後ろに置き直す)。 */
    @Test
    void requeueReplacesThePreviousEntry() {
        TeamFightQueue q = queue(new Random(1));
        assertTrue(q.enqueue(A, 4));
        assertFalse(q.enqueue(A, 6));
        assertEquals(1, q.waitingCount());
        assertEquals(6, q.snapshot().get(0).size());
    }

    /** 人数差が許容を超えたら組ませない。 */
    @Test
    void sizeDifferenceBeyondToleranceDoesNotMatch() {
        TeamFightQueue q = queue(new Random(1));
        q.enqueue(A, 2);
        q.enqueue(B, 2 + TeamFightQueue.SIZE_TOLERANCE + 1);
        assertTrue(q.pollMatch().isEmpty());
        assertEquals(2, q.waitingCount());
    }

    /** 組み合わせ不可能な組が並んでも無限ループせず、全員が待ち続ける。 */
    @Test
    void incompatiblePartiesStayQueuedWithoutSpinning() {
        TeamFightQueue q = queue(new Random(1));
        q.enqueue(A, 1);
        q.enqueue(B, 20);
        q.enqueue(C, 40);
        assertTrue(q.pollMatch().isEmpty());
        assertEquals(3, q.waitingCount());
    }

    /** 相手は候補からランダムに引く(抽選結果を差し替えて両方の分岐を確認)。 */
    @Test
    void opponentIsDrawnAtRandom() {
        Set<UUID> opponents = new HashSet<>();
        // new Random(seed) の nextInt(2) は小さいシードだと最上位ビットが変わらず全部同じ値に
        // なるので、抽選そのものを差し替えて検証する。
        for (int pick : new int[] {0, 1}) {
            TeamFightQueue q = queue(new ScriptedRandom(pick));
            q.enqueue(A, 4);
            q.enqueue(B, 4);
            q.enqueue(C, 4);
            Match match = q.pollMatch().orElseThrow();
            opponents.add(match.partyA().equals(A) ? match.partyB() : match.partyA());
            // 1組成立したら3人目は残る
            assertEquals(1, q.waitingCount());
        }
        assertEquals(Set.of(B, C), opponents, "both waiting parties must be drawable");
    }

    /** 抽選の戻り値を固定できる Random(実装の分岐だけを確かめるための差し替え)。 */
    private static final class ScriptedRandom extends Random {
        private final int pick;

        ScriptedRandom(int pick) {
            this.pick = pick;
        }

        @Override
        public int nextInt(int bound) {
            return ((pick % bound) + bound) % bound;
        }
    }

    /** キャンセルしたら並んでいない扱い。 */
    @Test
    void cancelRemovesTheParty() {
        TeamFightQueue q = queue(new Random(1));
        q.enqueue(A, 4);
        assertTrue(q.isQueued(A));
        assertTrue(q.cancel(A));
        assertFalse(q.isQueued(A));
        assertFalse(q.cancel(A));
    }

    /** 放置されたエントリーは掃除される。 */
    @Test
    void staleEntriesAreSwept() {
        TeamFightQueue q = new TeamFightQueue(() -> T0, new Random(1));
        q.enqueue(A, 4);
        assertEquals(0, q.sweep(T0.plus(TeamFightQueue.STALE_AFTER)));
        assertEquals(1, q.waitingCount());
        assertEquals(1, q.sweep(T0.plus(TeamFightQueue.STALE_AFTER).plus(Duration.ofSeconds(1))));
        assertEquals(0, q.waitingCount());
    }

    /** null や自分同士は弾く。 */
    @Test
    void nullAndSelfAreRejected() {
        TeamFightQueue q = queue(new Random(1));
        assertFalse(q.enqueue(null, 4));
        assertFalse(q.cancel(null));
        assertFalse(q.isQueued(null));
        assertFalse(TeamFightQueue.isSane(new Match(A, A)));
        assertTrue(TeamFightQueue.isSane(new Match(A, B)));
    }
}
