package com.rumilance.practice.command;

import com.rumilance.practice.RumilancePractice;
import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class PracticeAdminCommand implements CommandExecutor, TabCompleter {

    private final RumilancePractice plugin;
    private final ConfigService configService;
    private final SoundService soundService;
    private final MatchService matchService;
    private final LobbyService lobbyService;
    private final RuntimeFlags runtimeFlags;
    private final KitService kitService;
    private final ArenaTemplateStore arenaStore;
    private final ArenaService arenaService;
    private final FfaService ffaService;

    public PracticeAdminCommand(
            RumilancePractice plugin,
            ConfigService configService,
            SoundService soundService,
            MatchService matchService,
            LobbyService lobbyService,
            RuntimeFlags runtimeFlags,
            KitService kitService,
            ArenaTemplateStore arenaStore,
            ArenaService arenaService,
            FfaService ffaService
    ) {
        this.plugin = plugin;
        this.configService = configService;
        this.soundService = soundService;
        this.matchService = matchService;
        this.lobbyService = lobbyService;
        this.runtimeFlags = runtimeFlags;
        this.kitService = kitService;
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
        this.ffaService = ffaService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (name.equals("setlobbyitem")) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            lobbyService.saveLobbyInventory(player);
            player.sendMessage(Component.text("Lobby inventory saved.", NamedTextColor.GREEN));
            return true;
        }

        if (name.equals("slobby")) {
            return handleSlobby(sender, args);
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("/practiceadmin <tool|reload|status|matches|cleanup|maintenance>",
                    NamedTextColor.YELLOW));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "tool" -> {
                if (sender instanceof Player player) {
                    AdminTools.give(player);
                    player.sendMessage(Component.text("Admin tools given.", NamedTextColor.GREEN));
                }
                yield true;
            }
            case "reload" -> {
                configService.reload();
                soundService.reload();
                lobbyService.reload();
                kitService.reload();
                arenaStore.reload();
                arenaService.setTemplates(arenaStore.templates());
                ffaService.reload();
                sender.sendMessage(Component.text("Reloaded safe configs (kits/arenas/ffa/lobby/sounds).",
                        NamedTextColor.GREEN));
                yield true;
            }
            case "status" -> {
                sender.sendMessage(Component.text("Active matches: " + matchService.registry().activeCount()
                        + " | maintenance=" + runtimeFlags.maintenance(), NamedTextColor.AQUA));
                yield true;
            }
            case "matches" -> {
                matchService.registry().all().forEach(m ->
                        sender.sendMessage(Component.text(m.id() + " " + m.mode() + " " + m.state()
                                + " kit=" + m.kitName(), NamedTextColor.GRAY)));
                yield true;
            }
            case "cleanup" -> {
                matchService.shutdown();
                sender.sendMessage(Component.text("Forced match cleanup.", NamedTextColor.YELLOW));
                yield true;
            }
            case "maintenance" -> {
                boolean on = args.length > 1 && args[1].equalsIgnoreCase("on");
                configService.config().set("plugin.maintenance", on);
                configService.save(ConfigService.CONFIG);
                runtimeFlags.setMaintenance(on);
                sender.sendMessage(Component.text("Maintenance " + (on ? "ON" : "OFF"), NamedTextColor.GOLD));
                yield true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleSlobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("/slobby <pos1|pos2|spawn|info|validate>", NamedTextColor.YELLOW));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1" -> {
                AdminTools.setPos1(player, player.getLocation());
                player.sendMessage(Component.text("Lobby pos1 set.", NamedTextColor.GREEN));
                yield true;
            }
            case "pos2" -> {
                AdminTools.setPos2(player, player.getLocation());
                var p1 = AdminTools.pos1(player);
                var p2 = AdminTools.pos2(player);
                if (p1 != null && p2 != null) {
                    lobbyService.setRegion(Cuboid.of(p1, p2));
                }
                player.sendMessage(Component.text("Lobby pos2 set.", NamedTextColor.GREEN));
                yield true;
            }
            case "spawn" -> {
                lobbyService.setSpawn(player.getLocation());
                if (!LocationUtil.isInsideWorldBorder(player.getLocation(), player)) {
                    player.sendMessage(Component.text(
                            "Warning: spawn is outside WorldBorder and will be clamped on teleport.",
                            NamedTextColor.YELLOW));
                }
                player.sendMessage(Component.text("Lobby spawn set.", NamedTextColor.GREEN));
                yield true;
            }
            case "info" -> {
                player.sendMessage(Component.text("Spawn: " + lobbyService.spawn(), NamedTextColor.AQUA));
                player.sendMessage(Component.text("Region: " + lobbyService.region(), NamedTextColor.AQUA));
                yield true;
            }
            case "validate" -> {
                String error = lobbyService.validate();
                player.sendMessage(Component.text(error == null ? "Lobby OK" : error,
                        error == null ? NamedTextColor.GREEN : NamedTextColor.RED));
                yield true;
            }
            default -> {
                player.sendMessage(Component.text("Unknown slobby subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String current = TabCompletions.current(args);
        if (command.getName().equalsIgnoreCase("slobby") && args.length == 1) {
            return TabCompletions.filter(current, "pos1", "pos2", "spawn", "info", "validate");
        }
        if (args.length == 1) {
            return TabCompletions.filter(current, "tool", "reload", "status", "matches", "cleanup", "maintenance");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return TabCompletions.filter(current, "on", "off");
        }
        return List.of();
    }
}
