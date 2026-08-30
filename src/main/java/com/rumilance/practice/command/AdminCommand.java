package com.rumilance.practice.command;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.stats.StatsResetService;
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
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@code /admin reset point [player]} — wipe OpenSkill ratings and all stats (ranked, FFA,
 * daily, match history). Offline names resolve from the players table.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final StatsResetService statsResetService;
    private final PlayerRepository playerRepository;
    private final AsyncExecutor asyncExecutor;
    private final com.rumilance.practice.originalkit.OriginalKitService originalKitService;
    private com.rumilance.practice.scoreboard.ScoreboardService scoreboardService;

    public AdminCommand(
            Plugin plugin,
            StatsResetService statsResetService,
            PlayerRepository playerRepository,
            AsyncExecutor asyncExecutor
    ) {
        this(plugin, statsResetService, playerRepository, asyncExecutor, null);
    }

    public AdminCommand(
            Plugin plugin,
            StatsResetService statsResetService,
            PlayerRepository playerRepository,
            AsyncExecutor asyncExecutor,
            com.rumilance.practice.originalkit.OriginalKitService originalKitService
    ) {
        this.plugin = plugin;
        this.statsResetService = statsResetService;
        this.playerRepository = playerRepository;
        this.asyncExecutor = asyncExecutor;
        this.originalKitService = originalKitService;
    }

    public void setScoreboardService(com.rumilance.practice.scoreboard.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2
                || !args[0].equalsIgnoreCase("reset")
                || !args[1].equalsIgnoreCase("point")) {
            sender.sendMessage(Component.text("使い方: /admin reset point [プレイヤー]", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length == 2) {
            asyncExecutor.runAsync(() -> {
                try {
                    statsResetService.resetAll();
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                            "全プレイヤーの評価ポイントと統計をデフォルトに戻しました。", NamedTextColor.GREEN)));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to reset all stats", e);
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                            "統計のリセットに失敗しました。", NamedTextColor.RED)));
                }
            });
            return true;
        }
        String name = args[2];
        UUID fromMain = resolveOnMain(name);
        asyncExecutor.runAsync(() -> {
            try {
                UUID target = fromMain;
                if (target == null) {
                    target = playerRepository.findByUsername(name).map(PlayerData::uuid).orElse(null);
                }
                if (target == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                            "プレイヤーが見つかりません: " + name, NamedTextColor.RED)));
                    return;
                }
                statsResetService.resetPlayer(target);
                UUID done = target;
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        name + " の評価ポイントと統計をリセットしました (" + done + ")。", NamedTextColor.GREEN)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reset stats for " + name, e);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(
                        "統計のリセットに失敗しました。", NamedTextColor.RED)));
            }
        });
        return true;
    }

    private static UUID resolveOnMain(String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // username path
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached.getUniqueId();
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String current = TabCompletions.current(args);
        if (args.length == 1) {
            return TabCompletions.filter(current, "reset");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return TabCompletions.filter(current, "point");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("point")) {
            List<String> names = new ArrayList<>(TabCompletions.onlinePlayers(
                    sender instanceof Player player ? player : null));
            return TabCompletions.filter(current, names);
        }
        return List.of();
    }
}
