package com.rumilance.practice.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /party} (alias {@code /p}) command group: invite, accept, leave, kick, disband,
 * warp, list, and chat. Tab completion suggests online players for invite/accept/kick and the
 * sub-command verbs for the first argument.
 */
public final class PartyCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "invite", "accept", "leave", "kick", "disband", "warp", "list", "chat");

    private final PartyService partyService;

    public PartyCommand(PartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use parties.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party invite <player>", NamedTextColor.YELLOW));
                    return true;
                }
                partyService.invite(player, args[1]);
            }
            case "accept" -> {
                String inviter = args.length > 1 ? args[1] : null;
                partyService.accept(player, inviter);
            }
            case "leave" -> partyService.leave(player);
            case "disband" -> partyService.disband(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party kick <player>", NamedTextColor.YELLOW));
                    return true;
                }
                partyService.kick(player, args[1]);
            }
            case "warp" -> partyService.warp(player);
            case "list" -> sendList(player);
            case "chat" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party chat <message>", NamedTextColor.YELLOW));
                    return true;
                }
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                partyService.sendPartyChat(player, message);
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("Party commands:", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        player.sendMessage(Component.text("/party invite <player>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party accept [player]", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party leave", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party kick <player>", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party disband", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party warp", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party list", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/party chat <message>", NamedTextColor.GRAY));
    }

    private void sendList(Player player) {
        List<String> names = partyService.membersOnlineNames(player.getUniqueId());
        if (names.isEmpty()) {
            player.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Party members (" + names.size() + "): "
                + String.join(", ", names), NamedTextColor.AQUA));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return List.of();
        }
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return filter(current, SUBCOMMANDS);
        }
        if (args.length == 2 && List.of("invite", "accept", "kick").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(((Player) sender).getUniqueId())) {
                    names.add(online.getName());
                }
            }
            return filter(current, names);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> candidates) {
        return candidates.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
