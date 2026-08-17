package com.rumilance.practice.command;

import com.rumilance.practice.duel.DuelRequestService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Thin adapter for /accept and /deny. /accept and /deny without an argument resolve the most
 * recent incoming duel request; with a player name they accept/deny that specific sender.
 * Tab completion suggests every player who currently has a pending request to the sender,
 * plus {@code all} for /deny.
 */
public final class AcceptDenyCommand implements CommandExecutor, TabCompleter {

    private final DuelCommand duelCommand;
    private final DuelRequestService duelRequestService;

    public AcceptDenyCommand(DuelCommand duelCommand, DuelRequestService duelRequestService) {
        this.duelCommand = duelCommand;
        this.duelRequestService = duelRequestService;
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (DuelRequestService.RichDuelRequest req : duelRequestService.incoming(player.getUniqueId())) {
            String name = nameOf(req.sender());
            if (name != null) {
                names.add(name);
            }
        }
        // "all" is only meaningful for /deny and only when something is actually pending.
        if (command.getName().equalsIgnoreCase("deny") && !names.isEmpty()) {
            names.add("all");
        }
        // Only ACTUAL pending requesters are valid targets — no fallback to random online
        // players (suggesting names that /accept would just reject is worse than silence).
        return TabCompletions.filter(TabCompletions.current(args), names);
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : null;
    }
}
