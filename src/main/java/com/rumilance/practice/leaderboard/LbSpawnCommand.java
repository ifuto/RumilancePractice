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
 * leaderboard one block above the block the executor is looking at, yawed toward the lobby
 * spawn (the board always faces the spawn, never tracks viewers).
 * {@code /lbspawn remove <kill|streak|all>} deletes boards.
 */
public final class LbSpawnCommand implements CommandExecutor, TabCompleter {

    /**
     * How far along the executor's sight line a target block is accepted. Vanilla interaction
     * reach: 100 blocks used to accept distant walls/ceilings along an upward gaze, so the
     * board materialised ~10 blocks above the executor instead of at their feet.
     */
    private static final int LOOK_RANGE = 5;

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
                leaderboardService.place("kill", sightSpot(player));
                player.sendMessage(Component.text(
                        "月間キルリーダーボードを視線の先のブロックの上に設置しました(ロビースポーン向き)。",
                        NamedTextColor.GREEN));
            }
            case "streak" -> {
                leaderboardService.place("streak", sightSpot(player));
                player.sendMessage(Component.text(
                        "年間最大連勝リーダーボードを視線の先のブロックの上に設置しました(ロビースポーン向き)。",
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

    /**
     * The board goes ONE block above the block the executor is looking at (horizontally
     * centred). Looking at the sky (no block in range) falls back to the executor's feet.
     */
    private static org.bukkit.Location sightSpot(Player player) {
        org.bukkit.block.Block target = player.getTargetBlockExact(LOOK_RANGE,
                org.bukkit.FluidCollisionMode.NEVER);
        if (target == null) {
            return player.getLocation();
        }
        return new org.bukkit.Location(player.getWorld(),
                target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
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
