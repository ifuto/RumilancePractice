package com.rumilance.practice.herobot;

import com.rumilance.practice.packetbot.PacketBot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * HeroBot's {@code BotPlayerActionPack}, ported from
 * {@code hero.bane.herobot.bot.BotPlayerActionPack} (herobot 1.21.11-1.7.6) — the machinery behind
 * every {@code player <targets> <verb>} line the Quantum map runs.
 *
 * <p>It is a straight port: same action model (once / continuous / interval with a strike limit),
 * same 3-tick item-use cooldown, same movement multipliers ({@code sneaking ? 0.3 : 1.0},
 * {@code ×0.2} while using an item without the kinetic component), same look interpolation
 * (including {@code ~} turn rate = wrapped delta / ticks), same auto-jump probe, same
 * jump-once double-tap flight toggle, and the same target pick: block reach first, then entity
 * reach (see {@link #getTarget}).</p>
 *
 * <p>Deviations, all because Paper is not Fabric: the reference's {@code ServerPlayerMixin} tick
 * hook is {@link HeroBotPlayer#tick()}, its {@code BotPlayer#move} hook is a {@code move()} override,
 * and the delayed actions are drained against {@code MinecraftServer#getTickCount()} instead of the
 * Fabric server's tick counter. Nothing about the decisions above changes.</p>
 */
public final class BotActionPack {

    public final HeroBotPlayer player;

    private final Map<ActionType, Action> actions = new EnumMap<>(ActionType.class);
    private BlockPos currentBlock;
    private int blockHitDelay;
    private boolean isHittingBlock;
    private float curBlockDamageMP;
    public boolean autoJump = false;
    private int autoJumpTime;
    private boolean sneaking;
    private boolean sprinting;
    private float forward;
    private float strafing;
    private long lastJumpOnceTick = -100L;
    private boolean jumping;
    private int itemUseCooldown;
    private final List<DelayedAction> pendingActions = new ArrayList<>();
    private LookInterpolation lookInterpolation;

    public BotActionPack(HeroBotPlayer player) {
        this.player = player;
        this.stopAll();
    }

    public void copyFrom(BotActionPack other) {
        this.actions.putAll(other.actions);
        this.currentBlock = other.currentBlock;
        this.blockHitDelay = other.blockHitDelay;
        this.isHittingBlock = other.isHittingBlock;
        this.curBlockDamageMP = other.curBlockDamageMP;
        this.sneaking = other.sneaking;
        this.sprinting = other.sprinting;
        this.forward = other.forward;
        this.strafing = other.strafing;
        this.itemUseCooldown = other.itemUseCooldown;
    }

    // ------------------------------------------------------------------ actions

    public BotActionPack start(ActionType type, Action action) {
        if (action.isContinuous && this.actions.get(type) != null) {
            return this;
        }
        Action previous = this.actions.remove(type);
        if (previous != null) {
            type.stop(this.player, previous);
        }
        this.actions.put(type, action);
        return this;
    }

    public BotActionPack stop(ActionType type) {
        Action action = this.actions.remove(type);
        if (action != null) {
            action.ticksRemaining = -1;
            type.stop(this.player, action);
        }
        if (type == ActionType.USE) {
            this.itemUseCooldown = 0;
            this.player.stopUsingItem();
        }
        return this;
    }

    // ------------------------------------------------------------------ movement / look

    public BotActionPack setSneaking(boolean doSneak) {
        this.sneaking = doSneak;
        this.player.setShiftKeyDown(doSneak);
        return this;
    }

    public BotActionPack setSprinting(boolean doSprint) {
        this.sprinting = doSprint;
        this.player.setSprinting(doSprint);
        return this;
    }

    public BotActionPack setForward(float value) {
        this.forward = value;
        return this;
    }

    public BotActionPack setStrafing(float value) {
        this.strafing = value;
        return this;
    }

    public BotActionPack look(Direction direction) {
        return switch (direction) {
            case NORTH -> this.look(180.0f, 0.0f);
            case SOUTH -> this.look(0.0f, 0.0f);
            case WEST -> this.look(-90.0f, 0.0f);
            case EAST -> this.look(90.0f, 0.0f);
            case UP -> this.look(this.player.getYRot(), -90.0f);
            case DOWN -> this.look(this.player.getYRot(), 90.0f);
        };
    }

    public BotActionPack look(Vec2 rotation) {
        return this.look(rotation.y, rotation.x);
    }

    public BotActionPack look(float yaw, float pitch) {
        this.player.setYRot(yaw % 360.0f);
        this.player.setXRot(Mth.clamp(pitch, -90.0f, 90.0f));
        return this;
    }

    public BotActionPack lookAt(Vec3 position) {
        this.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, position);
        return this;
    }

    public BotActionPack turn(float yaw, float pitch) {
        return this.look(this.player.getYRot() + yaw, this.player.getXRot() + pitch);
    }

    public BotActionPack lookInterpolated(float targetYaw, float targetPitch, int ticks) {
        if (ticks <= 0) {
            return this.look(targetYaw, targetPitch);
        }
        float clampedPitch = Mth.clamp(targetPitch, -90.0f, 90.0f);
        this.lookInterpolation = new LookInterpolation(targetYaw, clampedPitch,
                Mth.wrapDegrees(targetYaw - this.player.getYRot()) / (float) ticks,
                (clampedPitch - this.player.getXRot()) / (float) ticks, ticks);
        return this;
    }

    public BotActionPack look(Direction direction, int ticks) {
        float targetYaw;
        float targetPitch;
        switch (direction) {
            case NORTH -> {
                targetYaw = 180.0f;
                targetPitch = 0.0f;
            }
            case SOUTH -> {
                targetYaw = 0.0f;
                targetPitch = 0.0f;
            }
            case WEST -> {
                targetYaw = -90.0f;
                targetPitch = 0.0f;
            }
            case EAST -> {
                targetYaw = 90.0f;
                targetPitch = 0.0f;
            }
            case UP -> {
                targetYaw = this.player.getYRot();
                targetPitch = -90.0f;
            }
            case DOWN -> {
                targetYaw = this.player.getYRot();
                targetPitch = 90.0f;
            }
            default -> {
                return this;
            }
        }
        return this.lookInterpolated(targetYaw, targetPitch, ticks);
    }

    public BotActionPack look(Vec2 rotation, int ticks) {
        return this.lookInterpolated(rotation.y, rotation.x, ticks);
    }

    public BotActionPack look(float yaw, float pitch, int ticks) {
        return this.lookInterpolated(yaw, pitch, ticks);
    }

    public BotActionPack turn(float yaw, float pitch, int ticks) {
        return this.lookInterpolated(this.player.getYRot() + yaw, this.player.getXRot() + pitch, ticks);
    }

    public BotActionPack lookAt(Vec3 position, int ticks) {
        if (ticks <= 0) {
            return this.lookAt(position);
        }
        Vec3 eye = this.player.getEyePosition();
        double dx = position.x - eye.x;
        double dy = position.y - eye.y;
        double dz = position.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        return this.lookInterpolated(yaw, pitch, ticks);
    }

    public void stopInterpolation() {
        this.lookInterpolation = null;
    }

    public BotActionPack stopMovement() {
        this.setSneaking(false);
        this.setSprinting(false);
        this.forward = 0.0f;
        this.strafing = 0.0f;
        return this;
    }

    public BotActionPack stopAll() {
        for (ActionType type : new ArrayList<>(this.actions.keySet())) {
            type.stop(this.player, this.actions.get(type));
        }
        this.actions.clear();
        this.stopInterpolation();
        return this.stopMovement();
    }

    // ------------------------------------------------------------------ the tick

    /** Called at the head of the bot's tick — the reference's {@code ServerPlayerEntity#tick} hook. */
    public void onUpdate() {
        if (this.autoJumpTime > 0) {
            --this.autoJumpTime;
            this.start(ActionType.JUMP, Action.once());
        }
        if (this.lookInterpolation != null) {
            --this.lookInterpolation.ticksRemaining;
            if (this.lookInterpolation.ticksRemaining == 0) {
                this.look(this.lookInterpolation.targetYaw, this.lookInterpolation.targetPitch);
                this.lookInterpolation = null;
            } else {
                this.player.setYRot(Mth.wrapDegrees(
                        this.player.getYRot() + this.lookInterpolation.deltaYaw));
                this.player.setXRot(Mth.clamp(
                        this.player.getXRot() + this.lookInterpolation.deltaPitch, -90.0f, 90.0f));
            }
        }
        if (!this.pendingActions.isEmpty()) {
            long currentTick = this.tickCount();
            Iterator<DelayedAction> it = this.pendingActions.iterator();
            while (it.hasNext()) {
                DelayedAction delayed = it.next();
                if (currentTick < delayed.tick()) {
                    continue;
                }
                delayed.action().run();
                it.remove();
            }
        }
        Map<ActionType, Boolean> actionAttempts = new HashMap<>();
        this.actions.values().removeIf(action -> action.done);
        for (Map.Entry<ActionType, Action> entry : this.actions.entrySet()) {
            ActionType type = entry.getKey();
            Action action = entry.getValue();
            Boolean actionStatus;
            if (actionAttempts.getOrDefault(ActionType.USE, false) && type == ActionType.ATTACK) {
                continue;
            }
            actionStatus = action.tick(this, type);
            if (actionStatus != null) {
                actionAttempts.put(type, actionStatus);
            }
            if (type == ActionType.ATTACK
                    && actionAttempts.getOrDefault(ActionType.ATTACK, false)
                    && !actionAttempts.getOrDefault(ActionType.USE, true)) {
                Action using = this.actions.get(ActionType.USE);
                if (using != null) {
                    using.retry(this, ActionType.USE);
                }
            }
        }
        if ((this.forward != 0.0f || this.strafing != 0.0f)
                && this.player.getFoodData().getFoodLevel() < 3) {
            this.player.setSprinting(false);
        } else if (this.sprinting) {
            this.player.setSprinting(true);
        }
        float vel = this.sneaking ? 0.3f : 1.0f;
        vel *= this.player.isUsingItem()
                && !this.player.getMainHandItem().has(DataComponents.KINETIC_WEAPON) ? 0.2f : 1.0f;
        if (this.forward != 0.0f || this.player instanceof HeroBotPlayer) {
            this.player.zza = this.forward * vel;
        }
        if (this.strafing != 0.0f || this.player instanceof HeroBotPlayer) {
            this.player.xxa = this.strafing * vel;
        }
        if (this.player.getAbilities().flying && this.player instanceof HeroBotPlayer) {
            double verticalSpeed = 0.15000000000000002;
            Vec3 delta = this.player.getDeltaMovement();
            if (this.jumping && !this.sneaking) {
                this.player.setDeltaMovement(delta.add(0.0, verticalSpeed, 0.0));
            } else if (this.sneaking && !this.jumping) {
                this.player.setDeltaMovement(delta.add(0.0, -verticalSpeed, 0.0));
            }
            if (this.jumping && this.actions.get(ActionType.JUMP) == null) {
                this.jumping = false;
            }
        }
    }

    private long tickCount() {
        return this.player.level().getServer().getTickCount();
    }

    /** The reference's {@code BotPlayer#move} hook that drives auto-jump from real displacement. */
    public void updateAutoJump(float movedX, float movedZ) {
        if (!this.canAutoJump()) {
            return;
        }
        Vec3 startPos = this.player.position();
        Vec3 endPos = startPos.add(movedX, 0.0, movedZ);
        Vec3 movement = new Vec3(movedX, 0.0, movedZ);
        float speed = this.player.getSpeed();
        float movementLenSqr = (float) movement.lengthSqr();
        if (movementLenSqr <= 0.001f) {
            Vec2 moveVector = new Vec2(this.strafing, this.forward);
            float xInput = speed * moveVector.y;
            float zInput = speed * moveVector.x;
            float sin = Mth.sin((float) (this.player.getYRot() * ((float) Math.PI / 180)));
            float cos = Mth.cos((float) (this.player.getYRot() * ((float) Math.PI / 180)));
            movement = new Vec3(xInput * cos - zInput * sin, movement.y, zInput * cos + xInput * sin);
            movementLenSqr = (float) movement.lengthSqr();
            if (movementLenSqr <= 0.001f) {
                return;
            }
        }
        float invLen = Mth.invSqrt(movementLenSqr);
        Vec3 movementDir = movement.scale(invLen);
        Vec3 playerForward = this.player.getForward();
        float forwardDot = (float) (playerForward.x * movementDir.x + playerForward.z * movementDir.z);
        if (forwardDot < -0.15f) {
            return;
        }
        ServerLevel level = (ServerLevel) this.player.level();
        CollisionContext collisionContext = CollisionContext.of(this.player);
        BlockPos blockPos = BlockPos.containing(this.player.getX(),
                this.player.getBoundingBox().maxY, this.player.getZ());
        if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos, collisionContext).isEmpty()) {
            return;
        }
        blockPos = blockPos.above();
        if (!level.getBlockState(blockPos).getCollisionShape(level, blockPos, collisionContext).isEmpty()) {
            return;
        }
        float maxStepHeight = 1.2f;
        if (this.player.hasEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST)) {
            maxStepHeight += (float) (java.util.Objects.requireNonNull(
                    this.player.getEffect(net.minecraft.world.effect.MobEffects.JUMP_BOOST))
                    .getAmplifier() + 1) * 0.75f;
        }
        float probeDistance = Math.max(speed * 7.0f, 1.0f / invLen);
        Vec3 probeEnd = endPos.add(movementDir.scale(probeDistance));
        float bbWidth = this.player.getBbWidth();
        float bbHeight = this.player.getBbHeight();
        AABB searchBox = new AABB(startPos, probeEnd.add(0.0, bbHeight, 0.0))
                .inflate(bbWidth, 0.0, bbWidth);
        Vec3 raisedStart = startPos.add(0.0, 0.51, 0.0);
        Vec3 raisedEnd = probeEnd.add(0.0, 0.51, 0.0);
        Vec3 side = movementDir.cross(new Vec3(0.0, 1.0, 0.0));
        Vec3 halfWidth = side.scale(bbWidth * 0.5f);
        Vec3 leftStart = raisedStart.subtract(halfWidth);
        Vec3 leftEnd = raisedEnd.subtract(halfWidth);
        Vec3 rightStart = raisedStart.add(halfWidth);
        Vec3 rightEnd = raisedEnd.add(halfWidth);
        float obstacleTopY = Float.MIN_VALUE;
        outer:
        for (VoxelShape shape : level.getBlockCollisions(this.player, searchBox)) {
            for (AABB hitBox : shape.toAabbs()) {
                if (!hitBox.clip(leftStart, leftEnd).isPresent()
                        && !hitBox.clip(rightStart, rightEnd).isPresent()) {
                    continue;
                }
                obstacleTopY = (float) hitBox.maxY;
                BlockPos obstaclePos = BlockPos.containing(hitBox.getCenter());
                int up = 1;
                while ((float) up < maxStepHeight) {
                    BlockPos abovePos = obstaclePos.above(up);
                    VoxelShape aboveShape = level.getBlockState(abovePos)
                            .getCollisionShape(level, abovePos, collisionContext);
                    if (!aboveShape.isEmpty()
                            && (double) (obstacleTopY = (float) aboveShape.max(Direction.Axis.Y)
                            + (float) abovePos.getY()) - this.player.getY() > (double) maxStepHeight) {
                        return;
                    }
                    if (up > 1) {
                        blockPos = blockPos.above();
                        if (!level.getBlockState(blockPos)
                                .getCollisionShape(level, blockPos, collisionContext).isEmpty()) {
                            return;
                        }
                    }
                    ++up;
                }
                break outer;
            }
        }
        if (obstacleTopY != Float.MIN_VALUE) {
            float stepHeight = (float) ((double) obstacleTopY - this.player.getY());
            if (stepHeight > 0.5f && stepHeight <= maxStepHeight) {
                this.autoJumpTime = 1;
            }
        }
    }

    public void attemptAutoJump() {
        boolean wasAutoJump = this.autoJump;
        this.autoJump = true;
        this.updateAutoJump(0.0f, 0.0f);
        this.autoJump = wasAutoJump;
    }

    private boolean canAutoJump() {
        return this.autoJump && this.autoJumpTime <= 0 && this.player.onGround()
                && !this.player.isPassenger() && (this.forward != 0.0f || this.strafing != 0.0f);
    }

    /** The reference's target pick: the block in reach wins, otherwise the entity in reach. */
    public static HitResult getTarget(HeroBotPlayer player) {
        double blockReach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        double entityReach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        HitResult hit = rayTrace(player, 1.0f, blockReach, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }
        return rayTrace(player, 1.0f, entityReach, false);
    }

    public void setSlot(int slot) {
        this.player.setSlot(slot);
    }

    public float forward() {
        return this.forward;
    }

    public float strafing() {
        return this.strafing;
    }

    public boolean isSprinting() {
        return this.sprinting;
    }

    public boolean isSneaking() {
        return this.sneaking;
    }

    public boolean isJumping() {
        return this.jumping;
    }

    /** True while a use/attack click is being held (the map's {@code continuous} verbs). */
    public boolean isActive(ActionType type) {
        return this.actions.containsKey(type);
    }

    // ------------------------------------------------------------------ ray trace

    /** The reference's {@code util/RayTrace}: block clip first, then the nearest entity inside it. */
    public static HitResult rayTrace(Entity source, float partialTicks, double reach, boolean fluids) {
        BlockHitResult blockHit = rayTraceBlocks(source, partialTicks, reach, fluids);
        double maxSqDist = reach * reach;
        if (blockHit != null) {
            maxSqDist = blockHit.getLocation().distanceToSqr(source.getEyePosition(partialTicks));
        }
        EntityHitResult entityHit = rayTraceEntities(source, partialTicks, reach, maxSqDist);
        return entityHit == null ? blockHit : entityHit;
    }

    public static BlockHitResult rayTraceBlocks(Entity source, float partialTicks, double reach,
                                                boolean fluids) {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 rotation = source.getViewVector(partialTicks);
        Vec3 reachEnd = pos.add(rotation.x * reach, rotation.y * reach, rotation.z * reach);
        return source.level().clip(new ClipContext(pos, reachEnd, ClipContext.Block.OUTLINE,
                fluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, source));
    }

    public static EntityHitResult rayTraceEntities(Entity source, float partialTicks, double reach,
                                                   double maxSqDist) {
        Vec3 pos = source.getEyePosition(partialTicks);
        Vec3 reachVec = source.getViewVector(partialTicks).scale(reach);
        AABB box = source.getBoundingBox().expandTowards(reachVec).inflate(1.0);
        return rayTraceEntities(source, pos, pos.add(reachVec), box,
                entity -> !entity.isSpectator() && entity.isPickable(), maxSqDist);
    }

    public static EntityHitResult rayTraceEntities(Entity source, Vec3 start, Vec3 end, AABB box,
                                                   Predicate<Entity> predicate, double maxSqDistance) {
        double targetDistance = maxSqDistance;
        Entity target = null;
        Vec3 targetHitPos = null;
        for (Entity current : source.level().getEntities(source, box, predicate)) {
            AABB currentBox = current.getBoundingBox().inflate(current.getPickRadius());
            java.util.Optional<Vec3> currentHit = currentBox.clip(start, end);
            if (currentBox.contains(start)) {
                if (targetDistance >= 0.0) {
                    target = current;
                    targetHitPos = currentHit.orElse(start);
                    targetDistance = 0.0;
                }
                continue;
            }
            if (currentHit.isEmpty()) {
                continue;
            }
            Vec3 hitPos = currentHit.get();
            double currentDistance = start.distanceToSqr(hitPos);
            if (currentDistance >= targetDistance && targetDistance != 0.0) {
                continue;
            }
            if (current.getRootVehicle() == source.getRootVehicle()) {
                if (targetDistance != 0.0) {
                    continue;
                }
                target = current;
                targetHitPos = hitPos;
                continue;
            }
            target = current;
            targetHitPos = hitPos;
            targetDistance = currentDistance;
        }
        return target == null ? null : new EntityHitResult(target, targetHitPos);
    }

    // ------------------------------------------------------------------ action types

    public enum ActionType {
        USE(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                BotActionPack pack = player.actionPack();
                if (pack.itemUseCooldown > 0) {
                    --pack.itemUseCooldown;
                    return true;
                }
                if (player.isUsingItem()) {
                    return true;
                }
                HitResult hit = BotActionPack.getTarget(player);
                if (HeroBotSettings.botLagUses) {
                    int delay = player.pingDelayTicks(1);
                    if (delay > 0) {
                        long executeAt = pack.tickCount() + delay;
                        pack.pendingActions.add(new DelayedAction(executeAt,
                                () -> executeUse(player, hit)));
                        pack.itemUseCooldown = delay;
                        return true;
                    }
                }
                return executeUse(player, hit);
            }

            @Override
            void inactiveTick(HeroBotPlayer player, Action action) {
                BotActionPack pack = player.actionPack();
                pack.itemUseCooldown = 0;
                player.stopUsingItem();
            }
        },
        ATTACK(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                ItemStack stack = player.getMainHandItem();
                boolean isSpear = stack.has(DataComponents.KINETIC_WEAPON);
                if (isSpear) {
                    if (player.getAttackStrengthScale(0.5f) < 1.0f) {
                        return false;
                    }
                    BotActionPack pack = player.actionPack();
                    if (HeroBotSettings.botLagAttacks) {
                        int delay = player.pingDelayTicks(1);
                        if (delay > 0) {
                            long executeAt = pack.tickCount() + delay;
                            pack.pendingActions.add(new DelayedAction(executeAt,
                                    () -> handleSpearStab(player)));
                            return true;
                        }
                    }
                    handleSpearStab(player);
                    return true;
                }
                HitResult hit = BotActionPack.getTarget(player);
                switch (hit.getType()) {
                    case ENTITY -> {
                        Entity target = ((EntityHitResult) hit).getEntity();
                        boolean continuous = action.isContinuous;
                        BotActionPack pack = player.actionPack();
                        if (HeroBotSettings.botLagAttacks) {
                            int delay = player.pingDelayTicks(1);
                            if (delay > 0) {
                                boolean wasSprinting = player.isSprinting();
                                double savedFallDistance = player.fallDistance;
                                boolean wasOnGround = player.onGround();
                                long executeAt = pack.tickCount() + delay;
                                pack.pendingActions.add(new DelayedAction(executeAt, () -> {
                                    boolean currentSprinting = player.isSprinting();
                                    double currentFallDistance = player.fallDistance;
                                    boolean currentOnGround = player.onGround();
                                    player.setSprinting(wasSprinting);
                                    player.fallDistance = savedFallDistance;
                                    player.setOnGround(wasOnGround);
                                    if (!continuous) {
                                        player.attack(target);
                                        player.swing(InteractionHand.MAIN_HAND);
                                    }
                                    player.resetAttackStrengthTicker();
                                    player.resetLastActionTime();
                                    player.setSprinting(currentSprinting);
                                    player.fallDistance = currentFallDistance;
                                    player.setOnGround(currentOnGround);
                                }));
                                return true;
                            }
                        }
                        if (!continuous) {
                            player.attack(target);
                            player.swing(InteractionHand.MAIN_HAND);
                        }
                        player.resetAttackStrengthTicker();
                        player.resetLastActionTime();
                        return true;
                    }
                    case BLOCK -> {
                        BotActionPack pack = player.actionPack();
                        if (pack.blockHitDelay > 0) {
                            --pack.blockHitDelay;
                            return false;
                        }
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getDirection();
                        ServerLevel level = (ServerLevel) player.level();
                        if (player.blockActionRestricted(level, pos,
                                player.gameMode.getGameModeForPlayer())) {
                            return false;
                        }
                        if (pack.currentBlock != null && level.getBlockState(pack.currentBlock).isAir()) {
                            pack.currentBlock = null;
                            return false;
                        }
                        BlockState state = level.getBlockState(pos);
                        boolean blockBroken = false;
                        if (player.gameMode.getGameModeForPlayer().isCreative()) {
                            player.gameMode.handleBlockBreakAction(pos,
                                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side,
                                    level.getHeight(), -1);
                            pack.blockHitDelay = 5;
                            blockBroken = true;
                        } else if (pack.currentBlock == null || !pack.currentBlock.equals(pos)) {
                            if (pack.currentBlock != null) {
                                player.gameMode.handleBlockBreakAction(pack.currentBlock,
                                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                                        side, level.getHeight(), -1);
                            }
                            player.gameMode.handleBlockBreakAction(pos,
                                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, side,
                                    level.getHeight(), -1);
                            boolean notAir = !state.isAir();
                            if (notAir && pack.curBlockDamageMP == 0.0f) {
                                state.attack(level, pos, player);
                            }
                            if (notAir && state.getDestroyProgress(player, level, pos) >= 1.0f) {
                                pack.currentBlock = null;
                                blockBroken = true;
                            } else {
                                pack.currentBlock = pos;
                                pack.curBlockDamageMP = 0.0f;
                            }
                        } else {
                            pack.curBlockDamageMP += state.getDestroyProgress(player, level, pos);
                            if (pack.curBlockDamageMP >= 1.0f) {
                                player.gameMode.handleBlockBreakAction(pos,
                                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                                        side, level.getHeight(), -1);
                                pack.currentBlock = null;
                                pack.blockHitDelay = 5;
                                blockBroken = true;
                            }
                            level.destroyBlockProgress(-1, pos, (int) (pack.curBlockDamageMP * 10.0f));
                        }
                        player.resetLastActionTime();
                        player.swing(InteractionHand.MAIN_HAND);
                        return blockBroken;
                    }
                }
                if (!action.isContinuous) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
                player.resetAttackStrengthTicker();
                player.resetLastActionTime();
                return false;
            }
        },
        JUMP(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                BotActionPack pack = player.actionPack();
                long currentTick = pack.tickCount();
                if (action.limit == 1) {
                    if (player.getAbilities().mayfly && currentTick - pack.lastJumpOnceTick <= 7L) {
                        player.getAbilities().flying = !player.getAbilities().flying;
                        player.onUpdateAbilities();
                        pack.lastJumpOnceTick = -100L;
                        return false;
                    }
                    pack.lastJumpOnceTick = currentTick;
                    if (player.getAbilities().flying) {
                        pack.jumping = true;
                    } else if (player.onGround()) {
                        player.jumpFromGround();
                    } else if (!player.onClimbable() && !player.getAbilities().flying) {
                        player.tryToStartFallFlying();
                    }
                } else if (player.getAbilities().flying) {
                    pack.jumping = true;
                } else {
                    player.setJumping(true);
                }
                return false;
            }

            @Override
            void inactiveTick(HeroBotPlayer player, Action action) {
                BotActionPack pack = player.actionPack();
                player.setJumping(false);
                pack.jumping = false;
            }
        },
        DROP_ITEM(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                player.resetLastActionTime();
                player.dropSelected(false);
                return false;
            }
        },
        DROP_STACK(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                player.resetLastActionTime();
                player.dropSelected(true);
                return false;
            }
        },
        SWAP_HANDS(true) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                player.resetLastActionTime();
                ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
                player.setItemInHand(InteractionHand.OFF_HAND,
                        player.getItemInHand(InteractionHand.MAIN_HAND));
                player.setItemInHand(InteractionHand.MAIN_HAND, off);
                return false;
            }
        },
        SWING(false) {
            @Override
            boolean execute(HeroBotPlayer player, Action action) {
                InteractionHand hand = action.hand == null ? InteractionHand.MAIN_HAND : action.hand;
                player.swing(hand);
                player.resetLastActionTime();
                return false;
            }
        };

        public final boolean preventSpectator;

        ActionType(boolean preventSpectator) {
            this.preventSpectator = preventSpectator;
        }

        abstract boolean execute(HeroBotPlayer player, Action action);

        void inactiveTick(HeroBotPlayer player, Action action) {
        }

        void stop(HeroBotPlayer player, Action action) {
            this.inactiveTick(player, action);
        }

        /** The reference's {@code executeUse}: block use, then entity use, then item use. */
        private static boolean executeUse(HeroBotPlayer player, HitResult hit) {
            BotActionPack pack = player.actionPack();
            for (InteractionHand hand : InteractionHand.values()) {
                switch (hit.getType()) {
                    case BLOCK -> {
                        player.resetLastActionTime();
                        ServerLevel level = (ServerLevel) player.level();
                        BlockHitResult blockHit = (BlockHitResult) hit;
                        BlockPos pos = blockHit.getBlockPos();
                        Direction side = blockHit.getDirection();
                        if (pos.getY() >= level.getHeight() - (side == Direction.UP ? 1 : 0)
                                || !level.mayInteract(player, pos)) {
                            break;
                        }
                        InteractionResult result = player.gameMode.useItemOn(player, level,
                                player.getItemInHand(hand), hand, blockHit);
                        if (!(result instanceof InteractionResult.Success success)) {
                            break;
                        }
                        if (success.swingSource() == InteractionResult.SwingSource.SERVER) {
                            player.swing(hand);
                        }
                        pack.itemUseCooldown = 3;
                        return true;
                    }
                    case ENTITY -> {
                        player.resetLastActionTime();
                        EntityHitResult entityHit = (EntityHitResult) hit;
                        Entity entity = entityHit.getEntity();
                        boolean handWasEmpty = player.getItemInHand(hand).isEmpty();
                        boolean itemFrameEmpty = entity instanceof ItemFrame frame
                                && frame.getItem().isEmpty();
                        Vec3 relativeHitPos = entityHit.getLocation()
                                .subtract(entity.getX(), entity.getY(), entity.getZ());
                        if (entity.interactAt(player, relativeHitPos, hand).consumesAction()) {
                            pack.itemUseCooldown = 3;
                            return true;
                        }
                        if (!player.interactOn(entity, hand).consumesAction()
                                || (handWasEmpty && itemFrameEmpty)) {
                            break;
                        }
                        pack.itemUseCooldown = 3;
                        return true;
                    }
                    default -> {
                    }
                }
                ItemStack handItem = player.getItemInHand(hand);
                if (!player.gameMode.useItem(player, (ServerLevel) player.level(), handItem, hand)
                        .consumesAction()) {
                    continue;
                }
                pack.itemUseCooldown = 3;
                return true;
            }
            return false;
        }

        private static void handleSpearStab(HeroBotPlayer player) {
            if (player.getAttackStrengthScale(0.5f) < 1.0f) {
                return;
            }
            player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STAB, player.blockPosition(),
                    player.getDirection()));
            player.resetLastActionTime();
        }

        @SuppressWarnings("unused")
        private static void unusedEntityTypeReference() {
            // Keeps the EntityType import honest for future verb ports that filter by type.
            EntityType<?> ignored = EntityType.PLAYER;
        }
    }

    // ------------------------------------------------------------------ action model

    public static final class Action {
        public boolean done = false;
        public final int limit;
        public final int interval;
        public final int offset;
        public final InteractionHand hand;
        private int count;
        private int next;
        private final boolean isContinuous;
        private int ticksRemaining;

        private Action(int limit, int interval, int offset, boolean continuous, InteractionHand hand,
                       int ticksRemaining) {
            this.limit = limit;
            this.interval = interval;
            this.offset = offset;
            this.hand = hand;
            this.next = interval + offset;
            this.isContinuous = continuous;
            this.ticksRemaining = ticksRemaining;
        }

        public static Action once() {
            return new Action(1, 1, 0, false, null, -1);
        }

        public static Action continuous() {
            return new Action(-1, 1, 0, true, null, -1);
        }

        public static Action continuous(int ticks) {
            return new Action(-1, 1, 0, true, null, ticks);
        }

        public static Action interval(int interval) {
            return new Action(-1, interval, 0, false, null, -1);
        }

        public static Action interval(int interval, int ticks) {
            return new Action(-1, interval, 0, false, null, ticks);
        }

        Boolean tick(BotActionPack pack, ActionType type) {
            if (this.ticksRemaining > 0) {
                --this.ticksRemaining;
                if (this.ticksRemaining <= 0) {
                    type.stop(pack.player, this);
                    this.done = true;
                    return null;
                }
            }
            --this.next;
            Boolean cancel = null;
            if (this.next <= 0) {
                if (this.interval == 1 && !this.isContinuous
                        && !(type.preventSpectator && pack.player.isSpectator())) {
                    type.inactiveTick(pack.player, this);
                }
                if (!type.preventSpectator || !pack.player.isSpectator()) {
                    cancel = type.execute(pack.player, this);
                }
                ++this.count;
                if (this.count == this.limit) {
                    type.stop(pack.player, null);
                    this.done = true;
                    return cancel;
                }
                this.next = this.interval;
            } else if (!type.preventSpectator || !pack.player.isSpectator()) {
                type.inactiveTick(pack.player, this);
            }
            return cancel;
        }

        void retry(BotActionPack pack, ActionType type) {
            if (!type.preventSpectator || !pack.player.isSpectator()) {
                type.execute(pack.player, this);
            }
            ++this.count;
            if (this.count == this.limit) {
                type.stop(pack.player, null);
                this.done = true;
            }
        }
    }

    private static final class LookInterpolation {
        final float targetYaw;
        final float targetPitch;
        final float deltaYaw;
        final float deltaPitch;
        int ticksRemaining;

        LookInterpolation(float targetYaw, float targetPitch, float deltaYaw, float deltaPitch,
                          int ticks) {
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.ticksRemaining = ticks;
        }
    }

    private record DelayedAction(long tick, Runnable action) {
    }

    /** Unused-parameter guard for {@code isHittingBlock}, kept for parity with the reference field. */
    public boolean isHittingBlock() {
        return this.isHittingBlock;
    }

    /** Thread-local random used by the reference's ping jitter. */
    static int randomBelow(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    /** Unused: keeps the packet-bot import graph honest if the pack is used without HeroBotPlayer. */
    static Class<?> packetBotType() {
        return PacketBot.class;
    }
}
