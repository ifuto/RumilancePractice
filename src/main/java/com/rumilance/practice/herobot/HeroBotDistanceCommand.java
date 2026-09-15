package com.rumilance.practice.herobot;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The reference mod's <b>syntax extensions</b>, which the map's functions use and which vanilla
 * therefore rejects on Paper (they are the only five {@code .mcfunction} files of the Quantum pack
 * that fail to compile without them):
 *
 * <ul>
 *   <li>{@code distance from <pos|entity> to <pos|entity|toHitbox …> [horizontal [e n]] [vertical
 *       [e n]] [e n]} — {@code herobot}'s {@code DistanceCommand} + {@code DistanceCalculator}
 *       (used by {@code quantum:allstats/newstats} and {@code quantum:allstats/advancestats} to
 *       store the distance to the opponent in a score).</li>
 *   <li>{@code hfilter <selector> h <range> [v <range>]} — not a reference verb at all: it is the
 *       runtime half of the port's stand-in for the mod's entity-selector options
 *       {@code distanceH=}/{@code distanceV=} (see {@link #hfilter()}).</li>
 * </ul>
 *
 * <p>{@code distanceH=…}/{@code distanceV=…} cannot be added to Paper's selector parser without a
 * mixin (that is how the reference gets them: {@code EntitySelectorOptionsMixin} +
 * {@code EntitySelectorMixin} filter the result list by
 * {@code (dx²+dz²)} / {@code |dy|} against the source position). The port therefore rewrites the
 * handful of magic-function lines that use those options into an equivalent
 * {@code hfilter}-guarded pair at compile time — same numbers, same filter order, no mixin
 * needed. See {@code HeroBotLineRewriter}.</p>
 */
public final class HeroBotDistanceCommand {

    /** Name of the objective the rewritten lines park their match count in. */
    public static final String TEMP_OBJECTIVE = "quantum_tmp";
    /** Fake player holding that count (one at a time — functions run on the server thread). */
    public static final String TEMP_HOLDER = ".qd";

    private HeroBotDistanceCommand() {
    }

    /** The roots to register: {@code /distance} (reference) and {@code /hfilter} (port support). */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> roots() {
        return List.of(distance(), hfilter());
    }

    // ------------------------------------------------------------------ /distance

    private static LiteralArgumentBuilder<CommandSourceStack> distance() {
        return Commands.literal("distance")
                .then(fromSubtree("from", false))
                .then(fromSubtree("fromHitbox", true));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> fromSubtree(String name, boolean hitbox) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name);
        if (hitbox) {
            literal.then(toSubtree(Commands.argument("fromHitboxBlock", BlockPosArgument.blockPos()),
                    ctx -> new AABB(BlockPosArgument.getBlockPos(ctx, "fromHitboxBlock"))));
            literal.then(toSubtree(Commands.argument("fromHitboxEntity", EntityArgument.entity()),
                    ctx -> EntityArgument.getEntity(ctx, "fromHitboxEntity").getBoundingBox()));
        } else {
            literal.then(toSubtree(Commands.argument("fromPos", Vec3Argument.vec3()),
                    ctx -> point(Vec3Argument.getVec3(ctx, "fromPos"))));
            literal.then(toSubtree(Commands.argument("fromEntity", EntityArgument.entity()),
                    ctx -> point(EntityArgument.getEntity(ctx, "fromEntity").position())));
        }
        return literal;
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> toSubtree(
            RequiredArgumentBuilder<CommandSourceStack, T> argument, ShapeSupplier from) {
        argument.then(Commands.literal("to")
                .then(withExecutors(Commands.argument("toPos", Vec3Argument.vec3()), from,
                        ctx -> point(Vec3Argument.getVec3(ctx, "toPos"))))
                .then(withExecutors(Commands.argument("toEntity", EntityArgument.entity()), from,
                        ctx -> point(EntityArgument.getEntity(ctx, "toEntity").position()))));
        argument.then(Commands.literal("toHitbox")
                .then(withExecutors(Commands.argument("toHitboxBlock", BlockPosArgument.blockPos()),
                        from, ctx -> new AABB(BlockPosArgument.getBlockPos(ctx, "toHitboxBlock"))))
                .then(withExecutors(Commands.argument("toHitboxEntity", EntityArgument.entity()),
                        from,
                        ctx -> EntityArgument.getEntity(ctx, "toHitboxEntity").getBoundingBox())));
        return argument;
    }

    private static <T> ArgumentBuilder<CommandSourceStack, ?> withExecutors(
            RequiredArgumentBuilder<CommandSourceStack, T> argument, ShapeSupplier from,
            ShapeSupplier to) {
        argument.executes(ctx -> measure(ctx, from, to, 0, Axis.SPHERICAL));
        argument.then(Commands.literal("e").then(Commands
                .argument("exp", IntegerArgumentType.integer(0))
                .executes(ctx -> measure(ctx, from, to,
                        IntegerArgumentType.getInteger(ctx, "exp"), Axis.SPHERICAL))));
        argument.then(Commands.literal("horizontal")
                .executes(ctx -> measure(ctx, from, to, 0, Axis.HORIZONTAL))
                .then(Commands.literal("e").then(Commands
                        .argument("exp", IntegerArgumentType.integer(0))
                        .executes(ctx -> measure(ctx, from, to,
                                IntegerArgumentType.getInteger(ctx, "exp"), Axis.HORIZONTAL)))));
        argument.then(Commands.literal("vertical")
                .executes(ctx -> measure(ctx, from, to, 0, Axis.VERTICAL))
                .then(Commands.literal("e").then(Commands
                        .argument("exp", IntegerArgumentType.integer(0))
                        .executes(ctx -> measure(ctx, from, to,
                                IntegerArgumentType.getInteger(ctx, "exp"), Axis.VERTICAL)))));
        return argument;
    }

    private enum Axis { SPHERICAL, HORIZONTAL, VERTICAL }

    /** Reference semantics: box-to-box distance, scaled by 10^exp and rounded, plus its feedback. */
    private static int measure(CommandContext<CommandSourceStack> ctx, ShapeSupplier from,
                               ShapeSupplier to, int exponent, Axis axis)
            throws CommandSyntaxException {
        Vec3[] closest = closestPointsBetween(from.get(ctx), to.get(ctx));
        Vec3 first = closest[0];
        Vec3 second = closest[1];
        if (axis == Axis.HORIZONTAL) {
            first = new Vec3(first.x, 0.0, first.z);
            second = new Vec3(second.x, 0.0, second.z);
        } else if (axis == Axis.VERTICAL) {
            first = new Vec3(0.0, first.y, 0.0);
            second = new Vec3(0.0, second.y, 0.0);
        }
        return distance(ctx.getSource(), first, second, exponent);
    }

    private static int distance(CommandSourceStack source, Vec3 pos1, Vec3 pos2, int exponent) {
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        double spherical = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int scale = exponent <= 0 ? 1 : (int) Math.pow(10.0, exponent);
        int result = (int) Math.round(spherical * scale);
        for (Component line : feedback(pos1, pos2, result)) {
            source.sendSuccess(() -> line, false);
        }
        return result;
    }

    /** The reference's four feedback lines ({@code DistanceCalculator#distanceBetweenPoints}). */
    private static List<Component> feedback(Vec3 pos1, Vec3 pos2, int result) {
        double dx = Math.abs((float) pos1.x - (float) pos2.x);
        double dy = Math.abs((float) pos1.y - (float) pos2.y);
        double dz = Math.abs((float) pos1.z - (float) pos2.z);
        double manhattan = dx + dy + dz;
        double spherical = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double cylindrical = Math.sqrt(dx * dx + dz * dz);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Distance between " + format(pos1) + " and " + format(pos2)
                + ":"));
        lines.add(Component.literal(" - Spherical: " + String.format("%.2f", spherical))
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xAAFFFF))));
        lines.add(Component.literal("   - Cylindrical: " + String.format("%.2f", cylindrical)));
        lines.add(Component.literal("   - Manhattan: " + String.format("%.1f", manhattan)));
        lines.add(Component.literal("> Output: " + result)
                .withStyle(style -> style.withColor(TextColor.fromRgb(0xFFFFAA))));
        return lines;
    }

    private static String format(Vec3 pos) {
        return String.format("(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

    /** {@code DistanceCalculator#closestPointOnHitbox}: clamp the point into the box, axis by axis. */
    static Vec3 closestPointOnHitbox(AABB box, Vec3 point) {
        return new Vec3(Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ));
    }

    /** {@code DistanceCalculator#closestPointsBetween}: three refinement passes, same as upstream. */
    static Vec3[] closestPointsBetween(AABB from, AABB to) {
        Vec3 centerFrom = from.getCenter();
        Vec3 onTo = closestPointOnHitbox(to, centerFrom);
        Vec3 onFrom = closestPointOnHitbox(from, onTo);
        Vec3 onToFinal = closestPointOnHitbox(to, onFrom);
        return new Vec3[]{onFrom, onToFinal};
    }

    private static AABB point(Vec3 vec) {
        return new AABB(vec, vec);
    }

    @FunctionalInterface
    private interface ShapeSupplier {
        AABB get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    // ------------------------------------------------------------------ /hfilter

    /**
     * The port's stand-in for the reference's {@code distanceH=}/{@code distanceV=} selector
     * options: {@code hfilter <selector> h <range> [v <range>]} returns how many of the entities
     * the selector matched survive the horizontal / vertical distance filter the reference mod
     * applies in {@code EntitySelectorMixin} (origin = the command source position, horizontal =
     * {@code dx²+dz²} against {@code MinMaxBounds.Doubles#matchesSqr}, vertical = {@code |dy|}
     * against {@code matches}).
     */
    private static LiteralArgumentBuilder<CommandSourceStack> hfilter() {
        return Commands.literal("hfilter")
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.literal("h").then(Commands
                                .argument("hrange", StringArgumentType.word())
                                .executes(HeroBotDistanceCommand::filter)
                                .then(Commands.literal("v").then(Commands
                                        .argument("vrange", StringArgumentType.word())
                                        .executes(HeroBotDistanceCommand::filter)))))
                        .then(Commands.literal("v").then(Commands
                                .argument("vrange", StringArgumentType.word())
                                .executes(HeroBotDistanceCommand::filter))));
    }

    private static int filter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinMaxBounds.Doubles horizontal = optionalRange(ctx, "hrange");
        MinMaxBounds.Doubles vertical = optionalRange(ctx, "vrange");
        Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "targets");
        Vec3 origin = ctx.getSource().getPosition();
        int matched = 0;
        for (Entity entity : entities) {
            double dx = entity.getX() - origin.x;
            double dy = entity.getY() - origin.y;
            double dz = entity.getZ() - origin.z;
            if (horizontal != null && !horizontal.matchesSqr(dx * dx + dz * dz)) {
                continue;
            }
            if (vertical != null && !vertical.matches(Math.abs(dy))) {
                continue;
            }
            matched++;
        }
        return matched;
    }

    /** {@code null} when the branch that was taken does not carry this bounds argument. */
    private static MinMaxBounds.Doubles optionalRange(CommandContext<CommandSourceStack> ctx,
                                                      String name) throws CommandSyntaxException {
        String text;
        try {
            text = StringArgumentType.getString(ctx, name);
        } catch (IllegalArgumentException absent) {
            return null;
        }
        return bounds(text);
    }

    /** Parses a selector bounds string ({@code ..30}, {@code 1..5}, {@code 30..}) like vanilla. */
    static MinMaxBounds.Doubles bounds(String raw) throws CommandSyntaxException {
        String text = raw.toLowerCase(Locale.ROOT);
        StringReader reader = new StringReader(text);
        MinMaxBounds.Doubles bounds = MinMaxBounds.Doubles.fromReader(reader);
        if (reader.canRead()) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                    .create("trailing input after the bounds: " + text);
        }
        return bounds;
    }
}
