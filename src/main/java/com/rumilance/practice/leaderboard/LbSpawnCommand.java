package com.rumilance.practice.leaderboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /lbspawn <kill|streak>} — places the floating monthly-kill or annual-win-streak
 * leaderboard at the executor's feet, yawed toward the lobby spawn (no pitch).
 * {@code /lbspawn remove <kill|streak|all>} deletes boards.
 */
public final class LbSpawnCommand implements CommandExecutor, TabCompleter {

    private final KillLeaderboardService leaderboardService;

    public LbSpawnCommand(KillLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can place leaderboards.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "kill" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "kill" -> {
                leaderboardService.place("kill", player.getLocation());
                player.sendMessage(Component.text(
                        "月間キルリーダーボードを足元に設置しました(ロビースポーン向き)。",
                        NamedTextColor.GREEN));
            }
            case "streak" -> {
                leaderboardService.place("streak", player.getLocation());
                player.sendMessage(Component.text(
                        "年間最大連勝リーダーボードを足元に設置しました(ロビースポーン向き)。",
                        NamedTextColor.GREEN));
            }
            case "remove" -> {
                String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
                boolean removed = false;
                if ("all".equals(what)) {
                    removed |= leaderboardService.remove("kill");
                    removed |= leaderboardService.remove("streak");
                } else {
                    removed = leaderboardService.remove(what);
                }
                if (removed) {
                    player.sendMessage(Component.text("リーダーボードを削除しました。", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("設置済みのリーダーボードがありません。", NamedTextColor.RED));
                }
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /lbspawn <kill|streak|remove <kill|streak|all>>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return List.of("kill", "streak", "remove").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && "remove".equals(args[0].toLowerCase(Locale.ROOT))) {
            String p2 = args[1].toLowerCase(Locale.ROOT);
            return List.of("kill", "streak", "all").stream()
                    .filter(s -> s.startsWith(p2))
                    .toList();
        }
        return List.of();
    }
}
