package com.rumilance.practice.command;

import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.arena.fawe.FaweBridge;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.gui.menus.KitAdminGui;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaKitAdminCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> FORBIDDEN = Set.of(
            "duel", "ffa", "pos1", "pos2", "p1", "p2", "create", "draft", "build", "delete", "show", "list",
            "info", "clone", "pool", "instance", "true", "false", "flat", "bumpy", "crystal", "netherite"
    );

    private final ConfigService configService;
    private final ArenaTemplateStore arenaStore;
    private final ArenaService arenaService;
    private final KitService kitService;
    private final QueueService queueService;
    private final FaweBridge faweBridge;
    private final File schematicRoot;
    private final SoundService soundService;
    private final KitAdminGui kitAdminGui;
    private final Map<String, ArenaTemplate> drafts = new ConcurrentHashMap<>();

    public ArenaKitAdminCommand(
            ConfigService configService,
            ArenaTemplateStore arenaStore,
            ArenaService arenaService,
            KitService kitService,
            QueueService queueService,
            FaweBridge faweBridge,
            File schematicRoot,
            SoundService soundService,
            KitAdminGui kitAdminGui
    ) {
        this.configService = configService;
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
        this.kitService = kitService;
        this.queueService = queueService;
        this.faweBridge = faweBridge;
        this.schematicRoot = schematicRoot;
        this.soundService = soundService;
        this.kitAdminGui = kitAdminGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("kit")) {
            return handleKit(sender, args);
        }
        if (name.equals("toggle")) {
            return handleToggle(sender, args);
        }
        return handleArena(sender, args);
    }

    private boolean handleToggle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("/toggle <queue|map> <enable|disable> <id>", NamedTextColor.YELLOW));
            return true;
        }
        boolean enable = args[1].equalsIgnoreCase("enable");
        if (args[0].equalsIgnoreCase("queue")) {
            kitService.setQueueEnabled(args[2], enable);
            sender.sendMessage(Component.text("Queue " + args[2] + " " + (enable ? "enabled" : "disabled"),
                    NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("map")) {
            // map enable/disable via arenas.yml enabled flag reload path
            arenaStore.setEnabled(args[2], enable);
            arenaService.setTemplates(arenaStore.templates());
            sender.sendMessage(Component.text("Map " + args[2] + " " + (enable ? "enabled" : "disabled"),
                    NamedTextColor.GREEN));
            return true;
        }
        return true;
    }

    private boolean handleKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            // No args -> open the Kit Management GUI (clearer than the command flags).
            kitAdminGui.open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "gui" -> {
                kitAdminGui.open(player);
                yield true;
            }
            case "help" -> {
                player.sendMessage(Component.text("/kit (no args) - open the Kit Management GUI", NamedTextColor.AQUA));
                player.sendMessage(Component.text("/kit create <name> | list | info <name> | enable/disable <name>", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit delete <name> | timeout <name> <sec>", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit adventure <name> <on|off> - force Adventure mode", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit <flag> <name> <on|off> - regen/food/place/break/pearl/totem/shield", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit order <name> <up|down> - GUI表示順を移動 (GUIでShift+クリックでも可)", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit rename <nowname> <newname> - 改名 (入力した大文字小文字がそのまま表示名に)", NamedTextColor.GRAY));
                player.sendMessage(Component.text("/kit arena <kit> <arena|any> - キットの使用アリーナを1つに固定", NamedTextColor.GRAY));
                yield true;
            }
            case "arena" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("/kit arena <kit> <arena|any>  - キットに使用アリーナを1つ固定 (any=解除)", NamedTextColor.YELLOW));
                    yield true;
                }
                var kitOpt = kitService.get(args[1]);
                if (kitOpt.isEmpty()) {
                    player.sendMessage(Component.text("Unknown kit: " + args[1], NamedTextColor.RED));
                    yield true;
                }
                if (args[2].equalsIgnoreCase("any") || args[2].equalsIgnoreCase("none")) {
                    kitService.save(kitOpt.get().toBuilder().arenaName("").build());
                    player.sendMessage(Component.text("Kit '" + args[1] + "' の固定アリーナを解除しました。", NamedTextColor.GREEN));
                    yield true;
                }
                boolean exists = arenaStore.templates().stream()
                        .anyMatch(t -> t.name().equalsIgnoreCase(args[2]));
                if (!exists) {
                    player.sendMessage(Component.text("Unknown arena: " + args[2], NamedTextColor.RED));
                    yield true;
                }
                kitService.save(kitOpt.get().toBuilder().arenaName(args[2].toLowerCase(Locale.ROOT)).build());
                player.sendMessage(Component.text("Kit '" + args[1] + "' は常にアリーナ '" + args[2] + "' を使用します。", NamedTextColor.GREEN));
                yield true;
            }
            case "rename" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("/kit rename <nowname> <newname>", NamedTextColor.YELLOW));
                    yield true;
                }
                // args[2] keeps the typed casing: it becomes the display name verbatim.
                KitService.RenameResult r = kitService.rename(args[1], args[2]);
                player.sendMessage(switch (r) {
                    case OK -> Component.text("Kit renamed: " + args[1] + " -> " + args[2], NamedTextColor.GREEN);
                    case NOT_FOUND -> Component.text("Unknown kit: " + args[1], NamedTextColor.RED);
                    case TARGET_EXISTS -> Component.text("A kit named '" + args[2] + "' already exists.", NamedTextColor.RED);
                });
                yield true;
            }
            case "order" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("/kit order <name> <up|down>", NamedTextColor.YELLOW));
                    yield true;
                }
                boolean up = args[2].equalsIgnoreCase("up");
                boolean moved = kitService.move(args[1], up);
                player.sendMessage(moved
                        ? Component.text("Kit '" + args[1] + "' moved " + (up ? "up" : "down") + ".", NamedTextColor.GREEN)
                        : Component.text("Could not move kit (unknown name or already at the edge).", NamedTextColor.RED));
                yield true;
            }
            case "adventure" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("/kit adventure <name> <on|off>", NamedTextColor.YELLOW));
                    yield true;
                }
                boolean value = parseToggle(args[2]);
                boolean[] updated = {false};
                kitService.get(args[1]).ifPresent(k -> {
                    kitService.save(k.toBuilder().forceAdventure(value).build());
                    updated[0] = true;
                });
                player.sendMessage(Component.text(updated[0] ? "Adventure " + (value ? "ON" : "OFF") + " for " + args[1] : "Unknown kit.",
                        updated[0] ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "create", "overwrite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("/kit create <name>", NamedTextColor.YELLOW));
                    yield true;
                }
                // Pass the RAW name: KitService lowercases the storage key itself but keeps
                // the typed casing as the display name (for gui.kit-name-case: KEEP).
                KitDefinition kit = kitService.createFromPlayer(player, args[1]);
                player.sendMessage(Component.text("Kit saved: " + kit.displayName(), NamedTextColor.GREEN));
                yield true;
            }
            case "list" -> {
                kitService.all().forEach(k -> player.sendMessage(Component.text(
                        k.name() + " enabled=" + k.enabled(), NamedTextColor.AQUA)));
                yield true;
            }
            case "info" -> {
                if (args.length < 2) {
                    yield true;
                }
                kitService.get(args[1]).ifPresentOrElse(
                        k -> player.sendMessage(Component.text(k.toString(), NamedTextColor.GRAY)),
                        () -> player.sendMessage(Component.text("Unknown kit.", NamedTextColor.RED)));
                yield true;
            }
            case "enable", "disable" -> {
                if (args.length < 2) {
                    yield true;
                }
                kitService.get(args[1]).ifPresent(k -> {
                    kitService.save(k.toBuilder().enabled(sub.equals("enable")).build());
                    player.sendMessage(Component.text("Updated.", NamedTextColor.GREEN));
                });
                yield true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    yield true;
                }
                boolean ok = kitService.delete(args[1]);
                player.sendMessage(Component.text(ok ? "Deleted." : "Not found.", ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                if (ok) {
                    soundService.play(player, "delete");
                }
                yield true;
            }
            case "timeout" -> {
                if (args.length < 3) {
                    yield true;
                }
                final int seconds;
                try {
                    seconds = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("数値を入力してください: /kit timeout <name> <sec>",
                            NamedTextColor.RED));
                    yield true;
                }
                kitService.get(args[1]).ifPresent(k -> {
                    kitService.save(k.toBuilder().timeoutSeconds(Math.max(0, seconds)).build());
                    player.sendMessage(Component.text("Timeout set.", NamedTextColor.GREEN));
                });
                yield true;
            }
            case "autoregen", "autofood", "blockplace", "blockbreak", "canbreak", "pearl", "totem", "swordshieldbreak" -> {
                if (args.length < 3) {
                    yield true;
                }
                // Unified order: /kit <flag> <kit> <on|off>  (matches /kit timeout/adventure)
                String kitName = args[1];
                boolean value = parseToggle(args[2]);
                kitService.get(kitName).ifPresent(k -> {
                    KitDefinition.Builder b = k.toBuilder();
                    switch (sub) {
                        case "autoregen" -> b.naturalHealthRegen(value);
                        case "autofood" -> b.autoFood(value);
                        case "blockplace" -> b.blockPlace(value);
                        case "blockbreak", "canbreak" -> b.blockBreak(value);
                        case "pearl" -> b.pearl(value);
                        case "totem" -> b.totem(value);
                        case "swordshieldbreak" -> b.swordShieldBreak(value);
                        default -> {
                        }
                    }
                    kitService.save(b.build());
                    player.sendMessage(Component.text("Updated " + sub, NamedTextColor.GREEN));
                });
                yield true;
            }
            default -> {
                player.sendMessage(Component.text("Unknown kit subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleArena(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("/arena <draft|selection|p1|p2|type|save|enable|disable|list|info|delete>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "draft" -> {
                if (args.length < 2 || FORBIDDEN.contains(args[1].toLowerCase(Locale.ROOT))) {
                    player.sendMessage(Component.text("Invalid arena name.", NamedTextColor.RED));
                    yield true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                drafts.put(id, new ArenaTemplate(UUID.randomUUID(), id, ArenaType.DUEL,
                        player.getWorld().getName(), 0, 0, 0, 0, 0, 0,
                        LocationUtil.serialize(player.getLocation()),
                        LocationUtil.serialize(player.getLocation()), "", false));
                player.sendMessage(Component.text("Draft created: " + id, NamedTextColor.GREEN));
                yield true;
            }
            case "pos1" -> {
                // Feet-position selection: /arena pos1 marks the block you are standing on.
                AdminTools.setPos1(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(Component.text("Selection pos1 = ここ(足元)に設定しました。", NamedTextColor.GREEN));
                yield true;
            }
            case "pos2" -> {
                AdminTools.setPos2(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(Component.text("Selection pos2 = ここ(足元)に設定しました。次: /arena selection apply <draft>", NamedTextColor.GREEN));
                yield true;
            }
            case "selection", "selectionapply" -> {
                if (args.length < 2 && !sub.equals("selection")) {
                    yield true;
                }
                String arena = args.length >= 3 ? args[2] : (args.length >= 2 ? args[1] : null);
                if (sub.equals("selection") && args.length >= 2 && args[1].equalsIgnoreCase("apply")) {
                    arena = args.length >= 3 ? args[2] : null;
                }
                if (arena == null) {
                    yield true;
                }
                Location p1 = AdminTools.pos1(player);
                Location p2 = AdminTools.pos2(player);
                if (p1 == null || p2 == null) {
                    player.sendMessage(Component.text("Select pos1/pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                Cuboid cuboid = Cuboid.of(p1, p2);
                ArenaTemplate draft = drafts.getOrDefault(arena.toLowerCase(Locale.ROOT),
                        new ArenaTemplate(UUID.randomUUID(), arena.toLowerCase(Locale.ROOT), ArenaType.DUEL,
                                cuboid.worldName(), cuboid.minX(), cuboid.minY(), cuboid.minZ(),
                                cuboid.maxX(), cuboid.maxY(), cuboid.maxZ(),
                                LocationUtil.serialize(player.getLocation()),
                                LocationUtil.serialize(player.getLocation()), "", false));
                drafts.put(arena.toLowerCase(Locale.ROOT), new ArenaTemplate(
                        draft.id(), draft.name(), draft.type(), cuboid.worldName(),
                        cuboid.minX(), cuboid.minY(), cuboid.minZ(), cuboid.maxX(), cuboid.maxY(), cuboid.maxZ(),
                        draft.serializedSpawnA(), draft.serializedSpawnB(), draft.schematicPath(), draft.enabled()));
                player.sendMessage(Component.text("Selection applied.", NamedTextColor.GREEN));
                yield true;
            }
            case "p1", "p2" -> {
                if (args.length < 2) {
                    yield true;
                }
                ArenaTemplate draft = drafts.get(args[1].toLowerCase(Locale.ROOT));
                if (draft == null) {
                    player.sendMessage(Component.text("Draft missing. /arena draft <name>", NamedTextColor.RED));
                    yield true;
                }
                String serialized = LocationUtil.serialize(player.getLocation());
                drafts.put(draft.name(), new ArenaTemplate(
                        draft.id(), draft.name(), draft.type(), draft.world(),
                        draft.minX(), draft.minY(), draft.minZ(), draft.maxX(), draft.maxY(), draft.maxZ(),
                        sub.equals("p1") ? serialized : draft.serializedSpawnA(),
                        sub.equals("p2") ? serialized : draft.serializedSpawnB(),
                        draft.schematicPath(), draft.enabled()));
                player.sendMessage(Component.text(sub + " set.", NamedTextColor.GREEN));
                yield true;
            }
            case "save" -> {
                if (args.length < 2) {
                    yield true;
                }
                ArenaTemplate draft = drafts.get(args[1].toLowerCase(Locale.ROOT));
                if (draft == null) {
                    player.sendMessage(Component.text("No draft.", NamedTextColor.RED));
                    yield true;
                }
                String schematic = draft.name() + ".schem";
                Path out = new File(schematicRoot, schematic).toPath();
                if (faweBridge.isAvailable()) {
                    faweBridge.saveSchematic(player.getWorld(), draft.minX(), draft.minY(), draft.minZ(),
                            draft.maxX(), draft.maxY(), draft.maxZ(), out);
                }
                ArenaTemplate saved = new ArenaTemplate(
                        draft.id(), draft.name(), draft.type(), draft.world(),
                        draft.minX(), draft.minY(), draft.minZ(), draft.maxX(), draft.maxY(), draft.maxZ(),
                        draft.serializedSpawnA(), draft.serializedSpawnB(), schematic, false);
                arenaStore.upsert(saved);
                arenaService.setTemplates(arenaStore.templates());
                player.sendMessage(Component.text("Arena saved (disabled until /arena enable).", NamedTextColor.GREEN));
                yield true;
            }
            case "enable", "disable" -> {
                if (args.length < 2) {
                    yield true;
                }
                arenaStore.setEnabled(args[1], sub.equals("enable"));
                arenaService.setTemplates(arenaStore.templates());
                player.sendMessage(Component.text("Updated.", NamedTextColor.GREEN));
                yield true;
            }
            case "list" -> {
                arenaStore.templates().forEach(t -> player.sendMessage(Component.text(
                        t.name() + " enabled=" + t.enabled(), NamedTextColor.AQUA)));
                yield true;
            }
            case "info" -> {
                if (args.length < 2) {
                    yield true;
                }
                arenaStore.templates().stream().filter(t -> t.name().equalsIgnoreCase(args[1])).findFirst()
                        .ifPresentOrElse(t -> player.sendMessage(Component.text(t.toString(), NamedTextColor.GRAY)),
                                () -> player.sendMessage(Component.text("Not found.", NamedTextColor.RED)));
                yield true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    yield true;
                }
                arenaStore.delete(args[1]);
                arenaService.setTemplates(arenaStore.templates());
                player.sendMessage(Component.text("Deleted.", NamedTextColor.YELLOW));
                soundService.play(player, "delete");
                yield true;
            }
            default -> {
                player.sendMessage(Component.text("Unknown arena subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private static boolean parseToggle(String raw) {
        if (raw.equalsIgnoreCase("toggle")) {
            return true;
        }
        return Boolean.parseBoolean(raw);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("kit")) {
            return completeKit(args);
        }
        if (name.equals("arena")) {
            return completeArena(args);
        }
        if (name.equals("toggle")) {
            return completeToggle(args);
        }
        return List.of();
    }

    private List<String> completeKit(String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "gui", "help", "create", "overwrite", "list", "info", "enable", "disable",
                    "delete", "timeout", "order", "rename", "arena", "adventure", "autoregen", "autofood",
                    "blockplace", "blockbreak", "canbreak", "pearl", "totem", "swordshieldbreak"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        // Subcommands that take a kit name next.
        if (args.length == 2 && List.of("info", "enable", "disable", "delete", "timeout", "order", "rename", "arena",
                "adventure", "autoregen", "autofood", "blockplace", "blockbreak", "canbreak", "pearl", "totem",
                "swordshieldbreak").contains(sub)) {
            return filter(kitService.all().stream().map(KitDefinition::name).toList(), args[1]);
        }
        if (args.length == 3 && sub.equals("arena")) {
            List<String> options = new ArrayList<>(
                    arenaStore.templates().stream().map(ArenaTemplate::name).toList());
            options.add("any");
            return filter(options, args[2]);
        }
        // Third arg: on/off for toggles, numeric hint for timeout, up/down for order.
        if (args.length == 3) {
            if (sub.equals("timeout")) {
                return filter(List.of("30", "60", "120", "300"), args[2]);
            }
            if (sub.equals("order")) {
                return filter(List.of("up", "down"), args[2]);
            }
            if (List.of("adventure", "autoregen", "autofood", "blockplace", "blockbreak", "canbreak",
                    "pearl", "totem", "swordshieldbreak").contains(sub)) {
                return filter(List.of("on", "off"), args[2]);
            }
        }
        return List.of();
    }

    private List<String> completeArena(String[] args) {
        if (args.length == 1) {
            return filter(List.of(
                    "draft", "pos1", "pos2", "selection", "p1", "p2", "save", "enable", "disable",
                    "list", "info", "delete"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> arenaNames = arenaStore.templates().stream().map(ArenaTemplate::name).toList();
        List<String> draftNames = new ArrayList<>(drafts.keySet());
        if (args.length == 2) {
            return switch (sub) {
                // Draft-stage subcommands operate on DRAFTS only.
                case "p1", "p2", "save" -> filter(draftNames, args[1]);
                // Template management operates on SAVED templates only.
                case "enable", "disable", "info", "delete" -> filter(arenaNames, args[1]);
                case "selection" -> filter(List.of("apply"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("selection") && args[1].equalsIgnoreCase("apply")) {
            // /arena selection apply <draft> — drafts only.
            return filter(draftNames, args[2]);
        }
        return List.of();
    }

    private List<String> completeToggle(String[] args) {
        if (args.length == 1) {
            return filter(List.of("queue", "map"), args[0]);
        }
        if (args.length == 2) {
            return filter(List.of("enable", "disable"), args[1]);
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("queue")) {
                return filter(kitService.all().stream().map(KitDefinition::name).toList(), args[2]);
            }
            if (args[0].equalsIgnoreCase("map")) {
                return filter(arenaStore.templates().stream().map(ArenaTemplate::name).toList(), args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(options);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
