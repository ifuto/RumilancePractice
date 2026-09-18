package com.rumilance.practice.herobot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import com.mojang.authlib.GameProfile;
import com.rumilance.practice.packetbot.PacketBot;

import java.util.ArrayList;
import java.util.List;

/**
 * A HeroBot on Paper: {@link PacketBot} (a carpet-style fake {@code ServerPlayer} behind a
 * dead connection) plus the pieces of {@code hero.bane.herobot.bot.BotPlayer} the Quantum map
 * can observe.
 *
 * <p>Where the Fabric mod uses mixins, this class uses overrides:</p>
 * <table>
 *   <tr><th>herobot (Fabric)</th><th>here (Paper)</th></tr>
 *   <tr><td>{@code ServerPlayerMixin} @Inject HEAD of {@code tick()}</td>
 *       <td>{@link #tick()} runs {@link BotActionPack#onUpdate()} first</td></tr>
 *   <tr><td>{@code BotPlayer#tick()} → {@code processPendingKBs()}</td>
 *       <td>{@link #tick()} after {@code super.tick()}</td></tr>
 *   <tr><td>{@code BotPlayer#method_6005} (knockback) with ping delay</td>
 *       <td>{@link #knockback(double, double, double, net.minecraft.world.entity.Entity, io.papermc.paper.event.entity.EntityKnockbackEvent.Cause)}</td></tr>
 *   <tr><td>{@code PlayerCommand} {@code player …} verbs</td>
 *       <td>{@link HeroBotCommands}</td></tr>
 * </table>
 *
 * <p>{@code ping} is the reference's simulated latency: the map sets it from
 * {@code .ping} ({@code quantum:options/set_ping} → {@code player @a[tag=xlib_bot] ping $(ping)}),
 * and HeroBot converts it to ticks with {@link HeroBotSettings#botPingToTicks}, then delays
 * knockback (and, with the lag flags on, attacks/uses) by that many ticks.</p>
 */
public class HeroBotPlayer extends PacketBot {

    private final BotActionPack actionPack;
    /** Simulated ping in milliseconds (herobot: {@code BotPlayer.ping}). */
    public int ping = 0;
    /** Reference spawn bookkeeping ({@code BotPlayer.spawnPos}/{@code spawnYaw}). */
    public Vec3 spawnPos;
    public double spawnYaw;

    private final List<PendingKnockback> pendingKnockbacks = new ArrayList<>();
    private long shieldDisabledTick = -1L;

    // ---------------------------------------------------------------- 爆発ノックバックの ping 遅延
    // 参照 (herobot Fabric) は ServerExplosionMixin.explosionKBPing でバニラの爆発 KB 適用
    // (entity.setDeltaMovement(vec)) を横取りし、BotPlayer は BotPlayer#delayedExplosionKB(vec)
    // で delayTicks(2) だけ遅らせてから processPendingKBs の終端で setDeltaMovement(vec) する。
    // Paper 側にこの横取りがないと、マップ tick-start フェーズの爆発 (アンカー/クリスタル) が
    // BOT 自身の移動処理より先に delta を書くため、KB が移動パイプラインに消われる —
    // 実測: 参照は爆発で vy+0.40 / 水平 0.70 で吹き飛ぶのに対し Paper BOT は実戦中 1 回も
    // 発射しない (クリスタル戦の「間合い」残差の一次因)。
    private final List<PendingExplosionKB> pendingExplosionKB = new ArrayList<>();

