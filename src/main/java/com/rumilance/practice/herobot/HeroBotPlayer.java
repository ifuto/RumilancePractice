package com.rumilance.practice.herobot;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 *       <td>{@link #knockback(double, double, double)}</td></tr>
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

    public HeroBotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
                         ClientInformation information) {
        super(server, level, profile, information);
        this.actionPack = new BotActionPack(this);
        if (this.getAttribute(Attributes.STEP_HEIGHT) != null) {
            this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6);
        }
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

    /** {@code ServerPlayer#tick()} stays vanilla — the server path above does not call it. */
    @Override
    public void tick() {
        super.tick();
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
                        pending.horizontalScale());
                return true;
            }
            return false;
        });
    }

    /** HeroBot's {@code BotPlayer#takeKnockback}: knockback arrives {@code delayTicks(2)} late. */
    @Override
    public void knockback(double strength, double x, double z) {
        this.scaledKnockback(strength, x, z, 1.0);
    }

    private void scaledKnockback(double strength, double x, double z, double horizontalScale) {
        int delay = this.pingDelayTicks(2);
        if (delay <= 0) {
            this.applyKnockbackWithScale(strength, x, z, horizontalScale);
        } else {
            this.pendingKnockbacks.add(
                    new PendingKnockback(this.tickCount() + delay, strength, x, z, horizontalScale));
        }
    }

    /**
     * HeroBot's knockback with a horizontal scale: 1.0 is vanilla, 0.4 is what the reference uses
     * while the shield-disable window ({@code shieldStunningWindow}) is open — the "shield stun"
     * knockback the map's {@code tempshield}/{@code .stun} scores are built around.
     */
    private void applyKnockbackWithScale(double strength, double x, double z,
                                          double horizontalScale) {
        if (horizontalScale >= 1.0) {
            super.knockback(strength, x, z);
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
                                    double horizontalScale) {
    }
}
