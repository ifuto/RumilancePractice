package com.rumilance.practice.command;

import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.model.PracticeRoom;
import com.rumilance.practice.practice.PracticeDraft;
import com.rumilance.practice.practice.PracticeService;
import com.rumilance.practice.practice.PracticeType;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.SafeTeleport;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Admin {@code /practice} — draft flow similar to arenas, but p1 only and case-sensitive IDs.
 */
public final class PracticeCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> FORBIDDEN = Set.of(
            "draft", "pos1", "pos2", "p1", "p2", "save", "enable", "disable", "delete", "list",
            "info", "tp", "anker", "mace", "type", "selection", "apply", "create", "selectionapply"
    );

    private final PracticeService practiceService;

    public PracticeCommand(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text(
                    "/practice <draft|pos1|selection|p1|save|enable|disable|delete|list|info|tp>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "draft" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text(
                            "Usage: /practice draft <Name> <ANKER|MACE>", NamedTextColor.YELLOW));
                    yield true;
                }
                String id = args[1];
                if (FORBIDDEN.contains(id.toLowerCase(Locale.ROOT))) {
                    player.sendMessage(Component.text("Invalid / reserved practice name.", NamedTextColor.RED));
                    yield true;
                }
                PracticeType type;
                try {
                    type = PracticeType.parse(args[2]);
                } catch (Exception e) {
                    player.sendMessage(Component.text("Type must be ANKER or MACE.", NamedTextColor.RED));
                    yield true;
                }
                practiceService.createDraft(id, type);
                player.sendMessage(Component.text("Draft created: " + id + " (" + type + ")",
                        NamedTextColor.GREEN));
                player.sendMessage(Component.text(
                        "Next: /practice pos1 -> pos2 -> selection apply " + id + " -> p1 " + id + " -> save",
                        NamedTextColor.GRAY));
                yield true;
            }
            case "pos1" -> {
                AdminTools.setPos1(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(Component.text("Selection pos1 = feet.", NamedTextColor.GREEN));
                yield true;
            }
            case "pos2" -> {
                AdminTools.setPos2(player, player.getLocation().getBlock().getLocation());
                player.sendMessage(Component.text(
                        "Selection pos2 = feet. Next: /practice selection apply <Name>",
                        NamedTextColor.GREEN));
                yield true;
            }
            case "selection" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("apply")) {
                    player.sendMessage(Component.text(
                            "Usage: /practice selection apply <Name>", NamedTextColor.YELLOW));
                    yield true;
                }
                String id = args[2];
                Location p1 = AdminTools.pos1(player);
                Location p2 = AdminTools.pos2(player);
                if (p1 == null || p2 == null) {
                    player.sendMessage(Component.text("Select pos1/pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = practiceService.applySelection(id, Cuboid.of(p1, p2));
                player.sendMessage(Component.text(ok ? "Selection applied." : "Draft/room not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "p1" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /practice p1 <Name>", NamedTextColor.YELLOW));
                    yield true;
                }
                boolean ok = practiceService.setP1(args[1], player.getLocation());
                player.sendMessage(Component.text(ok ? "p1 spawn set." : "Draft/room not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "save" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /practice save <Name>", NamedTextColor.YELLOW));
                    yield true;
                }
                Optional<String> err = practiceService.saveDraft(args[1]);
                if (err.isPresent()) {
                    player.sendMessage(Component.text(err.get(), NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text(
                            "Practice saved (disabled until /practice enable).", NamedTextColor.GREEN));
                }
                yield true;
            }
            case "enable", "disable" -> {
                if (args.length < 2) {
                    yield true;
                }
                boolean ok = practiceService.setEnabled(args[1], sub.equals("enable"));
                player.sendMessage(Component.text(ok ? "Updated." : "Not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    yield true;
                }
                boolean ok = practiceService.delete(args[1]);
                player.sendMessage(Component.text(ok ? "Deleted." : "Not found.",
                        ok ? NamedTextColor.YELLOW : NamedTextColor.RED));
                yield true;
            }
            case "list" -> {
                List<String> drafts = practiceService.draftIds();
                if (!drafts.isEmpty()) {
                    player.sendMessage(Component.text("Drafts: " + String.join(", ", drafts),
                            NamedTextColor.GRAY));
                }
                for (PracticeRoom room : practiceService.all()) {
                    player.sendMessage(Component.text(
                            room.id() + " [" + room.type() + "] enabled=" + room.enabled()
                                    + " (" + room.displayName() + ")",
                            NamedTextColor.AQUA));
                }
                if (practiceService.all().isEmpty() && drafts.isEmpty()) {
                    player.sendMessage(Component.text("No practice rooms.", NamedTextColor.GRAY));
                }
                yield true;
            }
            case "info" -> {
                if (args.length < 2) {
                    yield true;
                }
                practiceService.get(args[1]).ifPresentOrElse(
                        room -> player.sendMessage(Component.text(
                                room.id() + " type=" + room.type() + " world=" + room.world()
                                        + " enabled=" + room.enabled()
                                        + " region=" + room.region()
                                        + " spawn=" + room.serializedSpawn(),
                                NamedTextColor.GRAY)),
                        () -> {
                            Optional<PracticeDraft> draft = practiceService.draft(args[1]);
                            if (draft.isPresent()) {
                                PracticeDraft d = draft.get();
                                player.sendMessage(Component.text(
                                        "Draft " + d.id() + " type=" + d.type()
                                                + " region=" + d.region()
                                                + " spawn=" + d.serializedSpawn(),
                                        NamedTextColor.GRAY));
                            } else {
                                player.sendMessage(Component.text("Not found.", NamedTextColor.RED));
                            }
                        });
                yield true;
            }
            case "tp" -> {
                if (args.length < 2) {
                    yield true;
                }
                practiceService.get(args[1]).ifPresentOrElse(room -> {
                    Location spawn = LocationUtil.deserialize(room.serializedSpawn());
                    if (spawn.getWorld() == null) {
                        spawn.setWorld(player.getServer().getWorld(room.world()));
                    }
                    if (spawn.getWorld() == null) {
                        player.sendMessage(Component.text("World not loaded.", NamedTextColor.RED));
                        return;
                    }
                    SafeTeleport.teleport(player, spawn);
                    player.sendMessage(Component.text("Teleported to " + room.displayName(),
                            NamedTextColor.GREEN));
                }, () -> player.sendMessage(Component.text("Not found.", NamedTextColor.RED)));
                yield true;
            }
            default -> {
                player.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String current = TabCompletions.current(args);
        if (args.length == 1) {
            return TabCompletions.filter(current,
                    "draft", "pos1", "pos2", "selection", "p1", "save", "enable", "disable",
                    "delete", "list", "info", "tp");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        practiceService.all().forEach(r -> names.add(r.id()));
        names.addAll(practiceService.draftIds());
        if (args.length == 2) {
            return switch (sub) {
                case "draft" -> List.of();
                case "selection" -> TabCompletions.filter(current, "apply");
                case "p1", "save", "enable", "disable", "delete", "info", "tp" ->
                        TabCompletions.filter(current, names);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("draft")) {
                return TabCompletions.filter(current, "ANKER", "MACE");
            }
            if (sub.equals("selection") && args[1].equalsIgnoreCase("apply")) {
                return TabCompletions.filter(current, names);
            }
        }
        return List.of();
    }
}
