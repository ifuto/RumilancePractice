package com.rumilance.practice.command;

import com.rumilance.practice.RumilancePractice;
import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.ffa.FfaResetTimes;
import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.queue.QueueService;
import com.rumilance.practice.resourcepack.ResourcePackService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.stats.StatsResetService;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.Cuboid;
import com.rumilance.practice.util.LocationUtil;
import com.rumilance.practice.util.TickHealth;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
    private volatile QueueService queueService;
    private volatile StatsResetService statsResetService;
    private volatile PlayerRepository playerRepository;
    private volatile AsyncExecutor asyncExecutor;
    private volatile BanService banService;
    private volatile ResourcePackService resourcePackService;

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

    public void setQueueService(QueueService queueService) {
        this.queueService = queueService;
    }

    public void setStatsResetService(StatsResetService statsResetService) {
        this.statsResetService = statsResetService;
    }

    public void setPlayerRepository(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public void setAsyncExecutor(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    public void setBanService(BanService banService) {
        this.banService = banService;
    }

    public void setResourcePackService(ResourcePackService resourcePackService) {
        this.resourcePackService = resourcePackService;
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
            sender.sendMessage(Component.text(
                    "/practiceadmin <menu|tool|sign|reload|status|matches|cleanup|maintenance|ffacommand|ffa|statsreset|broadcast|time|kick|forceend|forcematch|toggle|packpolicy>",
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
                if (args.length > 1 && args[1].equalsIgnoreCase("tps")) {
                    double mspt = TickHealth.emaMspt();
                    double tps = mspt <= 0.0d ? 20.0d : Math.min(20.0d, 1000.0d / mspt);
                    boolean lagging = TickHealth.lagging();
                    sender.sendMessage(Component.text(String.format(
                                    "MSPT %.1fms (EMA) | TPS %.1f | %s",
                                    mspt, tps, lagging ? "LAGGING" : "OK"),
                            lagging ? NamedTextColor.RED : NamedTextColor.GREEN));
                    yield true;
                }
                sender.sendMessage(Component.text("Active matches: " + matchService.registry().activeCount()
                        + " | maintenance=" + runtimeFlags.maintenance()
                        + " | online=" + com.rumilance.practice.util.RealPlayers.count()
                        + " | FFA players=" + ffaService.occupantIds().size()
                        + " | queued=" + (queueService == null ? 0 : queueService.totalWaiting()),
                        NamedTextColor.AQUA));
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
            case "ffa" -> {
                yield handleFfa(sender, args);
            }
            case "statsreset" -> {
                yield handleStatsReset(sender, args);
            }
            case "broadcast" -> {
                yield handleBroadcast(sender, args);
            }
            case "time" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("In-game only.", NamedTextColor.RED));
                    yield true;
                }
                if (args.length >= 2) {
                    String token = args[1].toLowerCase(Locale.ROOT);
                    Long t = switch (token) {
                        case "day" -> 1000L;
                        case "noon" -> 6000L;
                        case "night" -> 13000L;
                        case "midnight" -> 18000L;
                        default -> null;
                    };
                    if (t != null) {
                        player.getWorld().setTime(t);
                        sender.sendMessage(Component.text("World time set to " + token + ".",
                                NamedTextColor.GREEN));
                        yield true;
                    }
                    if (token.equals("sun") || token.equals("clear")) {
                        player.getWorld().setStorm(false);
                        player.getWorld().setThundering(false);
                        sender.sendMessage(Component.text("Weather cleared.", NamedTextColor.GREEN));
                        yield true;
                    }
                }
                sender.sendMessage(Component.text(
                        "Usage: /practiceadmin time <day|noon|night|midnight|sun>  |  time now "
                                + player.getWorld().getTime() + " tick, "
                                + (player.getWorld().hasStorm() ? "storm" : "clear"),
                        NamedTextColor.YELLOW));
                yield true;
            }
            case "kick" -> {
                yield handleKick(sender, args);
            }
            case "forceend" -> {
                yield handleForceEnd(sender, args);
            }
            case "forcematch" -> {
                yield handleForceMatch(sender, args);
            }
            case "toggle" -> {
                yield handleToggle(sender, args);
            }
            case "packpolicy" -> {
                boolean required = true;
                if (args.length > 1 && (args[1].equalsIgnoreCase("recommended")
                        || args[1].equalsIgnoreCase("optional") || args[1].equalsIgnoreCase("off"))) {
                    required = false;
                }
                if (resourcePackService == null) {
                    sender.sendMessage(Component.text("Pack policy service not wired.", NamedTextColor.RED));
                    yield true;
                }
                resourcePackService.setRequired(required);
                sender.sendMessage(Component.text("Resource pack policy: "
                        + (required ? "REQUIRED" : "RECOMMENDED"), required
                        ? NamedTextColor.RED : NamedTextColor.GREEN));
                yield true;
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

    /**
     * /practiceadmin ffa — per-arena FFA control that used to live only in {@code /ffa}:
     * {@code list}, {@code info <arena>}, {@code reset <arena>}, {@code resettime <arena> <token>},
     * {@code enable|disable <arena>}, {@code count}.
     */
    private boolean handleFfa(CommandSender sender, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "" -> {
                sender.sendMessage(Component.text(
                        "/practiceadmin ffa <list|info|reset|resettime|enable|disable|count> [arena]",
                        NamedTextColor.YELLOW));
            }
            case "list" -> {
                List<FfaService.FfaArena> arenas = ffaService.list();
                if (arenas.isEmpty()) {
                    sender.sendMessage(Component.text("No FFA arenas.", NamedTextColor.GRAY));
                    break;
                }
                for (FfaService.FfaArena arena : arenas) {
                    int remaining = ffaService.resetRemainingSeconds(arena.id());
                    String timer = remaining < 0 ? "off"
                            : FfaResetTimes.format(remaining);
                    sender.sendMessage(Component.text(arena.id(), arena.enabled()
                                    ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                            .append(Component.text("  kit=" + arena.kitId()
                                    + "  players=" + ffaService.occupantCount(arena.id())
                                    + "  reset=" + timer
                                    + (arena.enabled() ? "" : "  [disabled]"),
                                    NamedTextColor.GRAY)));
                }
            }
            case "count" -> {
                sender.sendMessage(Component.text("FFA players: " + ffaService.occupantIds().size()
                        + " across " + ffaService.list().size() + " arenas.", NamedTextColor.AQUA));
            }
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /practiceadmin ffa info <arena>",
                            NamedTextColor.YELLOW));
                    break;
                }
                handleFfaInfo(sender, args[2]);
            }
            case "reset" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /practiceadmin ffa reset <arena>",
                            NamedTextColor.YELLOW));
                    break;
                }
                if (ffaService.get(args[2]).isEmpty()) {
                    sender.sendMessage(Component.text("Unknown arena: " + args[2], NamedTextColor.RED));
                    break;
                }
                ffaService.reset(args[2], true);
                sender.sendMessage(Component.text("Forced reset of " + args[2] + " FFA.",
                        NamedTextColor.GREEN));
            }
            case "resettime" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text(
                            "Usage: /practiceadmin ffa resettime <arena> <30s|5min|2hour|off>",
                            NamedTextColor.YELLOW));
                    break;
                }
                java.util.OptionalInt parsed = FfaResetTimes.parse(args[3]);
                if (parsed.isEmpty()) {
                    sender.sendMessage(Component.text("Bad time token: " + args[3], NamedTextColor.RED));
                    break;
                }
                int seconds = parsed.getAsInt();
                if (!ffaService.setResetIntervalSeconds(args[2], seconds)) {
                    sender.sendMessage(Component.text("Unknown arena: " + args[2], NamedTextColor.RED));
                    break;
                }
                sender.sendMessage(Component.text(args[2] + " FFA resets every "
                        + FfaResetTimes.format(seconds) + ".", NamedTextColor.GREEN));
            }
            case "enable", "disable" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text(
                            "Usage: /practiceadmin ffa " + sub + " <arena>", NamedTextColor.YELLOW));
                    break;
                }
                if (ffaService.get(args[2]).isEmpty()) {
                    sender.sendMessage(Component.text("Unknown arena: " + args[2], NamedTextColor.RED));
                    break;
                }
                boolean on = sub.equals("enable");
                ffaService.setEnabled(args[2], on);
                sender.sendMessage(Component.text(args[2] + " FFA "
                        + (on ? "enabled" : "disabled") + ".", NamedTextColor.GREEN));
            }
            default -> {
                sender.sendMessage(Component.text(
                        "Usage: /practiceadmin ffa <list|info|reset|resettime|enable|disable|count> [arena]",
                        NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private void handleFfaInfo(CommandSender sender, String arenaId) {
        var arena = ffaService.get(arenaId);
        if (arena.isEmpty()) {
            sender.sendMessage(Component.text("Unknown arena: " + arenaId, NamedTextColor.RED));
            return;
        }
        FfaService.FfaArena a = arena.get();
        int remaining = ffaService.resetRemainingSeconds(a.id());
        String timer = remaining < 0 ? "off" : FfaResetTimes.format(remaining);
        sender.sendMessage(Component.text("=== " + a.id() + " FFA ===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Kit: " + a.kitId(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Enabled: " + a.enabled(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Players: " + ffaService.occupantCount(a.id()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Reset interval: " + FfaResetTimes.format(a.resetIntervalSeconds())
                        + (a.resetIntervalSeconds() > 0 ? "  (next in " + timer + ")" : ""),
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("TPA: " + a.tpaEnabled() + " | RTP queue: "
                + a.rtpQueueEnabled() + " | FFA Bot: " + a.botEnabled(), NamedTextColor.GRAY));
        if (a.region() != null) {
            sender.sendMessage(Component.text("Region: " + a.region().worldName()
                    + " (" + a.sizeX() + "x" + a.sizeZ() + ")", NamedTextColor.GRAY));
        }
    }

    /**
     * /practiceadmin statsreset [player] — mirrors {@code /admin reset point} so the wipe
     * lives under the admin banner. No player argument wipes EVERYONE (rating + all stats).
     */
    private boolean handleStatsReset(CommandSender sender, String[] args) {
        if (statsResetService == null) {
            sender.sendMessage(Component.text("Stats reset service not wired.", NamedTextColor.RED));
            return true;
        }
        // Resolve player UUIDs on the main thread, DB work off-thread, report back on main —
        // the same shape as {@code /admin reset point}.
        java.util.function.Consumer<Runnable> main = runnable -> Bukkit.getScheduler().runTask(plugin, runnable);
        if (args.length >= 2) {
            UUID target = resolveUuid(args[1]);
            if (target == null && playerRepository != null) {
                try {
                    target = playerRepository.findByUsername(args[1]).map(PlayerData::uuid).orElse(null);
                } catch (Exception ignored) {
                }
            }
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            UUID done = target;
            Runnable work = () -> {
                try {
                    statsResetService.resetPlayer(done);
                    main.accept(() -> sender.sendMessage(Component.text("Stats & rating reset for "
                            + args[1] + " (" + done + ").", NamedTextColor.GREEN)));
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Stats reset failed for " + args[1], e);
                    main.accept(() -> sender.sendMessage(Component.text("Stats reset failed.",
                            NamedTextColor.RED)));
                }
            };
            if (asyncExecutor != null) {
                asyncExecutor.execute(work);
            } else {
                work.run();
            }
            return true;
        }
        Runnable workAll = () -> {
            try {
                statsResetService.resetAll();
                main.accept(() -> sender.sendMessage(Component.text("ALL stats & ratings reset.",
                        NamedTextColor.GREEN)));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stats reset all failed", e);
                main.accept(() -> sender.sendMessage(Component.text("Stats reset failed.",
                        NamedTextColor.RED)));
            }
        };
        if (asyncExecutor != null) {
            asyncExecutor.execute(workAll);
        } else {
            workAll.run();
        }
        return true;
    }

    /** /practiceadmin broadcast <message...> — server-wide (except bots) announcement. */
    private boolean handleBroadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /practiceadmin broadcast <message...>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        Component line = Component.text("[", NamedTextColor.YELLOW)
                .append(Component.text("お知らせ", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(message));
        for (Player viewer : com.rumilance.practice.util.RealPlayers.online()) {
            viewer.sendMessage(line);
        }
        sender.sendMessage(Component.text("Broadcast sent.", NamedTextColor.GREEN));
        return true;
    }

    /** /practiceadmin kick <player> [reason] — routes through {@link BanService} for the announce. */
    private boolean handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /practiceadmin kick <player> [reason]",
                    NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1].charAt(0) == '@' ? args[1].substring(1) : args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }
        String reason = args.length > 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                : "Kicked by staff";
        if (banService != null) {
            banService.kick(target, sender.getName(), reason);
        } else {
            target.kick(Component.text(reason, NamedTextColor.RED));
        }
        return true;
    }

    /** /practiceadmin forceend <player> — draw-end the match the player is in (or watching). */
    private boolean handleForceEnd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /practiceadmin forceend <player>",
                    NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }
        if (matchService.forceEndMatch(target.getUniqueId())) {
            sender.sendMessage(Component.text("Force-ended the match of " + target.getName() + ".",
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(target.getName() + " is not in a live match.",
                    NamedTextColor.RED));
        }
        return true;
    }

    /** /practiceadmin forcematch <p1> <p2> <kit> [arena] — force-start a duel between free players. */
    private boolean handleForceMatch(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "Usage: /practiceadmin forcematch <player1> <player2> <kit> [arena]",
                    NamedTextColor.YELLOW));
            return true;
        }
        Player a = Bukkit.getPlayerExact(args[1]);
        Player b = Bukkit.getPlayerExact(args[2]);
        if (a == null || b == null) {
            sender.sendMessage(Component.text("One of the players is offline.", NamedTextColor.RED));
            return true;
        }
        if (a.getUniqueId().equals(b.getUniqueId())) {
            sender.sendMessage(Component.text("Pick two different players.", NamedTextColor.RED));
            return true;
        }
        if (kitService.get(args[3]).isEmpty()) {
            sender.sendMessage(Component.text("Unknown kit: " + args[3], NamedTextColor.RED));
            return true;
        }
        String busyA = matchService.busyReason(a.getUniqueId());
        if (busyA != null) {
            sender.sendMessage(Component.text(a.getName() + " is busy (" + busyA + ").",
                    NamedTextColor.RED));
            return true;
        }
        String busyB = matchService.busyReason(b.getUniqueId());
        if (busyB != null) {
            sender.sendMessage(Component.text(b.getName() + " is busy (" + busyB + ").",
                    NamedTextColor.RED));
            return true;
        }
        String arena = args.length >= 5 ? args[4] : null;
        matchService.startDuel(a.getUniqueId(), b.getUniqueId(), args[3],
                MatchMode.UNRANKED, 1, java.util.Map.of(), arena);
        sender.sendMessage(Component.text("Match started: " + a.getName() + " vs "
                + b.getName() + " [" + args[3] + (arena != null ? " @ " + arena : "") + "]",
                NamedTextColor.GREEN));
        return true;
    }

    /** /practiceadmin toggle <queue|map> <enable|disable> <id> — global queue/map switch. */
    private boolean handleToggle(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("/practiceadmin toggle <queue|map> <enable|disable> <id>",
                    NamedTextColor.YELLOW));
            return true;
        }
        boolean enable = args[2].equalsIgnoreCase("enable");
        if (args[1].equalsIgnoreCase("queue")) {
            kitService.setQueueEnabled(args[3], enable);
            sender.sendMessage(Component.text("Queue " + args[3] + " " + (enable ? "enabled" : "disabled"),
                    NamedTextColor.GREEN));
            return true;
        }
        if (args[1].equalsIgnoreCase("map")) {
            arenaStore.setEnabled(args[3], enable);
            arenaService.setTemplates(arenaStore.templates());
            sender.sendMessage(Component.text("Map " + args[3] + " " + (enable ? "enabled" : "disabled"),
                    NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /practiceadmin toggle <queue|map> <enable|disable> <id>",
                NamedTextColor.YELLOW));
        return true;
    }

    private static UUID resolveUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            // username path
        }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) {
            return online.getUniqueId();
        }
        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(raw);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached.getUniqueId();
        }
        return null;
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
            return TabCompletions.filter(current, "menu", "sign", "tool", "reload", "status",
                    "matches", "cleanup", "maintenance", "ffacommand", "ffa", "statsreset",
                    "broadcast", "time", "kick", "forceend", "forcematch", "toggle", "packpolicy");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "maintenance", "packpolicy" -> {
                    return TabCompletions.filter(current, "on", "off");
                }
                case "ffacommand" -> {
                    return TabCompletions.filter(current, "on", "off", "add", "remove", "list", "clear");
                }
                case "ffa" -> {
                    return TabCompletions.filter(current, "list", "info", "reset", "resettime",
                            "enable", "disable", "count");
                }
                case "toggle" -> {
                    return TabCompletions.filter(current, "queue", "map");
                }
                case "time" -> {
                    return TabCompletions.filter(current, "day", "noon", "night", "midnight", "sun");
                }
                case "kick", "forceend" -> {
                    return TabCompletions.filter(current,
                            Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                }
                case "forcematch" -> {
                    return TabCompletions.filter(current,
                            Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                }
                case "status" -> {
                    return TabCompletions.filter(current, "tps");
                }
                default -> {
                }
            }
        }
        if (args.length == 3) {
            switch (sub) {
                case "ffacommand" -> {
                    if (args[1].equalsIgnoreCase("remove")) {
                        return TabCompletions.filter(current,
                                ffaService.whitelistedCommands().toArray(String[]::new));
                    }
                    if (args[1].equalsIgnoreCase("add")) {
                        return TabCompletions.filter(current, "spawn", "msg", "baltop", "pay");
                    }
                }
                case "ffa" -> {
                    if (List.of("info", "reset", "resettime", "enable", "disable").contains(
                            args[1].toLowerCase(Locale.ROOT))) {
                        return TabCompletions.filter(current,
                                ffaService.list().stream().map(FfaService.FfaArena::id).toList());
                    }
                }
                case "toggle" -> {
                    return TabCompletions.filter(current, "enable", "disable");
                }
                default -> {
                }
            }
        }
        if (args.length == 4 && sub.equals("toggle")) {
            if (args[1].equalsIgnoreCase("queue")) {
                return TabCompletions.filter(current,
                        kitService.all().stream().map(k -> k.name()).toList());
            }
            if (args[1].equalsIgnoreCase("map")) {
                return TabCompletions.filter(current,
                        arenaStore.templates().stream().map(
                                t -> t.name()).toList());
            }
        }
        if (args.length == 4 && sub.equals("ffa") && args[1].equalsIgnoreCase("resettime")) {
            return TabCompletions.filter(current, "30s", "5min", "10min", "1hour", "2hour", "off");
        }
        if (args.length == 4 && sub.equals("forcematch")) {
            return TabCompletions.filter(current,
                    kitService.enabled().stream().map(k -> k.name()).toList());
        }
        if (args.length == 2 && sub.equals("sign")) {
            return TabCompletions.filter(current,
                    kitService.enabled().stream().map(k -> k.name()).toList());
        }
        return List.of();
    }
}
