package com.rumilance.practice.command;

import com.rumilance.practice.replay.ReplayService;
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

/** Transport controls for the report replay viewer. */
public final class ReplayCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("pause", "rewind", "forward", "speed", "restart", "stop");

    private final ReplayService replayService;

    public ReplayCommand(ReplayService replayService) {
        this.replayService = replayService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (!replayService.isReplaying(player.getUniqueId())) {
            player.sendMessage(Component.text("再生中のリプレイがありません。/reportlist から開始してください。",
                    NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "pause" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pause", "play" -> replayService.togglePause(player);
            case "rewind" -> replayService.rewind(player);
            case "forward" -> replayService.forward(player);
            case "speed" -> replayService.cycleSpeed(player);
            case "restart" -> replayService.restart(player);
            case "stop" -> replayService.stop(player);
            default -> player.sendMessage(Component.text(
                    "/replay <pause|rewind|forward|speed|restart|stop>", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(TabCompletions.current(args), SUBS);
        }
        return List.of();
    }
}
