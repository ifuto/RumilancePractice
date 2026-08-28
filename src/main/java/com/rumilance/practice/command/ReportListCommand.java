package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.ReportListGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Operator command opening the pending report review list. */
public final class ReportListCommand implements CommandExecutor {

    private final ReportListGui reportListGui;

    public ReportListCommand(ReportListGui reportListGui) {
        this.reportListGui = reportListGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        reportListGui.open(player);
        return true;
    }
}
