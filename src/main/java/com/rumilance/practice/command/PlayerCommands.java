package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.ArrowEffectGui;
import com.rumilance.practice.gui.menus.EditKitGui;
import com.rumilance.practice.gui.menus.FfaListGui;
import com.rumilance.practice.gui.menus.PlayersGui;
import com.rumilance.practice.gui.menus.ProfileGui;
import com.rumilance.practice.gui.menus.SettingsGui;
import com.rumilance.practice.gui.menus.SpectateListGui;
import com.rumilance.practice.gui.menus.StatsKitGui;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.MatchHistoryEntry;
import com.rumilance.practice.model.RankedKitStats;
import com.rumilance.practice.punishment.ChatBanService;
import com.rumilance.practice.spectator.SpectatorService;
import com.rumilance.practice.stats.StatsService;
import com.rumilance.practice.util.AsyncExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerCommands implements CommandExecutor, TabCompleter {

    public enum Type {
        PING, STATS, PROFILE, RANKING, PLAYERS, SPEC, SPECGUI, SETTING, FFA, EKIT, ARROW, HISTORY, KDR, OBJECTION
    }

    private final Type type;
    private final Plugin plugin;
    private final AsyncExecutor asyncExecutor;
    private final StatsService statsService;
    private final KitService kitService;
    private final StatsKitGui statsKitGui;
    private final ProfileGui profileGui;
    private final SettingsGui settingsGui;
    private final PlayersGui playersGui;
    private final SpectateListGui spectateListGui;
    private final SpectatorService spectatorService;
    private final FfaListGui ffaListGui;
    private final EditKitGui editKitGui;
    private final ArrowEffectGui arrowEffectGui;
    private final ChatBanService chatBanService;

    public PlayerCommands(
            Type type,
            Plugin plugin,
            AsyncExecutor asyncExecutor,
            StatsService statsService,
            KitService kitService,
            StatsKitGui statsKitGui,
            ProfileGui profileGui,
            SettingsGui settingsGui,
            PlayersGui playersGui,
            SpectateListGui spectateListGui,
            SpectatorService spectatorService,
            FfaListGui ffaListGui,
            EditKitGui editKitGui,
            ArrowEffectGui arrowEffectGui,
            ChatBanService chatBanService
    ) {
        this.type = type;
        this.plugin = plugin;
        this.asyncExecutor = asyncExecutor;
        this.statsService = statsService;
        this.kitService = kitService;
        this.statsKitGui = statsKitGui;
        this.profileGui = profileGui;
        this.settingsGui = settingsGui;
        this.playersGui = playersGui;
        this.spectateListGui = spectateListGui;
        this.spectatorService = spectatorService;
        this.ffaListGui = ffaListGui;
        this.editKitGui = editKitGui;
        this.arrowEffectGui = arrowEffectGui;
        this.chatBanService = chatBanService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        switch (type) {
            case PING -> {
                Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : player;
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text(target.getName() + " ping: " + target.getPing() + "ms",
                        NamedTextColor.GREEN));
            }
            case STATS -> {
                Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : player;
                if (args.length > 0 && target == null) {
                    player.sendMessage(Component.text("Player not online.", NamedTextColor.RED));
                    return true;
                }
                statsKitGui.openFor(player, (target == null ? player : target).getUniqueId());
            }
            case PROFILE -> {
                Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : player;
                if (target == null) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (offline == null) {
                        player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                        return true;
                    }
                    profileGui.openFor(player, offline.getUniqueId());
                    return true;
                }
                profileGui.openFor(player, target.getUniqueId());
            }
            case RANKING -> handleRanking(player, args);
            case PLAYERS -> playersGui.open(player);
            case SPEC -> {
                if (args.length < 1) {
                    player.sendMessage(Component.text("Usage: /spec <player>", NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                spectatorService.trySpectate(player, target);
            }
            case SPECGUI -> spectateListGui.open(player);
            case SETTING -> settingsGui.open(player);
            case FFA -> ffaListGui.open(player);
            case EKIT -> editKitGui.openKitPicker(player);
            case ARROW -> {
                if (!player.hasPermission("rumilance.user.mem")
                        && !player.hasPermission("rumilance.user.vip")
                        && !player.hasPermission("rumilance.user.vip_plus")
                        && !player.isOp()) {
                    player.sendMessage(Component.text("Member+ required.", NamedTextColor.RED));
                    return true;
                }
                arrowEffectGui.open(player);
            }
            case HISTORY -> asyncExecutor.execute(() -> {
                try {
                    List<MatchHistoryEntry> history = statsService.recentRanked(player.getUniqueId(), 10);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (history.isEmpty()) {
                            player.sendMessage(Component.text("No ranked history.", NamedTextColor.GRAY));
                            return;
                        }
                        for (MatchHistoryEntry entry : history) {
                            player.sendMessage(Component.text(
                                    entry.kit() + " | winner="
                                            + (entry.winner() == null ? "draw" : StatsService.nameOf(entry.winner())),
                                    NamedTextColor.AQUA));
                        }
                    });
                } catch (Exception e) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> player.sendMessage(Component.text("History load failed.", NamedTextColor.RED)));
                }
            });
            case KDR -> {
                Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : player;
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                String kit = args.length > 1 ? args[1] : null;
                Player finalTarget = target;
                String finalKit = kit;
                asyncExecutor.execute(() -> {
                    try {
                        String statLabel;
                        double kd;
                        if (finalKit == null) {
                            var kits = statsService.allKits(finalTarget.getUniqueId());
                            int wins = kits.stream().mapToInt(RankedKitStats::wins).sum();
                            int losses = kits.stream().mapToInt(RankedKitStats::losses).sum();
                            kd = (double) wins / Math.max(1, losses);
                            statLabel = "overall";
                        } else {
                            RankedKitStats stats = statsService.kitStats(finalTarget.getUniqueId(), finalKit)
                                    .orElse(RankedKitStats.starting(finalTarget.getUniqueId(), finalKit));
                            kd = statsService.kd(stats);
                            statLabel = finalKit;
                        }
                        double finalKd = kd;
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                player.sendMessage(Component.text(finalTarget.getName() + " " + statLabel + " K/D: "
                                        + String.format("%.2f", finalKd)
                                        + " (ranked only)", NamedTextColor.GREEN)));
                    } catch (Exception e) {
                        plugin.getServer().getScheduler().runTask(plugin,
                                () -> player.sendMessage(Component.text("K/D load failed.", NamedTextColor.RED)));
                    }
                });
            }
            case OBJECTION -> {
                if (args.length < 1) {
                    player.sendMessage(Component.text("Usage: /objection <reason>", NamedTextColor.YELLOW));
                    return true;
                }
                chatBanService.submitObjection(player, String.join(" ", args));
            }
        }
        return true;
    }

    private void handleRanking(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /ranking <elo|kill|matches|winstreak>", NamedTextColor.YELLOW));
            return;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        asyncExecutor.execute(() -> {
            try {
                List<Component> lines = new ArrayList<>();
                switch (mode) {
                    case "winstreak" -> {
                        int rank = 1;
                        for (RankedKitStats stats : statsService.topWinStreak(10)) {
                            lines.add(Component.text("#" + rank + " " + StatsService.nameOf(stats.uuid())
                                    + " kit=" + stats.kit()
                                    + " streak=" + stats.winStreak()
                                    + " elo=" + stats.elo(), NamedTextColor.GOLD));
                            rank++;
                        }
                    }
                    case "elo" -> {
                        int rank = 1;
                        for (RankedKitStats stats : statsService.topEloOverall(10)) {
                            lines.add(Component.text("#" + rank + " " + StatsService.nameOf(stats.uuid())
                                    + " kit=" + stats.kit()
                                    + " elo=" + stats.elo()
                                    + " W/L=" + stats.wins() + "/" + stats.losses(), NamedTextColor.GOLD));
                            rank++;
                        }
                    }
                    case "kill" -> {
                        int rank = 1;
                        for (var entry : statsService.topDailyKills(10)) {
                            lines.add(Component.text("#" + rank + " " + StatsService.nameOf(entry.playerId())
                                    + " daily kills=" + entry.kills(), NamedTextColor.GOLD));
                            rank++;
                        }
                    }
                    case "matches" -> {
                        int rank = 1;
                        for (var entry : statsService.topDailyMatches(10)) {
                            lines.add(Component.text("#" + rank + " " + StatsService.nameOf(entry.playerId())
                                    + " daily matches=" + entry.matches(), NamedTextColor.GOLD));
                            rank++;
                        }
                    }
                    default -> {
                    }
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (lines.isEmpty()) {
                        player.sendMessage(Component.text("No ranking data yet.", NamedTextColor.GRAY));
                        return;
                    }
                    lines.forEach(player::sendMessage);
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.sendMessage(Component.text("Ranking failed.", NamedTextColor.RED)));
            }
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (type == Type.RANKING && args.length == 1) {
            return List.of("elo", "kill", "matches", "winstreak");
        }
        if (type == Type.KDR && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (type == Type.KDR && args.length == 2) {
            return kitService.enabled().stream().map(k -> k.name()).toList();
        }
        if (args.length == 1 && (type == Type.PING || type == Type.STATS || type == Type.PROFILE || type == Type.SPEC)) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
