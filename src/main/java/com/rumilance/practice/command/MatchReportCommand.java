package com.rumilance.practice.command;

import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.settings.SettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /matchreport} opens the combat report card for the player's most recent match.
 * A {@code toggle} subcommand flips the "auto-give report book after matches" setting without
 * having to open {@code /setting}.
 */
public final class MatchReportCommand implements CommandExecutor, TabCompleter {

    private final MatchService matchService;
    private final SettingsService settingsService;

    public MatchReportCommand(MatchService matchService, SettingsService settingsService) {
        this.matchService = matchService;
        this.settingsService = settingsService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view match reports.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            PlayerSettings settings = settingsService.get(player);
            boolean next = !settings.showMatchReport();
            settingsService.update(settings.withShowMatchReport(next));
            player.sendMessage(Component.text("Auto match report book: ", NamedTextColor.GRAY)
                    .append(Component.text(next ? "ON" : "OFF",
                            next ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            return true;
        }
        matchService.openMatchReport(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(TabCompletions.current(args), "toggle");
        }
        return List.of();
    }
}
