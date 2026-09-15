package com.rumilance.practice.herobot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * HeroBot's command surface on Paper — {@code /player}, {@code /playerspawn}, {@code /herobot}.
 *
 * <p>These are the verbs the Quantum datapack drives its bot with
 * ({@code player @s move left}, {@code player @s look at ~ ~1 ~}, {@code player @s attack once},
 * {@code player @s use continuous}, {@code player @s hotbar 6},
 * {@code player @s itemCd shield set 100}, …), so the argument grammar mirrors the reference
 * mod's Brigadier tree verbatim — including subtrees the map does not use yet, because on the
 * reference a function whose line fails to parse is dropped <i>whole</i>. Verbs the reference has
 * and this port does not (inventory, container, skin, path, copycat, delayed) are simply absent;
 * the function loader logs any function that fails to parse, so the gaps stay visible.</p>
 *
 * <p>Registration goes through Paper's Brigadier lifecycle event, which the feasibility spike
 * proved is the only hook that lets a plugin handler receive the map's argument strings.</p>
 */
public final class HeroBotCommands {

    private static final SimpleCommandExceptionType NOT_BOT =
            new SimpleCommandExceptionType(Component.literal("Only bot players can be targeted"));
    private static final SimpleCommandExceptionType NO_BOTS =
            new SimpleCommandExceptionType(Component.literal("No bot players were found"));

    private HeroBotCommands() {
    }

    /**
     * The reference roots ({@code /player}, {@code /playerspawn}, {@code /herobot}, and the
     * syntax extensions {@code /distance} + the port-side {@code /hfilter}).
     *
     * <p>They are registered on the server's own command dispatcher — the tree the map's
     * {@code .mcfunction} lines are compiled against — so a function can call them exactly like
     * the reference Fabric server's {@code /player} tree.</p>
     */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> roots(HeroBotRegistry registry) {
        CommandBuildContext buildContext = buildContext(registry);
        List<LiteralArgumentBuilder<CommandSourceStack>> roots = new ArrayList<>();
        roots.add(player(registry, buildContext));
        roots.add(playerspawn(registry));
        roots.add(herobot());
        // The reference mod's syntax extensions the map's functions rely on: /distance and the
        // port-side /hfilter that stands in for the distanceH=/distanceV= selector options.
        roots.addAll(HeroBotDistanceCommand.roots());
        return List.copyOf(roots);
    }

