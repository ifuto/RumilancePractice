package com.rumilance.practice.command;

import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.ffa.FfaResetTimes;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.menus.FfaListGui;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.util.Cuboid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/**
 * Player {@code /ffa} list GUI and admin FFA arena management subcommands.
 */
public final class FfaCommand implements CommandExecutor, TabCompleter {

    private final FfaListGui ffaListGui;
    private final FfaService ffaService;
    private final KitService kitService;

    public FfaCommand(FfaListGui ffaListGui, FfaService ffaService, KitService kitService) {
        this.ffaListGui = ffaListGui;
        this.ffaService = ffaService;
        this.kitService = kitService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                ffaListGui.open(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("rumilance.admin");
        if (!admin && List.of("create", "selection", "spawn", "kit", "enable", "disable", "delete", "reset", "rename", "resettime", "icon")
                .contains(sub)) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        return switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa create <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                var p1 = AdminTools.pos1(player);
                var p2 = AdminTools.pos2(player);
                if (p1 == null || p2 == null) {
                    player.sendMessage(Component.text("Set pos1/pos2 with admin wand first.", NamedTextColor.RED));
                    yield true;
                }
                Cuboid region = Cuboid.of(p1, p2);
                ffaService.create(args[1], region, player.getLocation(), "nodebuff");
                player.sendMessage(Component.text("FFA arena created (disabled): " + args[1], NamedTextColor.GREEN));
                yield true;
            }
            case "rename" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /ffa rename <old> <new>", NamedTextColor.YELLOW));
                    yield true;
                }
                FfaService.RenameResult r = ffaService.rename(args[1], args[2]);
                sender.sendMessage(Component.text(switch (r) {
                    case OK -> "Renamed: " + args[1] + " -> " + args[2];
                    case NOT_FOUND -> "Arena not found: " + args[1];
                    case TARGET_EXISTS -> "Target name already exists: " + args[2];
                }, r == FfaService.RenameResult.OK ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "selection" -> {
                if (!(sender instanceof Player player) || args.length < 3 || !args[1].equalsIgnoreCase("apply")) {
                    sender.sendMessage(Component.text("Usage: /ffa selection apply <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                var p1 = AdminTools.pos1(player);
                var p2 = AdminTools.pos2(player);
                if (p1 == null || p2 == null) {
                    player.sendMessage(Component.text("Set pos1/pos2 first.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = ffaService.updateRegion(args[2], Cuboid.of(p1, p2));
                player.sendMessage(Component.text(ok ? "Region applied." : "Arena not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "spawn" -> {
                if (!(sender instanceof Player player) || args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa spawn <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                boolean ok = ffaService.updateSpawn(args[1], player.getLocation());
                player.sendMessage(Component.text(ok ? "Spawn set." : "Arena not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "kit" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /ffa kit <arena> <kit>", NamedTextColor.YELLOW));
                    yield true;
                }
                if (kitService.get(args[2]).isEmpty()) {
                    sender.sendMessage(Component.text("Unknown kit.", NamedTextColor.RED));
                    yield true;
                }
                boolean ok = ffaService.updateKit(args[1], args[2]);
                sender.sendMessage(Component.text(ok ? "Kit set." : "Arena not found.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            case "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa enable <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                ffaService.setEnabled(args[1], true);
                sender.sendMessage(Component.text("Enabled " + args[1], NamedTextColor.GREEN));
                yield true;
            }
            case "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa disable <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                ffaService.setEnabled(args[1], false);
                sender.sendMessage(Component.text("Disabled " + args[1], NamedTextColor.YELLOW));
                yield true;
            }
            case "delete" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa delete <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                ffaService.delete(args[1]);
                sender.sendMessage(Component.text("Deleted " + args[1], NamedTextColor.YELLOW));
                yield true;
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa reset <arena>", NamedTextColor.YELLOW));
                    yield true;
                }
                ffaService.reset(args[1]);
                sender.sendMessage(Component.text("Reset started: " + args[1], NamedTextColor.GREEN));
                yield true;
            }
            case "resettime" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /ffa resettime <arena> [30s|5min|2hour|off]", NamedTextColor.YELLOW));
                    yield true;
                }
                String arenaId = args[1];
                if (ffaService.get(arenaId).isEmpty()) {
                    sender.sendMessage(Component.text("Arena not found: " + arenaId, NamedTextColor.RED));
                    yield true;
                }
                if (args.length == 2) {
                    int seconds = ffaService.resetIntervalSeconds(arenaId);
                    if (seconds <= 0) {
                        sender.sendMessage(Component.text(
                                arenaId + " FFA periodic reset is off.", NamedTextColor.YELLOW));
                    } else {
                        sender.sendMessage(Component.text(
                                arenaId + " FFA resets every " + FfaResetTimes.format(seconds) + ".",
                                NamedTextColor.AQUA));
                    }
                    yield true;
                }
                var parsed = FfaResetTimes.parse(args[2]);
                if (parsed.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "Usage: /ffa resettime <arena> <30s|5min|2hour|off>", NamedTextColor.YELLOW));
                    yield true;
                }
                int seconds = parsed.getAsInt();
                if (!ffaService.setResetIntervalSeconds(arenaId, seconds)) {
                    sender.sendMessage(Component.text("Arena not found: " + arenaId, NamedTextColor.RED));
                    yield true;
                }
                if (seconds <= 0) {
                    sender.sendMessage(Component.text(
                            arenaId + " FFA periodic reset disabled.", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text(
                            arenaId + " FFA will reset every " + FfaResetTimes.format(seconds) + ".",
                            NamedTextColor.GREEN));
                }
                yield true;
            }
            case "leave" -> {
                if (sender instanceof Player player) {
                    ffaService.leave(player);
                }
                yield true;
            }
            case "icon" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /ffa icon <arena> [material]", NamedTextColor.YELLOW));
                    yield true;
                }
                if (ffaService.get(args[1]).isEmpty()) {
                    sender.sendMessage(Component.text("Arena not found: " + args[1], NamedTextColor.RED));
                    yield true;
                }
                String material = args.length >= 3 ? args[2] : "IRON_SWORD";
                if (org.bukkit.Material.matchMaterial(material) == null) {
                    sender.sendMessage(Component.text("Unknown material: " + material, NamedTextColor.RED));
                    yield true;
                }
                boolean ok = ffaService.updateIcon(args[1], material);
                sender.sendMessage(Component.text(
                        ok ? "Icon set to " + material.toUpperCase(Locale.ROOT) + " for " + args[1]
                                : "Failed to update icon.",
                        ok ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            default -> {
                if (sender instanceof Player player) {
                    ffaListGui.open(player);
                }
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String current = TabCompletions.current(args);
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("leave"));
            if (sender.hasPermission("rumilance.admin")) {
                base.addAll(List.of("create", "selection", "spawn", "kit", "enable", "disable",
                        "delete", "reset", "rename", "resettime", "icon"));
            }
            return TabCompletions.filter(current, base);
        }
        // Admin-only subcommands: never suggest anything to non-admins past arg 1.
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        if (args.length == 2 && List.of("enable", "disable", "delete", "reset", "spawn", "kit", "rename", "resettime", "icon")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return TabCompletions.filter(current,
                    ffaService.list().stream().map(FfaService.FfaArena::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("kit")) {
            return TabCompletions.filter(current,
                    kitService.enabled().stream().map(k -> k.name()).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("selection")) {
            return TabCompletions.filter(current,
                    ffaService.list().stream().map(FfaService.FfaArena::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("selection")) {
            return TabCompletions.filter(current, "apply");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resettime")) {
            return TabCompletions.filter(current, "off", "30s", "5min", "10min", "30min", "1hour", "2hour");
        }
        return List.of();
    }
}
