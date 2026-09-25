package com.rumilance.practice.tournament;

import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.team.TeamService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * {@code /tournament} — party color-team tournament. The party owner starts a
 * single-elimination bracket over the party's assigned color sides; everyone else in the
 * party participates on their side or spectates. Kept to five subcommands on purpose:
 * start, join, leave, info, cancel.
 */
public final class TournamentCommand implements CommandExecutor, TabCompleter {

    private final TournamentService tournamentService;
    private final TeamService teamService;
    private final KitService kitService;
    private com.rumilance.practice.gui.menus.TournamentGui tournamentGui;

    public TournamentCommand(TournamentService tournamentService, TeamService teamService,
                             KitService kitService) {
        this.tournamentService = tournamentService;
        this.teamService = teamService;
        this.kitService = kitService;
    }

    /** Opens the setup screen; {@code /tournament start} becomes the GUI entry point. */
    public void setTournamentGui(com.rumilance.practice.gui.menus.TournamentGui tournamentGui) {
        this.tournamentGui = tournamentGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can run a tournament.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            start(player, args);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> start(player, args);
            case "info", "bracket" -> info(player);
            case "cancel" -> tournamentService.cancel(player);
            case "join", "leave" -> spectatorJoinLeave(player, args[0]);
            default -> printUsage(player);
        }
        return true;
    }

    private void start(Player player, String[] args) {
        var teamOpt = teamService.teamOf(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED));
            return;
        }
        if (!teamOpt.get().isOwner(player.getUniqueId())) {
            player.sendMessage(Component.text("Only the team owner can start a tournament.",
                    NamedTextColor.RED));
            return;
        }
        if (tournamentGui == null) {
            player.sendMessage(Component.text("Tournament GUI unavailable.",
                    NamedTextColor.RED));
            return;
        }
        // Opening an inventory from inside a command handler is safe; the GUI dedupes state
        // and drops the player back on the hub on Close.
        tournamentGui.open(player);
    }

    private void info(Player player) {
        var teamOpt = teamService.teamOf(player.getUniqueId());
        if (teamOpt.isEmpty()) {
            player.sendMessage(Component.text("You are not in a team.", NamedTextColor.RED));
            return;
        }
        tournamentService.info(player, teamOpt.get());
    }

    /** {@code join}/{@code leave} are hints — waiters auto-spectate in sequential mode. */
    private void spectatorJoinLeave(Player player, String sub) {
        player.sendMessage(Component.text(
                "Spectating is automatic: members without a color side (and, in sequential "
                        + "mode, everyone waiting) watch for themselves. No " + sub + " needed.",
                NamedTextColor.GRAY));
    }

    private void printUsage(Player player) {
        player.sendMessage(Component.text("Tournament commands:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/tournament  |  /tournament start  — open the setup screen",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("/tournament info  |  /tournament cancel",
                NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        var teamOpt = teamService.teamOf(player.getUniqueId());
        boolean owner = teamOpt.isPresent() && teamOpt.get().isOwner(player.getUniqueId());
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info"));
            if (owner) {
                subs.add("start");
                subs.add("cancel");
            }
            return filter(args[0], subs);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> candidates) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .sorted().toList();
    }
}
