package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.MatchInventoryGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Opens the stored end-of-match inventory GUI for a duel (used by kill-feed clicks). */
public final class MatchInvCommand implements CommandExecutor {

    private final MatchInventoryGui gui;

    public MatchInvCommand(MatchInventoryGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /matchinv <matchId> [playerUuid]", NamedTextColor.YELLOW));
            return true;
        }
        try {
            UUID matchId = UUID.fromString(args[0]);
            UUID focus = args.length >= 2 ? UUID.fromString(args[1]) : null;
            gui.open(player, matchId, focus);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid match id or player uuid.", NamedTextColor.RED));
        }
        return true;
    }
}
