package com.rumilance.practice.command;

import com.rumilance.practice.admin.AdminTools;
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
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("adffa")) {
            if (!sender.hasPermission("rumilance.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2 || !args[0].equalsIgnoreCase("reset")) {
                sender.sendMessage(Component.text("Usage: /adffa reset <arena>", NamedTextColor.YELLOW));
                return true;
            }
            ffaService.reset(args[1]);
            sender.sendMessage(Component.text("FFA reset started: " + args[1], NamedTextColor.GREEN));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                ffaListGui.open(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission("rumilance.admin");
        if (!admin && List.of("create", "selection", "spawn", "kit", "enable", "disable", "delete", "reset")
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
            case "leave" -> {
                if (sender instanceof Player player) {
                    ffaService.leave(player);
                }
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
        if (command.getName().equalsIgnoreCase("adffa")) {
            if (args.length == 1) {
                return List.of("reset");
            }
            if (args.length == 2) {
                return ffaService.list().stream().map(FfaService.FfaArena::id).toList();
            }
            return List.of();
        }
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("leave"));
            if (sender.hasPermission("rumilance.admin")) {
                base.addAll(List.of("create", "selection", "spawn", "kit", "enable", "disable", "delete", "reset"));
            }
            return base;
        }
        if (args.length == 2 && List.of("enable", "disable", "delete", "reset", "spawn", "kit")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return ffaService.list().stream().map(FfaService.FfaArena::id).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("kit")) {
            return kitService.enabled().stream().map(k -> k.name()).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("selection")) {
            return ffaService.list().stream().map(FfaService.FfaArena::id).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("selection")) {
            return List.of("apply");
        }
        return List.of();
    }
}
