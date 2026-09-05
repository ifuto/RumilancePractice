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
 * {@code /lbspawn kill} — places the monthly kill leaderboard at the executor's feet,
 * yawed toward the lobby spawn (no pitch). {@code /lbspawn remove} deletes it.
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
                leaderboardService.placeKillBoard(player.getLocation());
                player.sendMessage(Component.text(
                        "月間キルリーダーボードを足元に設置しました(ロビースポーン向き)。",
                        NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (leaderboardService.remove()) {
                    player.sendMessage(Component.text("リーダーボードを削除しました。", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("設置済みのリーダーボードがありません。", NamedTextColor.RED));
                }
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /lbspawn <kill|remove>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("kill", "remove").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
