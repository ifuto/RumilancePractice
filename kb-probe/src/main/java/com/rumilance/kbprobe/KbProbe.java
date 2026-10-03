package com.rumilance.kbprobe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KB Probe — 他サーバーのノックバック係数をクライアント側で実測する中核ロジック。
 *
 * <p><b>測定原理:</b> 自分が殴った対象について、サーバーが周囲へブロードキャストする
 * 速度パケット（EntityVelocityUpdateS2CPacket）を捕捉し、「前回パケット値との差分」=
 * そのヒットで実際に適用された押し出し速度として記録する。バニラ基準値
 * （水平 0.4 + 攻撃側加算、接地時の垂直 0.4）との比を推定係数として表示する。</p>
 *
 * <p><b>不正KBを計らないためのガード（本Modの核心）:</b></p>
 * <ul>
 *   <li>攻撃後にダメージ成立パケット（EntityDamageS2CPacket）が来ない → 保護領域/無敵。
 *       「ヒットしなかった殴り」は一切計測に使わない。</li>
 *   <li>ダメージは成立したのに速度パケットが一定 tick 来ない → KB無効領域（ロビー等）と
 *       判定し、係数 0 などの誤データは記録せず警告のみ表示する。</li>
 *   <li>相手が無敵時間中（hurtTime &gt; 0）なら殴り自体を保留しない。</li>
 *   <li>押し出し方向が攻撃方向と大きく逆 / 異常な大きさの速度はノイズ・外れ値として除外。</li>
 *   <li>垂直係数は「殴った瞬間に相手が接地していた」サンプルのみで評価（空中はバニラで
 *       Y が変化しないため係数を逆算できない）。</li>
 *   <li>盾ブロッキング中・クリエイティブ/スペクテイターの対象、メイスのスマッシュ攻撃は
 *       保留ヒット自体を作らない（いずれもバニラの melee KB モデルの外にある）。</li>
 *   <li>同一対象へのダメージパケットが窓内に 2 回以上来たら第三者の同時攻撃（合成KB）の
 *       混入と判断して除外する。</li>
 *   <li>速度パケットがダメージ確認より先着した場合（パケット順序逆転）は、窓切れ時に
 *       「KB無効領域」誤判定せず静かに破棄する。</li>
 *   <li>垂直係数は「速度パケット到着時点でも接地」かつ推定係数がバニラ上あり得る帯
 *       （0.05 〜 8.0 倍）のときだけ採用する（攻撃〜ヒット成立の間のジャンプで Y 不変に
 *       なった偽サンプルを防ぐ）。</li>
 *   <li>対象が窓内にテレポート/除去（kill, respawn, unload, 切断）されたら静かに破棄する
 *       （「KB無効領域」の偽陽性を防ぐ）。</li>
 * </ul>
 */
public final class KbProbe {

    // ---- 時間窓（client tick = 1/20s） -----------------------------------------------
    /** 攻撃 → ダメージ成立パケットの許容遅延。 */
    private static final long CONFIRM_WINDOW = 12;
    /** ダメージ成立 →（KB 相当の）速度パケットの許容遅延。 */
    private static final long MOTION_WINDOW = 12;
    /** 保留エントリの寿命。 */
    private static final long PENDING_TTL = 25;
    /** 同一対象への連続サンプリング抑制（hold-attack 連打対策）。 */
    private static final long PER_TARGET_COOLDOWN = 20;
    /** 「攻撃無効」「KB無効」通知の再表示間隔（30 秒）。 */
    private static final long NOTICE_COOLDOWN = 600;

    // ---- 測定基準: 定数と期待値式は KbProbeMath に集約（ゲーム外シミュレーションと同一実装） ----

    private static long clientTick;

