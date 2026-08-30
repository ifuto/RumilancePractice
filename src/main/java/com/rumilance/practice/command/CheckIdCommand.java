package com.rumilance.practice.command;

import com.rumilance.practice.duel.DuelIds;
import com.rumilance.practice.duel.DuelLogStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class CheckIdCommand implements CommandExecutor {

    private final DuelLogStore store;

    public CheckIdCommand(DuelLogStore store) {
        this.store = store;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1 || !DuelIds.valid(args[0])) {
            sender.sendMessage(Component.text("Usage: /checkid <5-character id>", NamedTextColor.YELLOW));
            return true;
        }
        store.find(args[0]).ifPresentOrElse(
                entry -> sender.sendMessage(
                        Component.text("Duel ", NamedTextColor.AQUA)
                                .append(Component.text(entry.id(), NamedTextColor.WHITE))
                                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                                .append(Component.text(entry.player1(), NamedTextColor.RED))
                                .append(Component.text(" vs ", NamedTextColor.GRAY))
                                .append(Component.text(entry.player2(), NamedTextColor.BLUE))),
                () -> sender.sendMessage(Component.text("No duel with that ID.", NamedTextColor.RED)));
        return true;
    }
}
