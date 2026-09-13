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
    private com.rumilance.practice.practice.PracticeService practiceService;
    private volatile com.rumilance.practice.signqueue.SignQueueService signQueueService;
    private java.util.function.Consumer<Player> openAdminMenu;
    private com.rumilance.practice.scoreboard.ScoreboardService scoreboardService;

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

    public void setPracticeService(com.rumilance.practice.practice.PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public void setSignQueueService(com.rumilance.practice.signqueue.SignQueueService signQueueService) {
        this.signQueueService = signQueueService;
    }

    public void setOpenAdminMenu(java.util.function.Consumer<Player> openAdminMenu) {
        this.openAdminMenu = openAdminMenu;
    }

    public void setScoreboardService(com.rumilance.practice.scoreboard.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
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
            sender.sendMessage(Component.text("/practiceadmin <menu|tool|sign|reload|status|matches|cleanup|maintenance|ffacommand>",
                    NamedTextColor.YELLOW));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (sender instanceof Player player) {
                    if (openAdminMenu != null) {
                        soundService.play(player, "gui-open");
                        openAdminMenu.accept(player);
                    } else {
                        player.sendMessage(Component.text("Admin menu not wired.", NamedTextColor.RED));
                    }
                } else {
                    sender.sendMessage(Component.text("The admin menu is in-game only.", NamedTextColor.RED));
                }
                yield true;
            }
            case "sign" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Queue signs are in-game only.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /practiceadmin sign <kit>", NamedTextColor.YELLOW));
                    yield true;
                }
                com.rumilance.practice.signqueue.SignQueueService signs = signQueueService;
                if (signs == null) {
                    sender.sendMessage(Component.text("Sign queue service not wired.", NamedTextColor.RED));
                    yield true;
                }
                String kitId = args[1].toLowerCase(Locale.ROOT);
                if (kitService.get(kitId).isEmpty()) {
                    sender.sendMessage(Component.text("Unknown kit: " + args[1], NamedTextColor.RED));
                    yield true;
                }
                player.getInventory().addItem(signs.createSignItem(player, kitId));
                soundService.play(player, "gui-click");
                player.sendMessage(Component.text(
                        "Queue sign for kit '" + kitId + "' given. Place it to create the queue sign.",
                        NamedTextColor.GREEN));
                yield true;
            }
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
                if (practiceService != null) {
                    practiceService.reload();
                }
                if (scoreboardService != null) {
                    scoreboardService.reload(new com.rumilance.practice.scoreboard.ScoreboardConfig(
                            configService.scoreboard()));
                }
                sender.sendMessage(Component.text(
                        "Reloaded safe configs (kits/arenas/practices/ffa/lobby/sounds/scoreboard).",
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
            case "ffacommand" -> {
                yield handleFfaCommand(sender, args);
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    /**
     * /practiceadmin ffacommand — the FFA out-of-combat command whitelist. Off by default:
     * while OFF, FFA command behaviour is untouched; while ON, FFA occupants can only run
     * the whitelisted commands and only while NOT combat-tagged.
     */
    private boolean handleFfaCommand(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "on", "off" -> {
                boolean on = sub.equals("on");
                ffaService.setCommandGate(on);
                sender.sendMessage(Component.text("FFA command whitelist " + (on ? "ENABLED" : "DISABLED")
                        + (on ? " — whitelisted commands work out of combat only." : "."),
                        on ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /practiceadmin ffacommand add <command>",
                            NamedTextColor.YELLOW));
                    break;
                }
                String label = ffaService.whitelistCommand(args[2])
                        ? args[2] : null;
                sender.sendMessage(Component.text(label != null
                                ? "Whitelisted: /" + label
                                : "Already whitelisted: /" + args[2],
                        label != null ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /practiceadmin ffacommand remove <command>",
                            NamedTextColor.YELLOW));
                    break;
                }
                sender.sendMessage(Component.text(ffaService.unwhitelistCommand(args[2])
                                ? "Removed from whitelist: /" + args[2]
                                : "Not on the whitelist: /" + args[2],
                        NamedTextColor.YELLOW));
            }
            case "list" -> {
                sender.sendMessage(Component.text(
                        "FFA command whitelist: " + (ffaService.commandGateEnabled() ? "ON" : "OFF"),
                        NamedTextColor.AQUA));
                java.util.Set<String> commands = ffaService.whitelistedCommands();
                if (commands.isEmpty()) {
                    sender.sendMessage(Component.text("  (empty — every command is blocked in FFA"
                            + " while the gate is on)", NamedTextColor.GRAY));
                } else {
                    sender.sendMessage(Component.text("  /" + String.join(", /",
                            new java.util.TreeSet<>(commands)), NamedTextColor.GRAY));
                }
            }
            case "clear" -> {
                ffaService.clearWhitelistedCommands();
                sender.sendMessage(Component.text("FFA command whitelist cleared.", NamedTextColor.YELLOW));
            }
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /practiceadmin ffacommand <on|off|add|remove|list|clear> [command]",
                        NamedTextColor.YELLOW));
            }
        }
        return true;
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
            return TabCompletions.filter(current, "menu", "sign", "tool", "reload", "status", "matches", "cleanup", "maintenance", "ffacommand");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return TabCompletions.filter(current, "on", "off");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ffacommand")) {
            return TabCompletions.filter(current, "on", "off", "add", "remove", "list", "clear");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ffacommand")
                && args[1].equalsIgnoreCase("remove")) {
            return TabCompletions.filter(current,
                    ffaService.whitelistedCommands().toArray(String[]::new));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ffacommand")
                && args[1].equalsIgnoreCase("add")) {
            return TabCompletions.filter(current, "spawn", "msg", "baltop", "pay");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sign")) {
            return TabCompletions.filter(current,
                    kitService.enabled().stream().map(k -> k.name()).toList());
        }
        return List.of();
    }
}
