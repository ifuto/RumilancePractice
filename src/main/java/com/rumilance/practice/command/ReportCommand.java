package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.ReportGui;
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

/**
 * {@code /report} (no arguments) opens the reason menu for the player's most recent 1v1
 * opponent. Any argument is treated as a mistake and the correct usage is explained.
 */
public final class ReportCommand implements CommandExecutor, TabCompleter {

    private final ReportGui reportGui;

    public ReportCommand(ReportGui reportGui) {
        this.reportGui = reportGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length > 0) {
            player.sendMessage(Component.text("/reportで前回試合した相手を追放できます。", NamedTextColor.YELLOW));
            return true;
        }
        reportGui.openReport(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