    /** The item argument is built once, at registration time, from the running server. */
    private static CommandBuildContext buildContext(HeroBotRegistry registry) {
        MinecraftServer server = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer())
                .getServer();
        return CommandBuildContext.simple(server.registryAccess(),
                server.getWorldData().enabledFeatures());
    }

    // ------------------------------------------------------------------ /player

    private static LiteralArgumentBuilder<CommandSourceStack> player(HeroBotRegistry registry,
                                                                    CommandBuildContext buildContext) {
        return Commands.literal("player").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.literal("stop").executes(ctx -> {
                    for (HeroBotPlayer bot : requireBots(ctx)) {
                        bot.actionPack().stopAll();
                    }
                    return 1;
                }))
                .then(action("use", BotActionPack.ActionType.USE))
                .then(action("swing", BotActionPack.ActionType.SWING))
                .then(action("jump", BotActionPack.ActionType.JUMP))
                .then(action("attack", BotActionPack.ActionType.ATTACK))
                .then(action("drop", BotActionPack.ActionType.DROP_ITEM))
                .then(action("dropStack", BotActionPack.ActionType.DROP_STACK))
                .then(action("swapHands", BotActionPack.ActionType.SWAP_HANDS))
                .then(itemCooldown(buildContext))
                .then(Commands.literal("hotbar").then(Commands
                        .argument("slot", IntegerArgumentType.integer(1, 9))
                        .executes(ctx -> {
                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.setSlot(slot);
                            }
                            return slot;
                        })))
                .then(Commands.literal("kill").executes(ctx -> {
                    for (HeroBotPlayer bot : requireBots(ctx)) {
                        bot.killBot();
                    }
                    return 1;
                }))
                .then(Commands.literal("disconnect").executes(ctx -> {
                    List<HeroBotPlayer> bots = List.copyOf(requireBots(ctx));
                    for (HeroBotPlayer bot : bots) {
                        registry.despawn(bot.profileName());
                    }
                    return 1;
                }))
                .then(Commands.literal("sneak").executes(manipulate(pack -> pack.setSneaking(true))))
                .then(Commands.literal("unsneak").executes(manipulate(pack -> pack.setSneaking(false))))
                .then(Commands.literal("sprint").executes(manipulate(pack -> pack.setSprinting(true))))
                .then(Commands.literal("unsprint").executes(manipulate(pack -> pack.setSprinting(false))))
                .then(Commands.literal("move")
                        .executes(manipulate(BotActionPack::stopMovement))
                        .then(Commands.literal("forward").executes(manipulate(pack -> pack.setForward(1.0f))))
                        .then(Commands.literal("backward").executes(manipulate(pack -> pack.setForward(-1.0f))))
                        .then(Commands.literal("left").executes(manipulate(pack -> pack.setStrafing(1.0f))))
                        .then(Commands.literal("right").executes(manipulate(pack -> pack.setStrafing(-1.0f)))))
                .then(look())
                .then(Commands.literal("ping")
                        .executes(ctx -> {
                            int value = 0;
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                value = bot.ping;
                            }
                            return value;
                        })
                        .then(Commands.argument("value", IntegerArgumentType.integer(0)).executes(ctx -> {
                            int value = IntegerArgumentType.getInteger(ctx, "value");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.ping = value;
                            }
                            return value;
                        })))
                .then(Commands.literal("autojump")
                        .executes(ctx -> autoJump(ctx, true))
                        .then(Commands.literal("true").executes(ctx -> autoJump(ctx, true)))
                        .then(Commands.literal("false").executes(ctx -> autoJump(ctx, false))))
                .then(Commands.literal("handedness")
                        .then(Commands.literal("left").executes(ctx -> handedness(ctx, true)))
                        .then(Commands.literal("right").executes(ctx -> handedness(ctx, false)))));
    }

    /** {@code player <targets> <verb> [once | continuous [ticks] | interval <interval> [ticks]]}. */
    private static LiteralArgumentBuilder<CommandSourceStack> action(String name,
                                                                    BotActionPack.ActionType type) {
        return Commands.literal(name)
                .executes(manipulate(pack -> pack.stop(type)))
                .then(Commands.literal("once")
                        .executes(manipulate(pack -> pack.start(type, BotActionPack.Action.once()))))
                .then(Commands.literal("continuous")
                        .executes(manipulate(pack -> pack.start(type, BotActionPack.Action.continuous())))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1)).executes(ctx -> {
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.actionPack().start(type, ticks == 1
                                        ? BotActionPack.Action.once()
                                        : BotActionPack.Action.continuous(ticks));
                            }
                            return 1;
                        })))
                .then(Commands.literal("interval").then(Commands
                        .argument("interval", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int interval = IntegerArgumentType.getInteger(ctx, "interval");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.actionPack().start(type, BotActionPack.Action.interval(interval));
                            }
                            return 1;
                        })
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1)).executes(ctx -> {
                            int interval = IntegerArgumentType.getInteger(ctx, "interval");
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.actionPack().start(type,
                                        BotActionPack.Action.interval(interval, ticks));
                            }
                            return 1;
                        }))));
    }

    /** {@code player <targets> itemCd [<item> [reset | set [ticks]]]}. */
    private static LiteralArgumentBuilder<CommandSourceStack> itemCooldown(
            CommandBuildContext buildContext) {
        return Commands.literal("itemCd")
                .executes(ctx -> {
                    int cleared = 0;
                    for (HeroBotPlayer bot : requireBots(ctx)) {
                        cleared += clearCooldowns(bot);
                    }
                    final int result = cleared;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Cleared " + result + " item cooldown" + (result == 1 ? "" : "s")), false);
                    return result;
                })
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(ctx -> {
                            Item item = ItemArgument.getItem(ctx, "item").getItem();
                            int last = 0;
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                last = remainingCooldown(bot, item);
                            }
                            return last;
                        })
                        .then(Commands.literal("reset").executes(ctx -> {
                            Item item = ItemArgument.getItem(ctx, "item").getItem();
                            int count = 0;
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.getCooldowns().removeCooldown(cooldownGroup(bot, item));
                                count++;
                            }
                            return count;
                        }))
                        .then(Commands.literal("set")
                                .executes(ctx -> {
                                    Item item = ItemArgument.getItem(ctx, "item").getItem();
                                    int count = 0;
                                    for (HeroBotPlayer bot : requireBots(ctx)) {
                                        setDefaultCooldown(bot, item);
                                        count++;
                                    }
                                    return count;
                                })
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            Item item = ItemArgument.getItem(ctx, "item").getItem();
                                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                            int count = 0;
                                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                                bot.getCooldowns().addCooldown(cooldownGroup(bot, item), ticks);
                                                count++;
                                            }
                                            return count;
                                        }))));
    }

    private static Identifier cooldownGroup(HeroBotPlayer bot, Item item) {
        return bot.getCooldowns().getCooldownGroup(new ItemStack(item));
    }

    private static int remainingCooldown(HeroBotPlayer bot, Item item) {
        var instance = bot.getCooldowns().cooldowns.get(cooldownGroup(bot, item));
        return instance == null ? 0 : Math.max(0, instance.endTime() - bot.getCooldowns().tickCount);
    }

    private static int clearCooldowns(HeroBotPlayer bot) {
        var cooldowns = bot.getCooldowns();
        List<Identifier> groups = new ArrayList<>(cooldowns.cooldowns.keySet());
        for (Identifier group : groups) {
            cooldowns.removeCooldown(group);
        }
        return groups.size();
    }

    private static void setDefaultCooldown(HeroBotPlayer bot, Item item) {
        UseCooldown useCooldown = new ItemStack(item).get(DataComponents.USE_COOLDOWN);
        if (useCooldown == null) {
            return;
        }
        bot.getCooldowns().addCooldown(cooldownGroup(bot, item), (int) Math.ceil(useCooldown.seconds() * 20.0));
    }

    // ------------------------------------------------------------------ /player … look

    private static LiteralArgumentBuilder<CommandSourceStack> look() {
        LiteralArgumentBuilder<CommandSourceStack> look = Commands.literal("look");
        look = look.then(directionLook("north", Direction.NORTH));
        look = look.then(directionLook("south", Direction.SOUTH));
        look = look.then(directionLook("east", Direction.EAST));
        look = look.then(directionLook("west", Direction.WEST));
        look = look.then(directionLook("up", Direction.UP));
        look = look.then(directionLook("down", Direction.DOWN));
        look = look.then(relativeLook("left", -90.0f));
        look = look.then(relativeLook("right", 90.0f));
        look = look.then(relativeLook("back", 180.0f));
        look = look.then(Commands.literal("relative").then(Commands
                .argument("rotation", RotationArgument.rotation())
                .executes(ctx -> applyRotation(ctx, "rotation", 0))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> applyRotation(ctx, "rotation",
                                IntegerArgumentType.getInteger(ctx, "ticks")))))));
        look = look.then(Commands.literal("random").executes(manipulate(pack -> {
            float yaw = ThreadLocalRandom.current().nextFloat() * 360.0f - 180.0f;
            float pitch = ThreadLocalRandom.current().nextFloat() * 180.0f - 90.0f;
            pack.look(yaw, pitch);
        })));
        look = look.then(Commands.literal("upon").then(Commands
                .argument("entity", EntityArgument.entity())
                .executes(ctx -> lookUpon(ctx, LookMode.EYES, 0))
                .then(lookUponMode("eyes", LookMode.EYES))
                .then(lookUponMode("feet", LookMode.FEET))
                .then(lookUponMode("closest", LookMode.CLOSEST))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> lookUpon(ctx, LookMode.EYES,
                                IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(offsetBranch((ctx, ticks) -> lookUpon(ctx, LookMode.CLOSEST, ticks)))));
        look = look.then(Commands.literal("at").then(Commands
                .argument("position", Vec3Argument.vec3())
                .executes(ctx -> lookAt(ctx, 0))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> lookAt(ctx, IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(offsetBranch(HeroBotCommands::lookAt))));
        look = look.then(Commands.literal("direction").then(Commands
                .argument("direction", RotationArgument.rotation())
                .executes(ctx -> applyRotation(ctx, "direction", 0))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> applyRotation(ctx, "direction",
                                IntegerArgumentType.getInteger(ctx, "ticks")))))
                .then(offsetBranch((ctx, ticks) -> applyRotation(ctx, "direction", ticks)))));
        return look;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> directionLook(String name, Direction direction) {
        return Commands.literal(name)
                .executes(manipulate(pack -> pack.look(direction)))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1)).executes(ctx -> {
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.actionPack().look(direction, ticks);
                            }
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> relativeLook(String name, float yaw) {
        return Commands.literal(name)
                .executes(manipulate(pack -> pack.turn(yaw, 0.0f)))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1)).executes(ctx -> {
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            for (HeroBotPlayer bot : requireBots(ctx)) {
                                bot.actionPack().turn(yaw, 0.0f, ticks);
                            }
                            return 1;
                        })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lookUponMode(String name, LookMode mode) {
        return Commands.literal(name)
                .executes(ctx -> lookUpon(ctx, mode, 0))
                .then(Commands.literal("delta").then(Commands
                        .argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> lookUpon(ctx, mode, IntegerArgumentType.getInteger(ctx, "ticks")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> offsetBranch(LookExecutor base) {
        return Commands.literal("offset").then(Commands
                .argument("yawOffset", DoubleArgumentType.doubleArg(0.0, 360.0))
                .then(Commands.argument("pitchOffset", DoubleArgumentType.doubleArg(0.0, 180.0))
                        .executes(ctx -> withOffset(ctx, base, 0))
                        .then(Commands.literal("delta").then(Commands
                                .argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> withOffset(ctx, base,
                                        IntegerArgumentType.getInteger(ctx, "ticks")))))));
    }

    private static int withOffset(CommandContext<CommandSourceStack> ctx, LookExecutor base, int ticks)
            throws CommandSyntaxException {
        double yawRange = DoubleArgumentType.getDouble(ctx, "yawOffset");
        double pitchRange = DoubleArgumentType.getDouble(ctx, "pitchOffset");
        int result = base.execute(ctx, ticks);
        for (HeroBotPlayer bot : requireBots(ctx)) {
            float yawOffset = (float) (ThreadLocalRandom.current().nextDouble() * 2.0 * yawRange - yawRange);
            float pitchOffset = (float) (ThreadLocalRandom.current().nextDouble() * 2.0 * pitchRange
                    - pitchRange);
            bot.actionPack().lookInterpolated(bot.getYRot() + yawOffset,
                    bot.getXRot() + pitchOffset, ticks);
        }
        return result;
    }

    private static int lookAt(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
        Vec3 position = Vec3Argument.getVec3(ctx, "position");
        for (HeroBotPlayer bot : requireBots(ctx)) {
            bot.actionPack().lookAt(position, ticks);
        }
        return 1;
    }

    /** {@code look relative <rotation>} / {@code look direction <rotation>} — resolved per bot. */
    private static int applyRotation(CommandContext<CommandSourceStack> ctx, String argument, int ticks)
            throws CommandSyntaxException {
        for (HeroBotPlayer bot : requireBots(ctx)) {
            Vec2 rotation = RotationArgument.getRotation(ctx, argument)
                    .getRotation(ctx.getSource().withRotation(new Vec2(bot.getXRot(), bot.getYRot())));
            bot.actionPack().look(rotation.y, rotation.x, ticks);
        }
        return 1;
    }

    private static int lookUpon(CommandContext<CommandSourceStack> ctx, LookMode mode, int ticks)
            throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(ctx, "entity");
        for (HeroBotPlayer bot : requireBots(ctx)) {
            Vec3 lookTarget = switch (mode) {
                case EYES -> target.getEyePosition();
                case FEET -> target.position();
                case CLOSEST -> closestPointToBox(bot.getEyePosition(), target.getBoundingBox());
            };
            bot.actionPack().lookAt(lookTarget, ticks);
        }
        return 1;
    }

    private static Vec3 closestPointToBox(Vec3 eye, AABB box) {
        return new Vec3(Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
    }

    // ------------------------------------------------------------------ /playerspawn + /herobot

    /**
     * {@code /playerspawn <name> [at <pos> [facing <rotation> | <cardinal>] [in <gamemode>
     * [on <dimension>]]]} — the reference mod's tree verbatim (herobot's
     * {@code PlayerSpawnCommand}), because {@code quantum:botspawning} runs
     * {@code playerspawn quantumbot at -646 57 88 facing 0 0 in creative on minecraft:overworld}
     * from inside a function and every node on that path has to exist for the function to parse.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> playerspawn(HeroBotRegistry registry) {
        return Commands.literal("playerspawn")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> spawnBot(ctx, registry))
                        .then(Commands.literal("at").then(Commands
                                .argument("position", Vec3Argument.vec3())
                                .executes(ctx -> spawnBot(ctx, registry))
                                .then(Commands.literal("facing").then(Commands
                                        .argument("rotation", RotationArgument.rotation())
                                        .executes(ctx -> spawnBot(ctx, registry))
                                        .then(inGamemode(registry))))
                                .then(cardinal(registry))))
                        .then(cardinal(registry)));
    }

    /** The reference's {@code in <gamemode> [on <dimension>]} tail. */
    private static LiteralArgumentBuilder<CommandSourceStack> inGamemode(HeroBotRegistry registry) {
        return Commands.literal("in").then(Commands
                .argument("gamemode", GameModeArgument.gameMode())
                .executes(ctx -> spawnBot(ctx, registry))
                .then(Commands.literal("on").then(Commands
                        .argument("dimension", net.minecraft.commands.arguments.DimensionArgument.dimension())
                        .executes(ctx -> spawnBot(ctx, registry)))));
    }

    /** The reference's {@code <north|south|east|west|up|down|"~ ~">} spawn facing. */
    private static LiteralArgumentBuilder<CommandSourceStack> cardinal(HeroBotRegistry registry) {
        return Commands.literal("cardinal").then(Commands
                .argument("cardinal", StringArgumentType.word())
                .executes(ctx -> spawnBot(ctx, registry))
                .then(inGamemode(registry)));
    }

    private static int spawnBot(CommandContext<CommandSourceStack> ctx, HeroBotRegistry registry)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "player");
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = ((org.bukkit.craftbukkit.CraftServer) org.bukkit.Bukkit.getServer())
                .getServer();
        // The reference's guards: a name that is already spawning, an online player with that name,
        // or a name longer than the server allows all make the command a no-op.
        if (registry.isSpawning(name) || server.getPlayerList().getPlayerByName(name) != null) {
            return 0;
        }
        int maxNameLength = server.getMaxPlayers() >= 0 ? 16 : 40;
        if (name.length() > maxNameLength) {
            return 0;
        }
        Vec3 position = argumentOrNull(ctx, "position",
                net.minecraft.commands.arguments.coordinates.Coordinates.class) != null
                ? Vec3Argument.getVec3(ctx, "position")
                : source.getPosition();
        if (!net.minecraft.world.level.Level.isInSpawnableBounds(
                BlockPos.containing(position))) {
            return 0;
        }
        Vec2 rotation = source.getRotation();
        if (argumentOrNull(ctx, "rotation",
                net.minecraft.commands.arguments.coordinates.Coordinates.class) != null) {
            rotation = RotationArgument.getRotation(ctx, "rotation").getRotation(source);
        } else {
            String cardinal = argumentOrNull(ctx, "cardinal", String.class);
            if (cardinal != null) {
                rotation = cardinalRotation(cardinal, source.getRotation());
            }
        }
        GameType mode = argumentOrNull(ctx, "gamemode", Object.class) != null
                ? GameModeArgument.getGameMode(ctx, "gamemode")
                : GameType.CREATIVE;
        net.minecraft.server.level.ServerLevel level = argumentOrNull(ctx, "dimension",
                Identifier.class) != null
                ? net.minecraft.commands.arguments.DimensionArgument.getDimension(ctx, "dimension")
                : source.getLevel();
        boolean flying;
        if (mode == GameType.SPECTATOR) {
            flying = true;
        } else if (mode.isSurvival()) {
            flying = false;
        } else {
            net.minecraft.world.entity.Entity executor = source.getEntity();
            flying = executor instanceof ServerPlayer player && player.getAbilities().flying;
        }
        org.bukkit.Location location = new org.bukkit.Location(level.getWorld(),
                position.x, position.y, position.z, rotation.y, rotation.x);
        HeroBotPlayer bot = registry.spawn(name, location, rotation.y, rotation.x, mode, null, flying);
        source.sendSuccess(() -> Component.literal("Spawned " + bot.profileName()), false);
        return 1;
    }

    /** The reference's {@code cardinalRotation}: south 0, west 90, north 180, east -90, up/down. */
    private static Vec2 cardinalRotation(String cardinal, Vec2 fallback) {
        return switch (cardinal) {
            case "south" -> new Vec2(0.0f, 0.0f);
            case "west" -> new Vec2(0.0f, 90.0f);
            case "north" -> new Vec2(0.0f, 180.0f);
            case "east" -> new Vec2(0.0f, -90.0f);
            case "up" -> new Vec2(-90.0f, fallback.y);
            case "down" -> new Vec2(90.0f, fallback.y);
            case "~ ~" -> fallback;
            default -> fallback;
        };
    }

    private static <T> T argumentOrNull(CommandContext<CommandSourceStack> ctx, String name, Class<T> type) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> herobot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("herobot");
        root = root.then(Commands.argument("rule", StringArgumentType.word())
                .then(Commands.argument("value", StringArgumentType.word())
                        .executes(ctx -> rule(ctx, false))
                        .then(Commands.literal("temp").executes(ctx -> rule(ctx, false)))
                        .then(Commands.literal("perm").executes(ctx -> rule(ctx, true)))
                        .then(Commands.literal("temp").then(Commands.literal("world")
                                .executes(ctx -> rule(ctx, false))))
                        .then(Commands.literal("perm").then(Commands.literal("world")
                                .executes(ctx -> rule(ctx, true)))))
                .then(Commands.literal("reset").executes(ctx -> {
                    HeroBotSettings.resetOne(StringArgumentType.getString(ctx, "rule"));
                    return 1;
                })));
        return root;
    }

    private static int rule(CommandContext<CommandSourceStack> ctx, boolean permanent) {
        String rule = StringArgumentType.getString(ctx, "rule");
        String value = StringArgumentType.getString(ctx, "value");
        boolean known = HeroBotSettings.apply(rule, value, permanent);
        if (!known) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "herobot: rule '" + rule + "' has no Paper equivalent yet (ignored)"), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ helpers

    private static int autoJump(CommandContext<CommandSourceStack> ctx, boolean value)
            throws CommandSyntaxException {
        for (HeroBotPlayer bot : requireBots(ctx)) {
            bot.actionPack().autoJump = value;
        }
        return 1;
    }

    private static int handedness(CommandContext<CommandSourceStack> ctx, boolean left)
            throws CommandSyntaxException {
        for (HeroBotPlayer bot : requireBots(ctx)) {
            bot.setMainArm(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        }
        return 1;
    }

    /** The reference's {@code CommandHelper.requireBotTargets}: bots only, never empty. */
    private static List<HeroBotPlayer> requireBots(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        List<HeroBotPlayer> bots = new ArrayList<>();
        for (ServerPlayer player : players) {
            if (!(player instanceof HeroBotPlayer bot)) {
                throw NOT_BOT.create();
            }
            bots.add(bot);
        }
        if (bots.isEmpty()) {
            throw NO_BOTS.create();
        }
        return bots;
    }

    private static Command<CommandSourceStack> manipulate(Consumer<BotActionPack> action) {
        return ctx -> {
            for (HeroBotPlayer bot : requireBots(ctx)) {
                action.accept(bot.actionPack());
            }
            return 1;
        };
    }

    /** Lower-case helper for the {@code /herobot} rule table (also used by the runtime). */
    static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private enum LookMode {
        EYES,
        FEET,
        CLOSEST
    }

    @FunctionalInterface
    private interface LookExecutor {
        int execute(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException;
    }
}
