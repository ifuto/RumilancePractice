package com.rumilance.practice.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Thin adapter for /accept and /deny.
 */
public final class AcceptDenyCommand implements CommandExecutor {

    private final DuelCommand duelCommand;

    public AcceptDenyCommand(DuelCommand duelCommand) {
        this.duelCommand = duelCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (command.getName().equalsIgnoreCase("accept")) {
            duelCommand.handleAccept(player, args.length > 0 ? args[0] : null);
        } else {
            duelCommand.handleDeny(player, args.length > 0 ? args[0] : null);
        }
        return true;
    }
}
