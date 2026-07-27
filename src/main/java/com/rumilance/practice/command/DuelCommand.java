package com.rumilance.practice.command;

import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.gui.menus.DuelRequestGui;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.queue.QueueCoordinator;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaTerrain;
import com.rumilance.practice.state.MatchMode;
import com.rumilance.practice.state.PlayerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles /duel, /unranked, /accept, /deny and related aliases.
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final QueueKitGui rankedGui;
    private final QueueKitGui unrankedGui;
    private final DuelRequestService duelRequestService;
    private final MatchService matchService;
    private final KitService kitService;
    private final PlayerStateManager stateManager;
    private final SoundService soundService;
    private final LobbyService lobbyService;
    private final QueueCoordinator queueCoordinator;
    private final RuntimeFlags runtimeFlags;
    private final boolean rankedDefault;
    private DuelRequestGui duelRequestGui;

    public DuelCommand(
            QueueKitGui rankedGui,
            QueueKitGui unrankedGui,
            DuelRequestService duelRequestService,
            MatchService matchService,
            KitService kitService,
            PlayerStateManager stateManager,
            SoundService soundService,
            LobbyService lobbyService,
            QueueCoordinator queueCoordinator,
            RuntimeFlags runtimeFlags,
            boolean rankedDefault
    ) {
        this.rankedGui = rankedGui;
        this.unrankedGui = unrankedGui;
        this.duelRequestService = duelRequestService;
        this.matchService = matchService;
        this.kitService = kitService;
        this.stateManager = stateManager;
        this.soundService = soundService;
        this.lobbyService = lobbyService;
        this.queueCoordinator = queueCoordinator;
        this.runtimeFlags = runtimeFlags;
        this.rankedDefault = rankedDefault;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rumilance.user") && !player.hasPermission("rumilance.duel.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("Practice is in maintenance mode.", NamedTextColor.RED));
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        boolean ranked = rankedDefault;
        if (name.equals("unranked") || name.equals("unrankduel") || name.equals("unduel") || name.equals("ud")) {
            ranked = false;
        }

        if (args.length == 0) {
            if (ranked) {
                rankedGui.open(player);
            } else {
                unrankedGui.open(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "cancel" -> {
                duelRequestService.latestOutgoing(player.getUniqueId()).ifPresentOrElse(req -> {
                    duelRequestService.cancel(req.id());
                    soundService.play(player, "queue-leave");
                    player.sendMessage(Component.text("Duel request cancelled.", NamedTextColor.YELLOW));
                }, () -> player.sendMessage(Component.text("No pending request.", NamedTextColor.RED)));
                yield true;
            }
            case "accept" -> {
                handleAccept(player, args.length > 1 ? args[1] : null);
                yield true;
            }
            case "deny" -> {
                handleDeny(player, args.length > 1 ? args[1] : null);
                yield true;
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    yield true;
                }
                if (duelRequestGui != null) {
                    duelRequestGui.openFor(player, target, ranked);
                } else {
                    sendRequest(player, target, ranked, args.length > 1 ? args[1] : firstKit(), ArenaTerrain.ANY, 1);
                }
                yield true;
            }
        };
    }

    public void handleAccept(Player player, String fromName) {
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN || state == PlayerState.PREPARING_MATCH) {
            player.sendMessage(Component.text("You cannot accept while in a match.", NamedTextColor.RED));
            return;
        }
        DuelRequestService.RichDuelRequest request;
        if (fromName == null) {
            request = duelRequestService.latestForTarget(player.getUniqueId()).orElse(null);
        } else {
            Player from = Bukkit.getPlayerExact(fromName);
            if (from == null) {
                player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            request = duelRequestService.latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).orElse(null);
        }
        if (request == null || request.isExpired(java.time.Instant.now())) {
            player.sendMessage(Component.text("No valid duel request.", NamedTextColor.RED));
            return;
        }
        if (!duelRequestService.accept(request.id())) {
            player.sendMessage(Component.text("Request expired.", NamedTextColor.RED));
            return;
        }
        matchService.startDuel(request.sender(), request.target(), request.kitName(),
                request.ranked() ? MatchMode.RANKED : MatchMode.UNRANKED,
                request.terrain(), request.bestOf());
    }

    public void handleDeny(Player player, String fromName) {
        if (fromName != null && fromName.equalsIgnoreCase("all")) {
            duelRequestService.denyAll(player.getUniqueId());
            player.sendMessage(Component.text("Denied all requests.", NamedTextColor.YELLOW));
            return;
        }
        if (fromName == null) {
            duelRequestService.latestForTarget(player.getUniqueId()).ifPresentOrElse(req -> {
                duelRequestService.cancel(req.id());
                player.sendMessage(Component.text("Denied duel request.", NamedTextColor.YELLOW));
            }, () -> player.sendMessage(Component.text("No request to deny.", NamedTextColor.RED)));
            return;
        }
        Player from = Bukkit.getPlayerExact(fromName);
        if (from == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return;
        }
        duelRequestService.latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).ifPresentOrElse(req -> {
            duelRequestService.cancel(req.id());
            player.sendMessage(Component.text("Denied duel from " + from.getName() + ".", NamedTextColor.YELLOW));
        }, () -> player.sendMessage(Component.text("No request from that player.", NamedTextColor.RED)));
    }

    private void sendRequest(Player sender, Player target, boolean ranked, String kit,
                             ArenaTerrain terrain, int bestOf) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text("You cannot duel yourself.", NamedTextColor.RED));
            return;
        }
        PlayerState targetState = stateManager.getState(target.getUniqueId());
        if (targetState == PlayerState.FIGHTING || targetState == PlayerState.COUNTDOWN) {
            sender.sendMessage(Component.text("That player is in a match.", NamedTextColor.RED));
            return;
        }
        duelRequestService.create(sender.getUniqueId(), target.getUniqueId(), kit, ranked, terrain, bestOf)
                .ifPresentOrElse(req -> {
                    soundService.play(sender, "duel-request-sent");
                    soundService.play(target, "duel-request-received");
                    sender.sendMessage(Component.text("Sent " + (ranked ? "ranked" : "unranked")
                            + " duel (" + kit + ") to " + target.getName() + ".", NamedTextColor.GREEN)
                            .append(Component.newline())
                            .append(Component.text("[CANCEL]", NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/duel cancel"))));
                    target.sendMessage(Component.text(sender.getName() + " challenged you to "
                                    + (ranked ? "ranked" : "unranked") + " (" + kit + ").", NamedTextColor.GOLD)
                            .append(Component.newline())
                            .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/accept " + sender.getName())))
                            .append(Component.space())
                            .append(Component.text("[DENY]", NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/deny " + sender.getName()))));
                }, () -> sender.sendMessage(Component.text("Could not send request (rate limit?).", NamedTextColor.RED)));
    }

    private String firstKit() {
        return kitService.enabled().stream().findFirst().map(k -> k.name()).orElse("nodebuff");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("cancel");
            out.add("accept");
            out.add("deny");
            for (Player online : Bukkit.getOnlinePlayers()) {
                out.add(online.getName());
            }
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
