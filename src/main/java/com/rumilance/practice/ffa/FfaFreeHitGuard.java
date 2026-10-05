package com.rumilance.practice.ffa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FreeHit 対策（相手が準備していないのに殴り始める行為への対策）の状態機械。
 *
 * <p>アリーナ単位の {@code settings.freehit-guard} が ON のときだけ使われる。純粋な状態だけを
 * 持ち、音やメッセージは呼び出し側（{@code FfaListener}）が出す — ルールをサーバーなしで検証
 * できるようにするため。
 *
 * <h3>状態</h3>
 * <ul>
 *   <li><b>仮Combat</b>（{@link #PROVISIONAL_MS} = 10 秒、非表示）— 誰かを殴ると「攻撃者 → 相手」
 *       の向きで付く。この間に殴り返されると本Combat になる。抜けてもペナルティは無い。</li>
 *   <li><b>本Combat</b>（{@link #COMBAT_IDLE_MS} = 30 秒）— 殴り返しで成立した2人組。最後の攻撃
 *       から 30 秒、または片方の死亡・退出まで続く。この間は相手以外と一切戦えない。</li>
 * </ul>
 *
 * <h3>判定</h3>
 * <ul>
 *   <li>2人とも本Combat 中で互いが相手 → {@link Verdict#ALLOW}（通常どおりダメージ・KB）。</li>
 *   <li>どちらかが本Combat 中だが相手ではない → {@link Verdict#BLOCKED}
 *       （ダメージ・KB・仮Combat すべて無し）。</li>
 *   <li>どちらも本Combat 中でない → {@link Verdict#FREE_HIT}
 *       （ダメージ・KB は入らないがヒット音などは通常どおり。仮Combat を付ける）。</li>
 * </ul>
 */
public final class FfaFreeHitGuard {

    /** 仮Combat の寿命: この時間内に相手から殴り返されると本Combat が成立する。 */
    public static final long PROVISIONAL_MS = 10_000L;

    /** 本Combat の寿命: 最後に攻撃が当たってからこの時間で自然終了する。 */
    public static final long COMBAT_IDLE_MS = 30_000L;

    /** 攻撃1回の判定。 */
    public enum Verdict {
        /** 本Combat 中の正規の攻撃。通常どおりダメージ・KB・Combat 判定が入る。 */
        ALLOW,
        /** 誰も Combat 中でない。ダメージは入らないが仮Combat を付ける。 */
        FREE_HIT,
        /** どちらかが別の相手と本Combat 中。何も起きない。 */
        BLOCKED
    }

    /** 本Combat の相手。双方向に入る。 */
    private final Map<UUID, UUID> partner = new ConcurrentHashMap<>();
    /** 本Combat で最後に攻撃が当たった時刻（ミリ秒）。これが両方の残り時間を決める。 */
    private final Map<UUID, Long> lastHitMillis = new ConcurrentHashMap<>();
    /** 仮Combat: 攻撃者 → (相手 → 失効時刻)。 */
    private final Map<UUID, Map<UUID, Long>> provisional = new ConcurrentHashMap<>();

    /**
     * 攻撃が当たった瞬間の判定。期限切れの状態はこの中で掃除される。
     *
     * @param attacker 攻撃したプレイヤー
     * @param victim   攻撃されたプレイヤー
     * @param now      現在時刻（{@code System.currentTimeMillis()}）
     */
    public Verdict evaluate(UUID attacker, UUID victim, long now) {
        if (attacker.equals(victim)) {
            return Verdict.ALLOW;
        }
        expire(now);
        UUID attackerPartner = partner.get(attacker);
        UUID victimPartner = partner.get(victim);
        boolean pairedWithEachOther = victim.equals(attackerPartner) && attacker.equals(victimPartner);
        if (pairedWithEachOther) {
            return Verdict.ALLOW;
        }
        if (attackerPartner != null || victimPartner != null) {
            return Verdict.BLOCKED;
        }
        return Verdict.FREE_HIT;
    }

    /**
     * 仮Combat を記録する。相手が自分に向けた仮Combat をまだ持っている（＝この10秒以内に
     * 相手から殴られていた）なら本Combat を成立させ、{@code true} を返す。
     */
    public boolean registerFreeHit(UUID attacker, UUID victim, long now) {
        provisional.computeIfAbsent(attacker, key -> new ConcurrentHashMap<>())
                .put(victim, now + PROVISIONAL_MS);
        Map<UUID, Long> incoming = provisional.get(victim);
        if (incoming != null) {
            Long until = incoming.get(attacker);
            if (until != null && until > now) {
                startCombat(attacker, victim, now);
                return true;
            }
        }
        return false;
    }

    /** 本Combat 中に攻撃が当たった: 30 秒のカウントを更新する。 */
    public void noteLandedHit(UUID attacker, UUID victim, long now) {
        lastHitMillis.put(attacker, now);
        lastHitMillis.put(victim, now);
    }

    /** 死亡・退出などでそのプレイヤーの Combat を解消する。相手も解放される。 */
    public void clear(UUID player) {
        UUID other = partner.remove(player);
        if (other != null) {
            partner.remove(other);
            lastHitMillis.remove(other);
        }
        lastHitMillis.remove(player);
        provisional.remove(player);
        // 自分が抱えている仮Combat だけでなく、他の誰かが自分に向けて持っている分も消す。
        for (Map<UUID, Long> held : provisional.values()) {
            held.remove(player);
        }
    }

    /** 期限切れの本Combat・仮Combat を掃除する。 */
    public void expire(long now) {
        for (UUID player : new ArrayList<>(partner.keySet())) {
            long last = lastHitMillis.getOrDefault(player, 0L);
            if (now - last > COMBAT_IDLE_MS) {
                clear(player);
            }
        }
        for (UUID attacker : new ArrayList<>(provisional.keySet())) {
            Map<UUID, Long> held = provisional.get(attacker);
            if (held == null) {
                continue;
            }
            held.values().removeIf(until -> until <= now);
            if (held.isEmpty()) {
                provisional.remove(attacker);
            }
        }
    }

    private void startCombat(UUID first, UUID second, long now) {
        partner.put(first, second);
        partner.put(second, first);
        noteLandedHit(first, second, now);
        // 成立した2人の仮Combat は不要になる（以降は本Combat が判定を握る）。
        provisional.remove(first);
        provisional.remove(second);
    }

    /** 検証用: 現在の本Combat の組。 */
    public List<UUID> playersInCombat() {
        return new ArrayList<>(partner.keySet());
    }

    /** 検証用: {@code player} が 本Combat 中に狙える相手。居なければ null。 */
    public UUID partnerOf(UUID player, long now) {
        expire(now);
        return partner.get(player);
    }
}