    private static final class PendingHit {
        final long hitTick;
        long confirmTick = -1L;
        boolean sampled;
        /** 保留中に届いた対象へのダメージパケット数（自撃+第三者）。2 以上で混戦混入と判定。 */
        int damagePackets;
        /** 第三者の同時攻撃（合成KB）で汚染されたサンプルは係数推定に使わない。 */
        boolean contaminated;
        /** 速度パケットがダメージ確認より先着した（順序逆転）。KB無効領域とは区別して静かに破棄。 */
        boolean velocityBeforeConfirm;
        // --- 先着速度の保存（順序逆転時でもサンプルを記録できるようにする） ---
        /** 先着した速度パケットの速度差分（KB インパルス）。 */
        double earlyDx, earlyDy, earlyDz;
        /** 先着速度受信時のエンティティ速度（isIdle 判定用）。 */
        double earlyCurX, earlyCurY, earlyCurZ;
        /** 先着速度受信時の tick。 */
        long earlyVelocityTick;
        /** 先着速度パケットのエンティティ ID。 */
        int earlyEntityId;
        final UUID victimUuid;
        final double dirX, dirZ;
        final boolean targetOnGround;
        /** 疾走ヒット（vanilla 1.21.1: チャージ率のゲートは無く、疾走中なら +1.0 ノックバックレベル）。 */
        final boolean sprintHit;
        /** 攻撃者側の attack_knockback 属性値（Knockbackエンチャ等 = 武器属性なので含まれる）。 */
        final double attackKnockback;
        final double resistance;
        /** 読み取れた対象の装備概要（エンチャント込み）。空文字 = 装備なし/非表示。 */
        final String gearSummary;

        PendingHit(long hitTick, UUID victimUuid, double dirX, double dirZ, boolean targetOnGround,
                   boolean sprintHit, double attackKnockback, double resistance, String gearSummary) {
            this.hitTick = hitTick;
            this.victimUuid = victimUuid;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.targetOnGround = targetOnGround;
            this.sprintHit = sprintHit;
            this.attackKnockback = attackKnockback;
            this.resistance = resistance;
            this.gearSummary = gearSummary;
        }

        /** vanilla の攻撃ノックバックレベル k = 属性 + (疾走ヒット 1.0)。 */
        double knockbackLevel() {
            return attackKnockback + (sprintHit ? 1.0d : 0.0d);
        }
    }

    /** 装備解析の結果: 実装備の属性コンポーネントから算出した耐衝撃 + 表示用サマリ。 */
    private record GearInfo(double resistance, String summary) {
    }

    /** 属性のレジストリID（汎用耐衝撃）。1.21.11 では GENERIC_ プレフィックスが削除。 */
    private static final String KNOCKBACK_RESISTANCE_ID = "minecraft:knockback_resistance";

    /** entityId → 保留中の自前ヒット。 */
    private static final Map<Integer, PendingHit> PENDING = new HashMap<>();
    /** entityId → 最後にサンプルを採った tick。 */
    private static final Map<Integer, Long> LAST_SAMPLED = new HashMap<>();
    /** 装備概要を表示済みの対象（ワールド内で1回だけ出すスパム防止）。 */
    private static final Set<UUID> GEAR_ANNOUNCED = new HashSet<>();
    /** 通知クールダウン（サーバー単位・種別単位）。
     *  初回通知が必ず出るよう -NOTICE_COOLDOWN 初期化する。
     *  （Long.MIN_VALUE 初期値は差分がオーバーフローして負になり、クールダウン判定が
     *    常に true = 全通知が永久ミュートになる — 0.3.1 の潜伏バグ。） */
    private static long lastNoKbNotice = -NOTICE_COOLDOWN;
    private static long lastNoDamageNotice = -NOTICE_COOLDOWN;

    private static ClientWorld lastWorld;

    private KbProbe() {
    }

    // ----------------------------------------------------------------------------------
    // Mixin からのイベント入口
    // ----------------------------------------------------------------------------------

