package com.rumilance.kbprobe;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

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

    // ---- 測定基準 ---------------------------------------------------------------------
    /** バニラ基礎ノックバック（強さ）/ 接地時 Y。 */
    private static final double BASE = 0.4d;
    /** 押し出しベクトルと攻撃方向の内積の下限（これ未満はノイズ）。 */
    private static final double DIR_MIN_DOT = 0.2d;
    /** これ以上の生速度は外れ値（爆発・KB棒・特殊衝撃）として集計から除外。 */
    private static final double OUTLIER = 2.5d;

    private static long clientTick;

    private static final class PendingHit {
        final long hitTick;
        long confirmTick = -1L;
        boolean sampled;
        final UUID victimUuid;
        final double dirX, dirZ;
        final boolean targetOnGround, sprint;
        final int enchant;
        final double resistance;
        /** 読み取れた対象の装備概要（エンチャント込み）。空文字 = 装備なし/非表示。 */
        final String gearSummary;

        PendingHit(long hitTick, UUID victimUuid, double dirX, double dirZ, boolean targetOnGround,
                   boolean sprint, int enchant, double resistance, String gearSummary) {
            this.hitTick = hitTick;
            this.victimUuid = victimUuid;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.targetOnGround = targetOnGround;
            this.sprint = sprint;
            this.enchant = enchant;
            this.resistance = resistance;
            this.gearSummary = gearSummary;
        }
    }

    /** 装備解析の結果: 実装備の属性コンポーネントから算出した耐衝撃 + 表示用サマリ。 */
    private record GearInfo(double resistance, String summary) {
    }

    /** 属性のレジストリID（汎用耐衝撃）。 */
    private static final String KNOCKBACK_RESISTANCE_ID = "minecraft:generic.knockback_resistance";

    /** entityId → 保留中の自前ヒット。 */
    private static final Map<Integer, PendingHit> PENDING = new HashMap<>();
    /** entityId → 直近に受信した速度パケットの値（サーバー目線の前回速度）。 */
    private static final Map<Integer, double[]> LAST_VELOCITY = new HashMap<>();
    /** entityId → 最後にサンプルを採った tick。 */
    private static final Map<Integer, Long> LAST_SAMPLED = new HashMap<>();
    /** 装備概要を表示済みの対象（ワールド内で1回だけ出すスパム防止）。 */
    private static final Set<UUID> GEAR_ANNOUNCED = new HashSet<>();
    /** 通知クールダウン（サーバー単位・種別単位）。 */
    private static long lastNoKbNotice = Long.MIN_VALUE;
    private static long lastNoDamageNotice = Long.MIN_VALUE;

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
        // ノックバックの押し出し方向は「攻撃者の yaw」由来（vanilla: sin(yaw), -cos(yaw)）
        float yawRad = (float) Math.toRadians(me.getYaw());
        double dirX = Math.sin(yawRad);
        double dirZ = -Math.cos(yawRad);
        int enchant = EnchantmentHelper.getKnockbackBonus(me);
        GearInfo gear = analyzeGear(victim);
        PendingHit hit = new PendingHit(clientTick, victim.getUuid(), dirX, dirZ, victim.isOnGround(),
                me.isSprinting(), enchant, gear.resistance(), gear.summary());
        PENDING.put(victim.getId(), hit);
    }

    /** ClientPlayNetworkHandler#onEntityDamage の TAIL から呼ばれる: ダメージ成立の確認。 */
    public static void onDamageConfirmed(int entityId) {
        PendingHit hit = PENDING.get(entityId);
        if (hit != null && hit.confirmTick < 0L && clientTick - hit.hitTick <= CONFIRM_WINDOW) {
            hit.confirmTick = clientTick;
        }
    }

    /** ClientPlayNetworkHandler#onEntityVelocityUpdate の HEAD から呼ばれる: 生速度の捕捉。 */
    public static void onVelocityPacket(int entityId, double vx, double vy, double vz) {
        double[] prev = LAST_VELOCITY.put(entityId, new double[]{vx, vy, vz});
        PendingHit hit = PENDING.get(entityId);
        if (hit == null || hit.sampled || hit.confirmTick < 0L) {
            return;
        }
        if (clientTick - hit.confirmTick > MOTION_WINDOW) {
            return;
        }
        hit.sampled = true;
        double baseX = prev != null ? prev[0] : 0.0d;
        double baseY = prev != null ? prev[1] : 0.0d;
        double baseZ = prev != null ? prev[2] : 0.0d;
        recordSample(hit, entityId, vx - baseX, vy - baseY, vz - baseZ);
    }

    /** MinecraftClient#tick の TAIL から呼ばれる: タイムアウト処理と状態の清掃。 */
    public static void onClientTick() {
        clientTick++;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != lastWorld) {
            // ワールド/サーバー移動: 保留・速度キャッシュを全破棄し、統計を永続化
            PENDING.clear();
            LAST_VELOCITY.clear();
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

    private static void recordSample(PendingHit hit, int entityId,
                                     double dx, double dy, double dz) {
        LAST_SAMPLED.put(entityId, clientTick);
        double hRaw = Math.hypot(dx, dz);

        // 方向ガード: KB は攻撃方向へ押し出すはず。逆向き/横向きの速度は他起因のノイズ
        if (hRaw > 1.0e-4 && (dx * hit.dirX + dz * hit.dirZ) / hRaw < DIR_MIN_DOT) {
            return;
        }
        // 外れ値ガード: 想定外の巨大速度は係数推定の母集団に入れない
        if (hRaw > OUTLIER || Math.abs(dy) > OUTLIER) {
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
        // 期待強さ = 基礎0.4 + KBエンチャント(0.5/lv) + 走り攻撃加算(近似 0.5)
        double strength = BASE + 0.5d * hit.enchant + (hit.sprint ? 0.5d : 0.0d);
        double fH = hRaw / (strength * (1.0d - hit.resistance));
        stats.addHorizontal(hRaw, fH);
        // 垂直は「接地していた相手」のみ有効（空中ではバニラは Y を変えない）
        Double fV = null;
        if (hit.targetOnGround) {
            fV = dy / BASE;
            stats.addVertical(dy, fV);
        }
        StatsStore.save();
        announceSample(hRaw, fH, hit.targetOnGround ? dy : null, fV, stats);
    }

    // ----------------------------------------------------------------------------------
    // 表示
    // ----------------------------------------------------------------------------------

    private static void announceSample(double hRaw, double fH, Double vRaw, Double fV,
                                       ServerStats stats) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        String vPart = (vRaw != null && fV != null)
                ? String.format(" V=%.3f(×%.2f)", vRaw, fV) : " V=—(空中)";
        // アクションバー: 直近サンプル
        player.sendMessage(Text.literal(
                String.format("KB計測 H=%.3f(×%.2f)%s @%s", hRaw, fH, vPart, serverKey())), true);
        // チャット: 鯖別の集計（5件ごと + 最初の1件）
        if (stats.hSamples() == 1 || stats.hSamples() % 5 == 0) {
            player.sendMessage(Text.literal(String.format(
                    "§b[KBProbe]§r %s 推定係数: 水平§e×%.2f§r / 垂直§e%s§r (H:%d件 V:%d件)%s",
                    serverKey(), stats.avgHorizontalFactor(),
                    stats.vSamples() > 0 ? String.format("×%.2f", stats.avgVerticalFactor()) : "未取得",
                    stats.hSamples(), stats.vSamples(),
                    stats.noKbEvents > 0 ? " ※KB無効領域" + stats.noKbEvents + "回検出" : "")), false);
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
        return attribute.getKey()
                .map(key -> key.getValue().toString().equals(KNOCKBACK_RESISTANCE_ID))
                .orElse(false);
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

    private static String serverKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) {
            return info.address;
        }
        return mc.isIntegratedServerRunning() ? "local" : "unknown";
    }
}
