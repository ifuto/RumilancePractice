package com.rumilance.practice.command;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.session.MatchMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OP-only match administration:
 * <ul>
 *   <li>{@code /forceend <player>} — force-ends the match the player is in (or watching) as a draw.</li>
 *   <li>{@code /forcematch <player1> <player2> <kit> [arena]} — force-starts a duel between two
 *   FREE players with the given kit and (optionally) arena template.</li>
 * </ul>
 */
public final class AdminMatchCommand implements CommandExecutor, TabCompleter {

    private final MatchService matchService;
    private final KitService kitService;
    private final ArenaService arenaService;

    public AdminMatchCommand(MatchService matchService, KitService kitService, ArenaService arenaService) {
        this.matchService = matchService;
        this.kitService = kitService;
        this.arenaService = arenaService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("OP only.", NamedTextColor.RED));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("forceend")) {
            return handleForceEnd(sender, args);
        }
        return handleForceMatch(sender, args);
    }

    private boolean handleForceEnd(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /forceend <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
            return true;
        }
        if (matchService.forceEndMatch(target.getUniqueId())) {
            sender.sendMessage(Component.text("Force-ended the match of " + target.getName() + ".",
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(target.getName() + " is not in a live match.",
                    NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleForceMatch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /forcematch <player1> <player2> <kit> [arena]", NamedTextColor.YELLOW));
            return true;
        }
        Player playerA = Bukkit.getPlayerExact(args[0]);
        Player playerB = Bukkit.getPlayerExact(args[1]);
        if (playerA == null || playerB == null) {
            sender.sendMessage(Component.text("One of the players is offline.", NamedTextColor.RED));
            return true;
        }
        if (playerA.getUniqueId().equals(playerB.getUniqueId())) {
            sender.sendMessage(Component.text("Pick two different players.", NamedTextColor.RED));
            return true;
        }
        String kitId = args[2];
        if (kitService.get(kitId).isEmpty()) {
            sender.sendMessage(Component.text("Unknown kit: " + kitId, NamedTextColor.RED));
            return true;
        }
        // Both players must be free — force-match never rips anyone out of an ongoing activity.
        String busyA = matchService.busyReason(playerA.getUniqueId());
        if (busyA != null) {
            sender.sendMessage(Component.text(
                    playerA.getName() + " is busy (" + busyA + ").", NamedTextColor.RED));
            return true;
        }
        String busyB = matchService.busyReason(playerB.getUniqueId());
        if (busyB != null) {
            sender.sendMessage(Component.text(
                    playerB.getName() + " is busy (" + busyB + ").", NamedTextColor.RED));
            return true;
        }
        String arena = args.length >= 4 ? args[3] : null;
        matchService.startDuel(playerA.getUniqueId(), playerB.getUniqueId(), kitId,
                MatchMode.UNRANKED, 1, Map.of(), arena);
        sender.sendMessage(Component.text("Match started: " + playerA.getName() + " vs "
                + playerB.getName() + " [" + kitId + (arena != null ? " @ " + arena : "") + "]",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            return List.of();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("forceend")) {
            if (args.length == 1) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
            }
            return List.of();
        }
        // /forcematch <p1> <p2> <kit> [arena]
        if (args.length <= 2) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[args.length - 1]);
        }
        if (args.length == 3) {
            return filter(kitService.enabled().stream().map(k -> k.name()).toList(), args[2]);
        }
        if (args.length == 4) {
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            names.add("random");
            arenaService.templates().forEach(t -> names.add(t.name()));
            return filter(names, args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted()
                .toList();
    }
}