    /** ClientPlayerInteractionManager#attackEntity の HEAD から呼ばれる。 */
    public static void onAttack(PlayerEntity attacker, Entity target) {
        if (!(attacker instanceof ClientPlayerEntity me) || !(target instanceof PlayerEntity victim)) {
            return; // 自分の手での攻撃 × プレイヤー相手のみ（mob/アーマースタンドは属性が違う）
        }
        if (target == me || PENDING.containsKey(victim.getId())) {
            return;
        }
        Long last = LAST_SAMPLED.get(victim.getId());
        if (last != null && clientTick - last < PER_TARGET_COOLDOWN) {
            return;
        }
        // 無敵時間中の相手を殴ってもダメージ成立しないので、そもそも保留に入れない
        if (victim.hurtTime > 0) {
            return;
        }
        // 盾ブロッキング中: 完全ブロックではダメージパケット自体が来ず「保護領域」偽警告になる。
        // （状態は entity metadata でクライアント同期済み）
        if (victim.isBlocking()) {
            return;
        }
        // クリエイティブ/スペクテイター: 虚空/kill 以外でダメージが成立しない。他プレイヤーの
        // abilities は同期されないため PlayerListEntry の GameMode で判定する。
        if (isSpectatorOrCreative(victim)) {
            return;
        }
        // メイスのスマッシュ攻撃: 落下距離由来の追加上向き速度はバニラ melee KB モデルの外。
        if (me.fallDistance > 1.5f && me.getMainHandStack().isOf(Items.MACE)) {
            return;
        }
        // 押し出し方向は「攻撃者 → 被害者」（vanilla 1.21.1 の damage(): d=src.getX()-this.getX()
        // を takeKnockback し内部で減算 → 被害者は攻撃者から遠ざかる方向へ飛ぶ）
        double dx = victim.getX() - me.getX();
        double dz = victim.getZ() - me.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-4) {
            return; // 完全に重なっている場合は方向定義不能（vanilla はランダム退避）
        }
        double attackKb = me.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK);
        // vanilla 1.21.1 実装（ソース検証済）: 疾走ブーにはチャージ率>0.9 のゲートは無い。
        // 生粋の疾走ヒット = 疾走中に attack → ノックバックレベルに +1.0 で合成される。
        boolean sprintHit = me.isSprinting();
        GearInfo gear = analyzeGear(victim);
        PendingHit hit = new PendingHit(clientTick, victim.getUuid(), dx / len, dz / len,
                victim.isOnGround(), sprintHit, attackKb, gear.resistance(), gear.summary());
        PENDING.put(victim.getId(), hit);
    }

    /** ClientPlayNetworkHandler#onEntityDamage の TAIL から呼ばれる: ダメージ成立の確認。 */
    public static void onDamageConfirmed(int entityId) {
        PendingHit hit = PENDING.get(entityId);
        if (hit == null) {
            return;
        }
        // EntityDamageS2CPacket は成因を問わず同一パケット。窓内に 2 発目以上が届く = 第三者
        // （または環境）も対象に介入しており、その後の速度は合成値なので係数推定に使えない。
        hit.damagePackets++;
        if (hit.damagePackets > 1) {
            hit.contaminated = true;
        }
        if (hit.confirmTick < 0L && clientTick - hit.hitTick <= CONFIRM_WINDOW) {
            hit.confirmTick = clientTick;
            // 速度パケットが先着していた場合、今すぐサンプルを記録する
            if (hit.velocityBeforeConfirm && !hit.sampled) {
                hit.sampled = true;
                // 先着時のエンティティ速度（isIdle 判定用）と差分を使用
                Vec3d earlyCur = new Vec3d(hit.earlyCurX, hit.earlyCurY, hit.earlyCurZ);
                recordSample(hit, hit.earlyEntityId, null,
                        earlyCur, hit.earlyDx, hit.earlyDy, hit.earlyDz);
            }
        }
    }

    /**
     * ClientPlayNetworkHandler#onEntityPosition ({@code EntityPositionS2CPacket}) から呼ばれる:
     * 対象の確定テレポート（kill/respawn・TP・スキルワープ等）。速度差分の基線がもう成立しない
     * ので、KB無効領域とは混同せず静かに破棄する。
     */
    public static void onEntityTeleported(int entityId) {
        PENDING.remove(entityId);
    }

    /**
     * ClientPlayNetworkHandler#onEntitiesDestroy ({@code EntitiesDestroyS2CPacket}) から呼ばれる:
     * 対象の除去（卸載・切断・リスポーン置き換え）。テレポート同様に静かに破棄する。
     */
    public static void onEntityRemoved(int entityId) {
        PENDING.remove(entityId);
    }

    /** ClientPlayNetworkHandler#onEntityVelocityUpdate の HEAD から呼ばれる: 生速度の捕捉。 */
    public static void onVelocityPacket(int entityId, double vx, double vy, double vz) {
        PendingHit hit = PENDING.get(entityId);
        if (hit == null || hit.sampled) {
            return;
        }
        if (hit.confirmTick < 0L) {
            // 速度パケットがダメージ確認より先着 = パケット順序の逆転（tick 境界や
            // リージョン跨ぎで起き得る）。速度データを保存し、ダメージ確認後に記録する。
            if (clientTick - hit.hitTick <= CONFIRM_WINDOW) {
                hit.velocityBeforeConfirm = true;
                // エンティティの現在速度を取得して差分を保存
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world != null) {
                    Entity entity = mc.world.getEntityById(entityId);
                    if (entity != null) {
                        Vec3d current = entity.getVelocity();
                        hit.earlyDx = vx - current.x;
                        hit.earlyDy = vy - current.y;
                        hit.earlyDz = vz - current.z;
                        hit.earlyCurX = current.x;
                        hit.earlyCurY = current.y;
                        hit.earlyCurZ = current.z;
                        hit.earlyVelocityTick = clientTick;
                        hit.earlyEntityId = entityId;
                    }
                }
            }
            return;
        }
        if (clientTick - hit.confirmTick > MOTION_WINDOW) {
            return;
        }
        // ベースラインは「過去の速度パケット」では読めない（速度パケットは衝撃時のみ → 古い）。
        // クライアントは対象エンティティの動きを tick ごとに再シミュレーションしているので、
        // HEAD 時点（= vanilla が新速度を適用する直前）のローカル速度が最も正確な現在速度。
        Vec3d current = null;
        Entity entity = null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            entity = mc.world.getEntityById(entityId);
            if (entity != null) {
                current = entity.getVelocity();
            }
        }
        if (current == null) {
            return;
        }
        hit.sampled = true;
        recordSample(hit, entityId, entity, current, vx - current.x, vy - current.y, vz - current.z);
    }

    /** MinecraftClient#tick の TAIL から呼ばれる: タイムアウト処理と状態の清掃。 */
    public static void onClientTick() {
        clientTick++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != lastWorld) {
            // ワールド/サーバー移動: 保留・速度キャッシュを全破棄し、統計を永続化
            PENDING.clear();
            LAST_SAMPLED.clear();
            GEAR_ANNOUNCED.clear();
            lastWorld = mc.world;
            StatsStore.save();
        }
        Iterator<Map.Entry<Integer, PendingHit>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, PendingHit> e = it.next();
            PendingHit hit = e.getValue();
            long age = clientTick - hit.hitTick;
            boolean expired = age > PENDING_TTL
                    || (hit.confirmTick < 0L && age > CONFIRM_WINDOW)
                    || (hit.confirmTick >= 0L && !hit.sampled
                        && clientTick - hit.confirmTick > MOTION_WINDOW);
            if (!expired) {
                continue;
            }
            if (hit.confirmTick < 0L) {
                // 【ガード1】殴ったのにダメージ不成立 = 保護領域/無敵 → 計測しない
                noticeNoDamage();
                StatsStore.statsFor(serverKey()).noDamageEvents++;
                StatsStore.save();
            } else if (!hit.sampled) {
                if (hit.velocityBeforeConfirm) {
                    // 【ガード2b】速度パケットのほうがダメージ確認より先に来ていた = 順序逆転。
                    // KB は実際に適用されているので「無効領域」とは言わせず静かに破棄。
                    it.remove();
                    continue;
                }
                // 【ガード2】ダメージは入ったのに速度が来ない = KB無効領域 → 係数は記録しない
                noticeNoKnockback(hit);
                StatsStore.statsFor(serverKey()).noKbEvents++;
                StatsStore.save();
            } else {
                it.remove();
                continue;
            }
            it.remove();
        }
    }

    // ----------------------------------------------------------------------------------
    // 測定処理
    // ----------------------------------------------------------------------------------

    private static void recordSample(PendingHit hit, int entityId, Entity victim, Vec3d current,
                                     double dx, double dy, double dz) {
        LAST_SAMPLED.put(entityId, clientTick);

        // 混戦ガード: 窓内に第三者（または環境）のダメージが割り込んでいると、測れた速度は
        // 自撃との合成値であり最大 +100% 偏る。送信者はクライアントから判別不能なので除外。
        if (hit.contaminated) {
            StatsStore.statsFor(serverKey()).contaminatedEvents++;
            StatsStore.save();
            return;
        }

        double hRaw = Math.hypot(dx, dz);

        // KB 抑制ガード: 速度パケットが来ても差分が0 ≈ サーバーがKBを抑制した
        // （ロビー保護、damage event cancel 後の空パケット等）。fH≈0 の偽サンプルが
        // 平均を破壊するので静かに棄却。しきい値 = 速度パケット 1 単位 (1/8000 ≈ 0.000125) より余裕。
        if (hRaw < 5.0e-4 && Math.abs(dy) < 5.0e-4) {
            return;
        }

        // 動いている相手にも計測できるよう、旧速度を考慮した推定式:
        //   vanilla: newH = oldH/2 + impulseH  →  deltaH = impulseH - oldH/2
        //   impulseH = deltaH + oldH_parallel/2
        double oldH_parallel = (current.x * hit.dirX + current.z * hit.dirZ);
        double impulseH = hRaw + Math.abs(oldH_parallel) / 2.0;
        // 旧速度が大きすぎると推定精度が落ちる: スプリント速度(0.26)×2まで許容
        if (Math.abs(oldH_parallel) > 0.5) {
            return;
        }
        // 方向ガード: KB は攻撃者→被害者へ押し出すはず。逆向き/横向きの速度は他起因のノイズ
        if (!KbProbeMath.directionOk(dx, dz, hit.dirX, hit.dirZ, hRaw)) {
            return;
        }
        // 外れ値ガード: 想定外の巨大速度は係数推定の母集団に入れない
        if (KbProbeMath.outlier(hRaw, dy)) {
            return;
        }
        // 耐衝撃ガード: 相手の推定耐衝撃が 1.0 以上なら水平は常に 0 → 計算不能
        if (hit.resistance >= 1.0d) {
            return;
        }

        // 初回ヒット時に、対象の装備＋エンチャント（クライアントに同期されている表示用装備）と
        // そこから算出した推定耐衝撃を一度だけ提示する。係数が掛かった「土台」を読み手が確認できる。
        if (GEAR_ANNOUNCED.add(hit.victimUuid)) {
            chat("§7[KBProbe] 対象の装備: "
                    + (hit.gearSummary.isEmpty() ? "(装備なし/非表示)" : hit.gearSummary)
                    + String.format(" → 推定耐衝撃 %.0f%%", hit.resistance * 100.0d));
        }

        ServerStats stats = StatsStore.statsFor(serverKey());
        double k = hit.knockbackLevel();
        double expectH = KbProbeMath.expectHorizontal(k, hit.resistance);
        // impulseH = deltaH + |oldH_parallel|/2 ≈ 実際のKBインパルス
        // 静止標的なら impulseH ≈ hRaw（旧速度=0 なので等価）
        double fH = impulseH / expectH;
        stats.addHorizontal(hRaw, fH);
        Double fV = null;
        if (hit.targetOnGround) {
            // 垂直は「攻撃時 は 接地」だけでなく、速度パケット到着時点でも接地していること
            // が条件。バニラの onGround 判定はサーバー側ダメージ適用 tick で行われるため、
            // クリック〜成立の間にジャンプされると Y は不変のまま (fV≈0 の偽サンプル) になる。
            // さらに係数自体がバニラ上あり得る帯 (0.05〜8.0) に収まるサンプルのみ採用する。
            boolean stillGrounded = victim != null && victim.isOnGround();
            double expectV = KbProbeMath.expectVertical(k, hit.resistance);
            if (stillGrounded && expectV > 1.0e-4) {
                double candidate = dy / expectV;
                if (KbProbeMath.verticalFactorPlausible(candidate)) {
                    fV = candidate;
                    stats.addVertical(dy, fV);
                }
            }
        }
        StatsStore.save();
        // デバッグ: 生値をチャット表示
        chat(String.format("§8[KBProbe] raw Δh=%.4f Δy=%.4f old∥=%.4f impl=%.4f fH=%.2f §7(H%d/V%d)",
                hRaw, dy, oldH_parallel, impulseH, fH, stats.hSamples, stats.vSamples));
        announceSample(impulseH, fH, hit.targetOnGround ? dy : null, fV, k, stats);
    }

    // ----------------------------------------------------------------------------------
    // 表示
    // ----------------------------------------------------------------------------------

    private static void announceSample(double hRaw, double fH, Double vRaw, Double fV,
                                       double knockbackLevel, ServerStats stats) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        String vPart = (vRaw != null && fV != null)
                ? String.format(" V=%.3f(×%.2f)", vRaw, fV) : " V=—(空中/条件外)";
        // アクションバー: 直近サンプル（k=攻撃ノックバックレベル: 属性+疾走ボーナス）
        player.sendMessage(Text.literal(
                String.format("KB計測 H=%.3f(×%.2f)%s k=%.1f @%s",
                        hRaw, fH, vPart, knockbackLevel, serverKey())), true);
        // チャット: 鯖別の集計（5件ごと + 最初の1件）
        if (stats.hSamples == 1 || stats.hSamples % 5 == 0) {
            player.sendMessage(Text.literal(String.format(
                    "§b[KBProbe]§r %s 推定係数: 水平§e×%.2f§r / 垂直§e%s§r (H:%d件 V:%d件)%s%s",
                    serverKey(), stats.avgHorizontalFactor(),
                    stats.vSamples > 0 ? String.format("×%.2f", stats.avgVerticalFactor()) : "未取得",
                    stats.hSamples, stats.vSamples,
                    stats.noKbEvents > 0 ? " ※KB無効領域" + stats.noKbEvents + "回検出" : "",
                    stats.contaminatedEvents > 0 ? " ※混戦混入" + stats.contaminatedEvents + "回除外" : "")), false);
        }
    }

    private static void noticeNoDamage() {
        if (clientTick - lastNoDamageNotice < NOTICE_COOLDOWN) {
            return;
        }
        lastNoDamageNotice = clientTick;
        chat("§e[KBProbe]§r 攻撃がヒットとして成立しませんでした（保護領域か相手が無敵）。§7この殴りは計測しません。§r");
    }

    private static void noticeNoKnockback(PendingHit hit) {
        if (clientTick - lastNoKbNotice < NOTICE_COOLDOWN) {
            return;
        }
        lastNoKbNotice = clientTick;
        String gear = hit.resistance > 0.0d
                ? String.format("（相手の推定耐衝撃 %.0f%% も要因になり得ます）", hit.resistance * 100.0d)
                : "";
        chat("§e[KBProbe]§r ダメージは入りましたが相手の速度は変化しません。ここは§6ノックバック無効領域§r"
                + "（ロビー保護など）と判断しました。§7係数0等の誤データは記録しません。§r" + gear);
    }

    private static void chat(String message) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    // ----------------------------------------------------------------------------------
    // 補助
    // ----------------------------------------------------------------------------------

    /**
     * 対象の装備を「属性コンポーネント込み」で解析する。他プレイヤーの装備は描画のため
     * サーバーから同期されており、アイテム本体・エンチャント・属性モディファイアをすべて
     * クライアントで読める。实体の解決済み attribute map（クラス/キット由来の直接付与など）
     * だけは同期されないので、その分だけ推定が甘くなる点に注意。
     */
    private static GearInfo analyzeGear(PlayerEntity victim) {
        double resistance = 0.0d;
        StringBuilder summary = new StringBuilder();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR
                    && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
                continue;
            }
            ItemStack stack = victim.getEquippedStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            resistance += knockbackResistanceOf(stack, slot);
            if (summary.length() > 0) {
                summary.append(' ');
            }
            summary.append(Registries.ITEM.getId(stack.getItem()).getPath());
            String ench = enchantSummary(stack);
            if (!ench.isEmpty()) {
                summary.append('(').append(ench).append(')');
            }
        }
        return new GearInfo(Math.min(resistance, 1.0d), summary.toString());
    }

    /** 装備1点の耐衝撃（generic.knockback_resistance の ADD_VALUE 合算。装着スロット一致分のみ）。 */
    private static double knockbackResistanceOf(ItemStack stack, EquipmentSlot wornSlot) {
        var mods = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods == null) {
            return 0.0d;
        }
        double r = 0.0d;
        for (AttributeModifiersComponent.Entry entry : mods.modifiers()) {
            if (!isKnockbackResistance(entry.attribute())
                    || entry.modifier().operation() != EntityAttributeModifier.Operation.ADD_VALUE
                    || !slotApplies(entry.slot(), wornSlot)) {
                continue;
            }
            r += entry.modifier().value();
        }
        return r;
    }

    private static boolean isKnockbackResistance(
            RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        // 1.21.11: GENERIC_ プレフィックス削除に伴い matchesId で直接比較
        return attribute.matchesId(
                Identifier.of("minecraft", "knockback_resistance"));
    }

    /** AttributeModifierSlot ↔ 実装備スロットの照合（yarn の matches() 更新に左右されない自前判定）。 */
    private static boolean slotApplies(net.minecraft.component.type.AttributeModifierSlot modifierSlot,
                                       EquipmentSlot wornSlot) {
        return switch (modifierSlot) {
            case HEAD -> wornSlot == EquipmentSlot.HEAD;
            case CHEST -> wornSlot == EquipmentSlot.CHEST;
            case LEGS -> wornSlot == EquipmentSlot.LEGS;
            case FEET -> wornSlot == EquipmentSlot.FEET;
            case ARMOR -> wornSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
            case MAINHAND -> wornSlot == EquipmentSlot.MAINHAND;
            case OFFHAND -> wornSlot == EquipmentSlot.OFFHAND;
            case HAND -> wornSlot == EquipmentSlot.MAINHAND || wornSlot == EquipmentSlot.OFFHAND;
            case ANY -> true;
            default -> true; // BODY 等: 現状の武器/防具には無いので受け流す
        };
    }

    /** エンチャントのコンパクト表示（例 "protection:4,unbreaking:3"）。補正自体はKB計算に使わない情報欄。 */
    private static String enchantSummary(ItemStack stack) {
        var enchants = stack.getEnchantments();
        if (enchants == null || enchants.getEnchantments().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RegistryEntry<net.minecraft.enchantment.Enchantment> entry : enchants.getEnchantments()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey().map(k -> k.getValue().getPath()).orElse("?"));
            sb.append(':').append(enchants.getLevel(entry));
        }
        return sb.length() <= 64 ? sb.toString() : sb.substring(0, 64);
    }

    /**
     * 対象がクリエイティブ/スペクテイターか。他プレイヤーの abilities は同期されないので
     * {@code victim.isCreative()} は当てにできず、プレイヤーリスト（GameProfile 由来の
     * {@link PlayerListEntry#getGameMode()}）で判定する。生命無敵の殴りは保留にしない。
     */
    private static boolean isSpectatorOrCreative(PlayerEntity victim) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) {
            return false;
        }
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(victim.getUuid());
        if (entry == null) {
            return false; // リスト未同期の擬似プレイヤー（NPC bot 等）は従来どおり計測対象
        }
        GameMode mode = entry.getGameMode();
        return mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE;
    }

    private static String serverKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return info.address;
        }
        return mc.isIntegratedServerRunning() ? "local" : "unknown";
    }
}
