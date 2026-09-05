package com.rumilance.practice.team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.UUID;

/**
 * {@code /team} — owner-invite style teams with public/private visibility, manual/auto red-blue
 * split, and match start. Subcommands: create, invite, join, leave, kick, public, list, info,
 * side, autosplit, clearsides, start, disband.
 */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamService teamService;
    private final com.rumilance.practice.kit.KitService kitService;
    private final com.rumilance.practice.gui.menus.TeamHubGui teamHubGui;
    private final com.rumilance.practice.gui.menus.TeamsBrowserGui teamsBrowserGui;
    private final com.rumilance.practice.locale.MessageService messageService;

    public TeamCommand(TeamService teamService, com.rumilance.practice.kit.KitService kitService,
                       com.rumilance.practice.gui.menus.TeamHubGui teamHubGui,
                       com.rumilance.practice.gui.menus.TeamsBrowserGui teamsBrowserGui) {
        this(teamService, kitService, teamHubGui, teamsBrowserGui, null);
    }

    public TeamCommand(TeamService teamService, com.rumilance.practice.kit.KitService kitService,
                       com.rumilance.practice.gui.menus.TeamHubGui teamHubGui,
                       com.rumilance.practice.gui.menus.TeamsBrowserGui teamsBrowserGui,
                       com.rumilance.practice.locale.MessageService messageService) {
        this.teamService = teamService;
        this.kitService = kitService;
        this.teamHubGui = teamHubGui;
        this.teamsBrowserGui = teamsBrowserGui;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use teams.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (teamService.teamOf(player.getUniqueId()).isPresent()) {
                teamHubGui.open(player);
            } else {
                teamsBrowserGui.open(player);
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                boolean isPublic = args.length > 1 && args[1].equalsIgnoreCase("public");
                String name = args.length > 2
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                        : player.getName() + "'s Team";
                TeamService.Result r = teamService.create(player, name, isPublic);
                if (r != TeamService.Result.OK) player.sendMessage(err(r));
            }
            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team invite <player>", NamedTextColor.YELLOW));
                    return true;
                }
                TeamService.Result r = teamService.invite(player, args[1]);
                if (r != TeamService.Result.OK) {
                    Player invited = Bukkit.getPlayerExact(args[1]);
                    UUID invitedId = invited == null ? null : invited.getUniqueId();
                    player.sendMessage(Component.text(teamService.errorMessage(player, r, invitedId), NamedTextColor.RED)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                }
            }
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Public teams:", NamedTextColor.AQUA));
                    if (teamService.publicTeams().isEmpty()) {
                        player.sendMessage(Component.text("  (none)", NamedTextColor.GRAY));
                    }
                    teamService.publicTeams().forEach(t ->
                            player.sendMessage(Component.text("  - " + t.name() + " (" + t.size() + ")", NamedTextColor.GRAY)));
                    return true;
                }
                // Team names may contain spaces (e.g. "Steve's Team") — join the rest of the args.
                String teamName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                TeamService.Result r = teamService.join(player, teamName);
                if (r != TeamService.Result.OK) player.sendMessage(err(r));
            }
            case "decline" -> teamService.decline(player);
            case "leave" -> teamService.leave(player);
            case "kick" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team kick <player>", NamedTextColor.YELLOW));
                    return true;
                }
                teamService.kick(player, args[1]);
            }
            case "public" -> teamService.togglePublic(player);
            case "info" -> teamService.teamOf(player.getUniqueId()).ifPresentOrElse(
                    t -> player.sendMessage(Component.text(teamService.info(t), NamedTextColor.AQUA)),
                    () -> player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED)));
            case "side" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /team side <player> <red|blue>", NamedTextColor.YELLOW));
                    return true;
                }
                TeamService.Result r = teamService.assignSide(player, args[1], args[2]);
                if (r != TeamService.Result.OK) player.sendMessage(err(r));
            }
            case "autosplit" -> {
                TeamService.Result r = teamService.autoAssign(player);
                if (r == TeamService.Result.OK) {
                    player.sendMessage(Component.text("Teams split automatically.", NamedTextColor.GREEN));
                } else player.sendMessage(err(r));
            }
            case "clearsides" -> teamService.clearSides(player);
            case "teamcount" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                            "Usage: /team teamcount <2-" + teamService.maxTeamsFor(player.getUniqueId()) + ">",
                            NamedTextColor.YELLOW));
                    return true;
                }
                int count;
                try {
                    count = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Usage: /team teamcount <number>", NamedTextColor.YELLOW));
                    return true;
                }
                TeamService.Result r = teamService.setTeamCount(player, count);
                if (r != TeamService.Result.OK) {
                    player.sendMessage(err(r));
                } else {
                    player.sendMessage(Component.text(
                            "Team battle now uses " + teamService.teamOf(player.getUniqueId())
                                    .map(t -> t.teamCount()).orElse(2) + " teams.",
                            NamedTextColor.GREEN));
                }
            }
            case "start" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team start <kit>", NamedTextColor.YELLOW));
                    return true;
                }
                String kitId = args[1].toLowerCase(Locale.ROOT);
                if (kitService.get(kitId).filter(k -> k.enabled()).isEmpty()) {
                    player.sendMessage(err(TeamService.Result.KIT_NOT_FOUND));
                    return true;
                }
                TeamService.Result r = teamService.start(player, kitId);
                if (r != TeamService.Result.OK) player.sendMessage(err(r));
            }
            case "disband" -> teamService.disband(player);
            case "list" -> {
                player.sendMessage(Component.text("Public teams:", NamedTextColor.AQUA));
                if (teamService.publicTeams().isEmpty()) {
                    player.sendMessage(Component.text("  (none)", NamedTextColor.GRAY));
                }
                teamService.publicTeams().forEach(t ->
                        player.sendMessage(Component.text("  " + t.name() + " - " + t.size() + " players",
                                NamedTextColor.GRAY)));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(Component.text("Team commands:", NamedTextColor.AQUA));
        p.sendMessage(Component.text("/team create [public] [name]", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team invite <player>", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team join <name|list>", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team side <player> <red|blue>", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team autosplit | /team clearsides", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team start <kit> | /team disband", NamedTextColor.GRAY));
    }

    private Component err(TeamService.Result r) {
        String msg = switch (r) {
            case ALREADY_IN_TEAM -> "You are already in a team.";
            case NOT_OWNER -> "Only the team owner can do that.";
            case NOT_IN_TEAM -> "You are not in a team.";
            case TEAM_FULL -> "That team is full.";
            case TARGET_OFFLINE -> "Player not found.";
            case TARGET_IN_TEAM -> "That player is already in a team.";
            case NO_INVITE -> "No invite to that private team.";
            case INVITE_EXPIRED -> "Invite expired.";
            case INVALID_NAME -> "Invalid team name (max 24 chars).";
            case TOO_SMALL -> "Need at least 2 players.";
            case UNBALANCED -> "Assign everyone to red/blue first (use /team side or /team autosplit).";
            case OWNER_CANNOT_LEAVE -> "The owner cannot be kicked. Disband the team instead.";
            case INVALID_SIDE -> "Side must be RED or BLUE.";
            case KIT_NOT_FOUND -> "Kit not found.";
            case NO_ARENA -> "No arena available right now.";
            case COOLDOWN -> "Wait before sending another invite.";
            default -> r.name();
        };
        return Component.text(msg, NamedTextColor.RED);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            // Context-sensitive: only offer subcommands that can actually succeed right now.
            var teamOpt = teamService.teamOf(player.getUniqueId());
            List<String> subs = new ArrayList<>(List.of("gui", "list"));
            if (teamOpt.isEmpty()) {
                subs.addAll(List.of("create", "join", "decline"));
            } else {
                subs.addAll(List.of("info", "leave"));
                if (teamOpt.get().isOwner(player.getUniqueId())) {
                    subs.addAll(List.of("invite", "kick", "public", "side",
                            "autosplit", "clearsides", "teamcount", "start", "disband"));
                }
            }
            return filter(args[0], subs);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("join")) {
            List<String> names = new ArrayList<>();
            teamService.publicTeams().forEach(t -> names.add(t.name()));
            return filter(args[1], names);
        }
        if (args.length == 2 && sub.equals("invite")) {
            // Only players who could actually accept: online, not the sender, not already in a team.
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())
                        && teamService.teamOf(online.getUniqueId()).isEmpty()) {
                    names.add(online.getName());
                }
            }
            return filter(args[1], names);
        }
        if (args.length == 2 && sub.equals("kick")) {
            // Only your own team's members (excluding yourself, the owner).
            List<String> names = new ArrayList<>();
            teamService.teamOf(player.getUniqueId()).ifPresent(t ->
                    t.members().forEach(u -> {
                        if (!u.equals(player.getUniqueId())) {
                            Player p = Bukkit.getPlayer(u);
                            if (p != null) names.add(p.getName());
                        }
                    }));
            return filter(args[1], names);
        }
        if (args.length == 2 && sub.equals("create")) {
            return filter(args[1], List.of("public", "private"));
        }
        if (args.length == 2 && sub.equals("start")) {
            return filter(args[1], kitService.enabled().stream().map(k -> k.name()).toList());
        }
        if (args.length == 2 && sub.equals("teamcount")) {
            int max = teamService.maxTeamsFor(player.getUniqueId());
            List<String> counts = new ArrayList<>();
            for (int i = 2; i <= max; i++) {
                counts.add(String.valueOf(i));
            }
            return filter(args[1], counts);
        }
        if (args.length == 3 && sub.equals("side")) {
            List<String> colors = new ArrayList<>();
            teamService.teamOf(player.getUniqueId()).ifPresent(t ->
                    t.activeColors().forEach(c -> colors.add(c.name().toLowerCase(Locale.ROOT))));
            if (colors.isEmpty()) {
                colors.addAll(List.of("red", "blue"));
            }
            return filter(args[2], colors);
        }
        if (args.length == 2 && (sub.equals("side"))) {
            List<String> names = new ArrayList<>();
            teamService.teamOf(player.getUniqueId()).ifPresent(t ->
                    t.members().forEach(u -> {
                        Player p = Bukkit.getPlayer(u);
                        if (p != null) names.add(p.getName());
                    }));
            return filter(args[1], names);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> candidates) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
