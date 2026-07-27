package com.rumilance.practice.command;

import com.rumilance.practice.queue.QueueCoordinator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class QueueLeaveCommand implements CommandExecutor {

    private final QueueCoordinator queueCoordinator;

    public QueueLeaveCommand(QueueCoordinator queueCoordinator) {
        this.queueCoordinator = queueCoordinator;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            queueCoordinator.leave(player);
            return true;
        }
        player.sendMessage(Component.text("Usage: /queue leave", NamedTextColor.YELLOW));
        return true;
    }
}