    public HeroBotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                         ClientInformation information) {
        super(server, level, profile, information);
        this.actionPack = new BotActionPack(this);
        if (this.getAttribute(Attributes.STEP_HEIGHT) != null) {
            this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6);
        }
        // Paper は ServerPlayer の爆発 KB 耐性を 1.0 (完全耐性) にしている: 実クライアントは
        // ClientboundExplodePacket で自前適用する前提。偽コネクションの BOT にクライアントは
        // 居ないため爆発 KB が完全に消える (実測: EntityKnockbackEvent の kb が全て 0)。
        // 参照 (vanilla + herobot) は ServerExplosion が setDeltaMovement で実適用するため、
        // BOT の属性を 0 に戻してバニラ経路の KB を復活させる。
        // 注: 属性はコンストラクタ時点で未登録 (null) — 最初の doTick で設定する。
    }

    public BotActionPack actionPack() {
        return this.actionPack;
    }

    /** Server tick counter — HeroBot reads {@code level.getServer().getTickCount()}. */
    public long tickCount() {
        MinecraftServer server = this.level().getServer();
        return server == null ? 0L : server.getTickCount();
    }

    /**
     * HeroBot's {@code BotPlayer.delayTicks(n)}: {@code ping / (botPingToTicks * n)} ticks with
     * a random +1 tick for the remainder. Used for knockback and (optionally) attack/use lag.
     */
    public int pingDelayTicks(int multiplier) {
        int conversion = HeroBotSettings.botPingToTicks;
        if (conversion <= 0 || multiplier <= 0) {
            return 0;
        }
        int remainder = this.ping % (conversion * multiplier);
        if (remainder == 0) {
            return this.ping / conversion;
        }
        int random = java.util.concurrent.ThreadLocalRandom.current().nextInt(conversion);
        return random < remainder ? this.ping / conversion + 1 : this.ping / conversion;
    }

    /**
     * The server's player tick path on Paper is
     * {@code PlayerList#tick()} → {@link net.minecraft.server.level.ServerPlayer#doTick()}, and
     * {@code doTick()} reaches the entity's movement through a direct {@code super.tick()} call —
     * i.e. it never dispatches to {@code ServerPlayer#tick()}. The reference's
     * {@code @Inject(method = "tick", at = HEAD)} therefore has to live here: the action pack has
     * to write its inputs before {@code doTick()} runs the movement for the tick.
     */
    @Override
    public void doTick() {
        // 参照 (BotPlayer#method_5773 HEAD → processPendingKBs) と同じく、移動物理の前に
        // 遅延爆発 KB を適用する。この tick の super.doTick() 内の物理が KB を積分する。
        this.processPendingExplosionKB();
        if ((int) this.tickCount() == this.explosionKBCleanupTick) {
            // 爆発 KB の翌 tick: 参照脳は入力ベースで motion を丸ごと再構築する。
            // 実測 (cryR19 fabric): |vy| は全ラウンドで max 0.665 (ジャンプ/落下の範囲) —
            // 爆発 vy は1tick分だけ統合されて消える (=1ブロックのホップ、y_max≈32.2)。
            // Paper の物理は vy を重力に任せて保持するため、全成分をここでリセットしないと
            // 蓄積して空中レジーム (maxY 84-91) になる。
            this.setDeltaMovement(Vec3.ZERO);
            this.explosionKBCleanupTick = -1;
        }
        // Paper の causeExtraKnockback が消したノックバックを、BOT ではここでも保険として復元する。
        this.restoreKnockbackIfStolen();
        this.actionPack.onUpdate();
        double startX = this.getX();
        double startY = this.getY();
        double startZ = this.getZ();
        super.doTick();
        this.processPendingKnockbacks();
        if (this.tickCount() % 10 == 0) {
            // Reference: keep the bot's chunk tracking alive from its own position.
            ((ServerLevel) this.level()).getChunkSource().move(this);
        }
        Vec3 movement = new Vec3(this.getX() - startX, this.getY() - startY, this.getZ() - startZ);
        if (movement.lengthSqr() > 1.0E-5) {
            this.resetLastActionTime();
        }
        this.updateFallDistance();
    }

    /**
     * The reference's bots ride a fake client connection, so the server's own player-movement path
     * keeps {@code Entity#fallDistance} up to date. A Paper bot without a client never runs that
     * path, so the field stays 0 — and every {@code quantum:fall_distance*} / {@code quantum:vmotion*}
     * predicate (the water, cobweb and lava flows of the pack are gated on them) reads false.
     * Reproduce vanilla's accumulation rule here: gain while descending, reset on landing.
     */
    private void updateFallDistance() {
        double dy = this.getDeltaMovement().y;
        if (this.onGround()) {
            this.fallDistance = 0.0;
        } else if (dy < 0.0) {
            this.fallDistance -= dy;
        }
    }

    /**
     * Paper の {@code Player#causeExtraKnockback} は、被弾した {@code ServerPlayer} の delta を
     * <b>被弾前の値に戻す</b>（実クライアントが motion packet を適用する前提の実装:
     * bytecode offset 270 = {@code target.setDeltaMovement(currentMovement)}）。
     * BOT にはクライアントが居ないので、この「戻し」だけが効いてノックバックが完全に消える。
     * そのため、適用したインパクトと被弾前の値を控えておき、戻されたら書き戻す。
     */
    private net.minecraft.world.phys.Vec3 knockbackGuardDelta = null;
    private net.minecraft.world.phys.Vec3 knockbackGuardBefore = null;
    private long knockbackGuardTick = -1L;

    private void armKnockbackGuard(net.minecraft.world.phys.Vec3 before) {
        this.knockbackGuardBefore = before;
        this.knockbackGuardDelta = this.getDeltaMovement();
        this.knockbackGuardTick = this.tickCount();
    }

    /** {@code causeExtraKnockback} の「戻し」で消されたノックバックを書き戻す（同一 tick のみ）。 */
    public void restoreKnockbackIfStolen() {
        if (this.knockbackGuardDelta == null || this.knockbackGuardTick != this.tickCount()) {
            return;
        }
        net.minecraft.world.phys.Vec3 current = this.getDeltaMovement();
        if (current.equals(this.knockbackGuardDelta)) {
            return; // 生きている
        }
        if (this.knockbackGuardBefore != null && current.equals(this.knockbackGuardBefore)) {
            // Paper が被弾前の値へ戻した → BOT はクライアントの代わりにここで復元する
            this.setDeltaMovement(this.knockbackGuardDelta);
        }
        this.knockbackGuardDelta = null;
        this.knockbackGuardBefore = null;
    }

    /** {@code ServerPlayer#tick()} stays vanilla — the server path above does not call it. */
    @Override
    public void tick() {
        super.tick();
    }

    /**
     * Paper の {@code ServerPlayer#getKnownMovement} は {@code lastKnownClientMovement}
     * (クライアントの move packet で更新) を返す。参照の BOT は fake client 接続でそれが
     * 餌付けされるが、この BOT にクライアントは居ないため <b>常にゼロ</b> —
     * {@code minecraft:entity_properties} の movement 述語 (vertical_speed / speed /
     * horizontal_speed) が全て 0 を読み、マップの {@code quantum:vmotion_*} 述語
     * (mace far_pearl/wind_pearl、cobweb・water フロー等) が一度も成立しなくなる
     * (実測: 落下中 fall_distance≥1.0 でも vmotion_m1 不発)。
     * サーバ側物理が権威なので、素の delta をそのまま返す。
     */
    @Override
    public net.minecraft.world.phys.Vec3 getKnownMovement() {
        return this.getDeltaMovement();
    }

    /**
     * Reference: {@code BotPlayer#move} runs the auto-jump probe on the distance the bot actually
     * travelled this tick (its {@code player @s autojump true} support).
     */
    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
        double oldX = this.getX();
        double oldZ = this.getZ();
        super.move(type, movement);
        this.actionPack.updateAutoJump((float) (this.getX() - oldX), (float) (this.getZ() - oldZ));
    }

    /** {@code player <name> drop}/{@code dropStack}: drop the selected item, vanilla style. */
    public void dropSelected(boolean fullStack) {
        ItemStack selected = this.getInventory().getSelectedItem();
        if (selected.isEmpty()) {
            return;
        }
        ItemStack dropped = fullStack ? selected.copy() : selected.copyWithCount(1);
        if (fullStack) {
            this.getInventory().setSelectedItem(ItemStack.EMPTY);
        } else {
            selected.shrink(1);
        }
        this.drop(dropped, false);
    }

    private void processPendingKnockbacks() {
        if (this.pendingKnockbacks.isEmpty()) {
            return;
        }
        long currentTick = this.tickCount();
        this.pendingKnockbacks.removeIf(pending -> {
            if (currentTick >= pending.tick()) {
                this.applyKnockbackWithScale(pending.strength(), pending.x(), pending.z(),
                        pending.horizontalScale(), pending.source(), pending.cause());
                return true;
            }
            return false;
        });
    }

    /**
     * {@code ServerExplosionMixin.explosionKBPing} の代替。バニラは爆発 KB を
     * {@code ServerPlayer} に対しても即時に {@code setDeltaMovement(vec)} する (1.21.11)。
     * 参照はここを横取りして {@code delayTicks(2)} 遅延にしているため、ここで
     * 「直近 doTick 終端からの外部変化 & 爆発ダメージと同 tick」を検出して
     * 差し戻し + キュー入れ (or 遅延なしの即時適用) を再現する。
     */
    /** Paper の ServerPlayer 既定 1.0 (完全耐性) を 0 へ戻す。属性は初 tick 以降に存在する。 */
    /** {@code BotPlayer#processPendingKBs} の爆発分: tick 終端 (移動後) に setDeltaMovement(vec)。 */
    /** 爆発 KB を SET した直後の tick — 参照の脳と同じく水平速度を入力ベースへ再構築する。 */
    private int explosionKBCleanupTick = -1;

    private void processPendingExplosionKB() {
        if (this.pendingExplosionKB.isEmpty()) {
            return;
        }
        // 参照はサーバ tick カウンタで予定/判定する (BotPlayer#delayedExplosionKB /
        // lambda$processPendingKBs$7 とも MinecraftServer#getTickCount)。tickServer 冒頭で
        // インクリメントされるため、ワールド/エンティティtick中は常に現在の tick 値を返す。
        long currentTick = ((ServerLevel) this.level()).getServer().getTickCount();
        this.pendingExplosionKB.removeIf(pending -> {
            if (currentTick >= pending.tick()) {
                // 参照の適用は super.push(生KB) — 適用時点の現在 delta への「加算」である
                // (bytecode: invokespecial class_3222.method_60491 = Entity.push)。
                // キャプチャ時の delta を焼き込むと、パールテレポート等で移動した後の適用で
                // 数tick前の速度が復活し、空中へ打ち上げられる (cryR17/18 で実測した不具合)。
                // 参照 (herobot) はクライアント権限シムで、KB 適用の翌 tick には脳の入力速度で
                // delta を再構築する (実測: 爆発直後 Motion=(0, vy, 0) — 水平成分は 1 tick で消える)。
                // Paper の入力積分物理は KB を何 tick も保持して吹き飛びすぎるため、翌 tick の
                // doTick 冒頭で水平のみリセットする (vy は参照と同じく重力減衰に任せる)。
                // さらに垂直成分: 参照の実測 (cryR57-60) では接地 BOT が爆発で持ち上がらない
                // (爆発後 4t の maxYgain med/p75 = +0.00。入力再構築が接地中の vy を即座に食う)。
                // Paper は push の vy が travel を持ち上げ、OnGround ゲート (mech/hit 等) が
                // 閉れて swing/charge 減・pearl 増のループに繋がった (crystal-b 残差の根本)。
                // → 接地での適用は垂直を食って水平のみ (空中での適用は生ベクトルのまま)。
                Vec3 kb = pending.vec();
                if (this.onGround() && kb.y > 0) {
                    kb = new Vec3(kb.x, 0, kb.z);
                }
                super.push(kb);
                this.explosionKBCleanupTick = (int) this.tickCount() + 1;
                return true;
            }
            return false;
        });
    }


    /** 最後に爆発ダメージを受けた server tick (push(Vec3) 横取りの照合用)。 */
    private long lastExplosionHurtServerTick = -1L;

    /**
     * ServerExplosion はこの Paper ビルドでは EntityKnockbackEvent を発火させず、
     * BOT (ServerPlayer) への爆発 KB を直接 {@code push(Vec3)} で書く (実測: TNT/クリスタル
     * ともに確認)。参照の ServerExplosionMixin 相当として、爆発ダメージと同 tick の push を
     * 横取りして即時適用をやめ、{@code pingDelayTicks(2)} 後の tick 冒頭に
     * {@code super.push(生KB)} する (遅延・生ベクトル — 参照 {@code DelayedExplosionKB} は
     * 生KBのみを保存し、適用も Entity.push = 現在 delta への加算。同一 tick の複数爆発は
     * 加算スタックする)。delay=0 はバニラ通り即時加算 (参照の即時 push と等価)。
     */
    @Override
    public void push(net.minecraft.world.phys.Vec3 vec) {
        if (this.lastExplosionHurtServerTick != this.tickCount()) {
            super.push(vec);
            return;
        }
        int delay = this.pingDelayTicks(2);
        if (delay <= 0) {
            super.push(vec);
            return;
        }
        // 参照と同じく「生KBベクトル」だけを保存する (delta を焼き込まない)。
        long now = ((ServerLevel) this.level()).getServer().getTickCount();
        this.pendingExplosionKB.add(new PendingExplosionKB(now + delay, vec));
    }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.lastExplosionHurtServerTick = this.tickCount();
            // Paper は実クライアント向けに modifier で爆発 KB 耐性を 1.0 にする (KB は
            // ClientboundExplodePacket 経由でクライアント適用)。偽コネクションの BOT には
            // クライアントが居ないので KB が完全消滅する。KB 計算 (hurt 直後・同一イテレーション)
            // の直前で base/modifier を潰し、vanilla と同じ res=0 で計算させる。
            var expKbAttr = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
            if (expKbAttr != null) {
                for (var modifier : new java.util.ArrayList<>(expKbAttr.getModifiers())) {
                    expKbAttr.removeModifier(modifier);
                }
                expKbAttr.setBaseValue(0.0);
            }
        }
        return super.hurtServer(level, source, amount);
    }

    /** HeroBot's {@code BotPlayer#takeKnockback}: knockback arrives {@code delayTicks(2)} late. */
    @Override
    public void knockback(double strength, double x, double z) {
        this.scaledKnockback(strength, x, z, 1.0, null, null);
    }

    /**
     * ★ Paper の近接ノックバックは <b>この 5 引数版</b>で飛んでくる
     * ({@code Player#causeExtraKnockback} → {@code LivingEntity#knockback(DDD, Entity, Cause)})。
     * 3 引数版は Paper では攻撃経路から呼ばれないため、こちらを override しないと
     * <b>BOT がノックバックを一切受けない</b> — 実測: ダイヤ剣で殴っても Paper 側の被弾 BOT の
     * {@code Motion} が {@code (0,-0.078,0)} のまま 0.5 秒で 0 ブロックしか動かず、参照 (Fabric)
     * は 1.686 ブロック吹き飛ぶ。これがクリスタル戦の「間合い」差 (Paper が 1.84 ブロックまで
     * 詰める / 地上率 92% vs 78% / 殴り 2.4 倍) の一次原因だった。
     */
    @Override
    public void knockback(double strength, double x, double z,
                          net.minecraft.world.entity.Entity source,
                          io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
        this.scaledKnockback(strength, x, z, 1.0, source, cause);
    }

    private void scaledKnockback(double strength, double x, double z, double horizontalScale,
                                 net.minecraft.world.entity.Entity source,
                                 io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
        int delay = this.pingDelayTicks(2);
        if (delay <= 0) {
            this.applyKnockbackWithScale(strength, x, z, horizontalScale, source, cause);
        } else {
            this.pendingKnockbacks.add(new PendingKnockback(this.tickCount() + delay, strength,
                    x, z, horizontalScale, source, cause));
        }
    }

    /**
     * HeroBot's knockback with a horizontal scale: 1.0 is vanilla, 0.4 is what the reference uses
     * while the shield-disable window ({@code shieldStunningWindow}) is open — the "shield stun"
     * knockback the map's {@code tempshield}/{@code .stun} scores are built around.
     */
    private void applyKnockbackWithScale(double strength, double x, double z,
                                          double horizontalScale,
                                          net.minecraft.world.entity.Entity source,
                                          io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
        net.minecraft.world.phys.Vec3 before = this.getDeltaMovement();
        if (horizontalScale >= 1.0) {
            if (source != null && cause != null) {
                super.knockback(strength, x, z, source, cause);
            } else {
                super.knockback(strength, x, z);
            }
            this.armKnockbackGuard(before);
            return;
        }
        double amount = strength * (1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (amount <= 0.0) {
            return;
        }
        double dx = x;
        double dz = z;
        while (dx * dx + dz * dz < 1.0E-5) {
            dx = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
            dz = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
        }
        Vec3 direction = new Vec3(dx, 0.0, dz).normalize().scale(amount * horizontalScale);
        Vec3 velocity = this.getDeltaMovement();
        this.setDeltaMovement(velocity.x / 2.0 - direction.x,
                this.onGround() ? Math.min(0.4, velocity.y / 2.0 + amount) : velocity.y,
                velocity.z / 2.0 - direction.z);
        this.armKnockbackGuard(before);
    }

    /**
     * 近接攻撃のあと、被弾 BOT のノックバックが Paper の「戻し」で消えていないか確認して復元する。
     * （BOT には motion packet を適用するクライアントが居ないため、サーバ側 delta が唯一の真実。）
     */
    @Override
    public void attack(net.minecraft.world.entity.Entity target) {
        super.attack(target);
        if (target instanceof HeroBotPlayer victim) {
            victim.restoreKnockbackIfStolen();
        }
    }

    /** HeroBot's {@code handleSpearStab}: the vanilla stab action + a swing. */
    public void stabAttack() {
        if (this.connection != null) {
            this.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STAB, this.blockPosition(), this.getDirection()));
        }
        this.swing(InteractionHand.MAIN_HAND);
    }

    /** {@code player <name> hotbar <1..9>}: select the hotbar slot and tell the (fake) client. */
    public void setSlot(int slot) {
        this.getInventory().setSelectedSlot(slot - 1);
        if (this.connection != null) {
            this.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(
                    slot - 1));
        }
    }

    /** Marks the shield-disable window the map's {@code tempshield}/{@code .stun} logic reads. */
    public void markShieldDisabled() {
        this.shieldDisabledTick = this.tickCount();
    }

    public boolean recentlyShieldDisabled() {
        return this.shieldDisabledTick >= 0
                && this.tickCount() - this.shieldDisabledTick <= HeroBotSettings.shieldStunningWindow;
    }

    /** {@code player <name> disconnect}: the reference drops the fake connection. */
    public void disconnect(String reason) {
        MinecraftServer server = this.owningServer();
        if (server != null) {
            server.execute(() -> {
                if (this.connection != null) {
                    this.connection.disconnect(Component.literal(reason),
                            org.bukkit.event.player.PlayerKickEvent.Cause.PLUGIN);
                }
            });
        }
    }

    /** Marks the shield-disable window (the reference's {@code shieldDisabledTick}). */
    public long shieldDisabledTick() {
        return this.shieldDisabledTick;
    }

    /** {@code player <name> kill}: kill via the bot's own damage source (herobot shakes off first). */
    public void killBot() {
        this.setHealth(0.0f);
        this.die(this.level().damageSources().genericKill());
    }

    private record PendingKnockback(long tick, double strength, double x, double z,
                                    double horizontalScale,
                                    net.minecraft.world.entity.Entity source,
                                    io.papermc.paper.event.entity.EntityKnockbackEvent.Cause cause) {
    }

    /** {@code BotPlayer$DelayedExplosionKB}: 適用予定 tick と、バニラが計算した爆発後の delta。 */
    private record PendingExplosionKB(long tick, Vec3 vec) {
    }
}
