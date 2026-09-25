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
            messageService.send(sender, "party.player-only");
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
                // /party creates a party (external fights + tournament); /team creates an
                // internal color-team group. The label routing keeps the two words honest.
                String lower = label == null ? "team" : label.toLowerCase(Locale.ROOT);
                boolean party = lower.equals("party") || lower.equals("p");
                String name = args.length > 2
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                        : player.getName() + (party ? "'s Party" : "'s Team");
                TeamService.Result r = teamService.create(player, name, isPublic,
                        party ? com.rumilance.practice.team.GroupKind.PARTY
                                : com.rumilance.practice.team.GroupKind.TEAM);
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
            case "queue" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /team queue <kit>", NamedTextColor.YELLOW));
                    return true;
                }
                String queueKit = args[1].toLowerCase(Locale.ROOT);
                if (kitService.get(queueKit).filter(k -> k.enabled()).isEmpty()) {
                    player.sendMessage(err(TeamService.Result.KIT_NOT_FOUND));
                    return true;
                }
                TeamService.Result qr = teamService.queueFight(player, queueKit);
                if (qr != TeamService.Result.OK) player.sendMessage(err(qr));
            }
            case "unqueue" -> {
                TeamService.Result ur = teamService.cancelQueueFight(player);
                if (ur != TeamService.Result.OK) player.sendMessage(err(ur));
            }
            case "duel" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /team duel <party> <kit>",
                            NamedTextColor.YELLOW));
                    return true;
                }
                String duelKit = args[2].toLowerCase(Locale.ROOT);
                if (kitService.get(duelKit).filter(k -> k.enabled()).isEmpty()) {
                    player.sendMessage(err(TeamService.Result.KIT_NOT_FOUND));
                    return true;
                }
                TeamService.Result dr = teamService.requestTeamDuel(player, args[1], duelKit);
                if (dr != TeamService.Result.OK) player.sendMessage(err(dr));
            }
            case "accept" -> {
                TeamService.Result ar = teamService.acceptTeamDuel(player);
                if (ar != TeamService.Result.OK) player.sendMessage(err(ar));
            }
            case "deny" -> {
                TeamService.Result dr = teamService.denyTeamDuel(player);
                if (dr != TeamService.Result.OK) player.sendMessage(err(dr));
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
            default -> sendHelp(player, label);
        }
        return true;
    }

    /**
     * 案内を2層に分ける。{@code /party} は「大きなひとかたまり」(作成・招待・参加・
     * Party 戦)、{@code /team} は「最小分解」(赤/青への色分けとパーティー内戦)。
     * 同じ実行部を共有しているので、どちらからでも全サブコマンドは動く。
     */
    private void sendHelp(Player p, String label) {
        String used = label == null || label.isBlank() ? "team" : label.toLowerCase(Locale.ROOT);
        boolean party = used.equals("party") || used.equals("p");
        if (party) {
            p.sendMessage(Component.text("Party commands (the whole group):", NamedTextColor.AQUA));
            p.sendMessage(Component.text("/party create [public] [name]", NamedTextColor.GRAY));
            p.sendMessage(Component.text("/party invite <player> | /party join <name|list>",
                    NamedTextColor.GRAY));
            p.sendMessage(Component.text("/party leave | /party kick <player> | /party disband",
                    NamedTextColor.GRAY));
            p.sendMessage(Component.text("/party queue <kit> | /party unqueue", NamedTextColor.GRAY));
            p.sendMessage(Component.text("/party duel <party> <kit> | /party accept | /party deny",
                    NamedTextColor.GRAY));
            p.sendMessage(Component.text("Red/blue split: /team side | /team autosplit",
                    NamedTextColor.DARK_GRAY));
            return;
        }
        p.sendMessage(Component.text("Team commands (red/blue split inside a party):",
                NamedTextColor.AQUA));
        p.sendMessage(Component.text("/team side <player> <red|blue>", NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team autosplit | /team clearsides | /team teamcount <n>",
                NamedTextColor.GRAY));
        p.sendMessage(Component.text("/team start <kit>", NamedTextColor.GRAY));
        p.sendMessage(Component.text("Party level: /party create | /party invite | /party queue",
                NamedTextColor.DARK_GRAY));
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
            case ALREADY_QUEUED -> "Already waiting in the team fight queue.";
            case NO_PENDING_DUEL -> "No pending team duel request.";
            case DUEL_SELF -> "You cannot challenge your own party.";
            case WRONG_KIND -> "That only works for the right group kind (team or party).";
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
                    subs.addAll(List.of("queue", "unqueue", "duel", "accept", "deny"));
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
