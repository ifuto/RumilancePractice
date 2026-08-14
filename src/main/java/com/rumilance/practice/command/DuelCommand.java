package com.rumilance.practice.command;

import com.rumilance.practice.config.RuntimeFlags;
import com.rumilance.practice.duel.DuelRequestService;
import com.rumilance.practice.gui.menus.QueueKitGui;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.lobby.LobbyService;
import com.rumilance.practice.locale.MessageService;
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
    private final MessageService messageService;

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
            boolean rankedDefault,
            MessageService messageService
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
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player-only");
            return true;
        }
        if (!player.hasPermission("rumilance.user") && !player.hasPermission("rumilance.duel.use")) {
            messageService.send(player, "general.no-permission");
            return true;
        }
        if (runtimeFlags.maintenance() && !player.hasPermission("rumilance.admin")) {
            messageService.send(player, "general.maintenance");
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
                    messageService.send(player, "duel.request-cancelled",
                            MessageService.tags("target", senderName(req.target())));
                }, () -> messageService.send(player, "duel.no-pending-request"));
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
                    messageService.send(player, "general.player-not-found",
                            MessageService.tags("target", args[0]));
                    yield true;
                }
                // /duel <player> [kit] sends the request immediately (no GUI).
                String kit = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : firstKit();
                if (kitService.get(kit).filter(k -> k.enabled()).isEmpty()) {
                    messageService.send(player, "kit.not-found", MessageService.tags("kit", args[1]));
                    yield true;
                }
                sendRequest(player, target, ranked, kit, ArenaTerrain.ANY, 1);
                yield true;
            }
        };
    }

    public void handleAccept(Player player, String fromName) {
        PlayerState state = stateManager.getState(player.getUniqueId());
        if (state == PlayerState.FIGHTING || state == PlayerState.COUNTDOWN || state == PlayerState.PREPARING_MATCH) {
            messageService.send(player, "duel.already-in-match");
            return;
        }
        DuelRequestService.RichDuelRequest request;
        if (fromName == null) {
            request = duelRequestService.latestForTarget(player.getUniqueId()).orElse(null);
        } else {
            Player from = Bukkit.getPlayerExact(fromName);
            if (from == null) {
                messageService.send(player, "general.player-not-found", MessageService.tags("target", fromName));
                return;
            }
            request = duelRequestService.latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).orElse(null);
        }
        if (request == null || request.isExpired(java.time.Instant.now())) {
            messageService.send(player, "duel.no-pending-request");
            return;
        }
        if (!duelRequestService.accept(request.id())) {
            messageService.send(player, "duel.request-expired",
                    MessageService.tags("target", senderName(request.sender())));
            return;
        }
        matchService.startDuel(request.sender(), request.target(), request.kitName(),
                request.ranked() ? MatchMode.RANKED : MatchMode.UNRANKED,
                request.terrain(), request.bestOf());
    }

    public void handleDeny(Player player, String fromName) {
        if (fromName != null && fromName.equalsIgnoreCase("all")) {
            duelRequestService.denyAll(player.getUniqueId());
            messageService.send(player, "duel.deny-all");
            return;
        }
        if (fromName == null) {
            duelRequestService.latestForTarget(player.getUniqueId()).ifPresentOrElse(req -> {
                duelRequestService.cancel(req.id());
                messageService.send(player, "duel.deny-own", MessageService.tags("target", senderName(req.sender())));
            }, () -> messageService.send(player, "duel.no-request-to-deny"));
            return;
        }
        Player from = Bukkit.getPlayerExact(fromName);
        if (from == null) {
            messageService.send(player, "general.player-not-found", MessageService.tags("target", fromName));
            return;
        }
        duelRequestService.latestFromSenderToTarget(from.getUniqueId(), player.getUniqueId()).ifPresentOrElse(req -> {
            duelRequestService.cancel(req.id());
            messageService.send(player, "duel.deny-own", MessageService.tags("target", from.getName()));
        }, () -> messageService.send(player, "duel.no-pending-request"));
    }

    private String senderName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        return online != null ? online.getName() : "?";
    }

    private void sendRequest(Player sender, Player target, boolean ranked, String kit,
                             ArenaTerrain terrain, int bestOf) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            messageService.send(sender, "duel.cannot-duel-self");
            return;
        }
        PlayerState targetState = stateManager.getState(target.getUniqueId());
        if (targetState == PlayerState.FIGHTING || targetState == PlayerState.COUNTDOWN) {
            messageService.send(sender, "duel.target-already-in-match",
                    MessageService.tags("target", target.getName()));
            return;
        }
        duelRequestService.create(sender.getUniqueId(), target.getUniqueId(), kit, ranked, terrain, bestOf)
                .ifPresentOrElse(req -> {
                    soundService.play(sender, "duel-request-sent");
                    soundService.play(target, "duel-request-received");
                    String modeWord = messageService.modeWord(sender, ranked);
                    String senderLocale = messageService.resolveLocale(sender);
                    String targetLocale = messageService.resolveLocale(target);
                    sender.sendMessage(messageService.render(senderLocale, "duel.request-sent",
                                    MessageService.tags("mode", modeWord, "kit", kit, "target", target.getName()))
                            .append(Component.newline())
                            .append(Component.text("[CANCEL]", NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/duel cancel"))));
                    target.sendMessage(messageService.render(targetLocale, "duel.request-received",
                                    MessageService.tags("mode", messageService.modeWord(target, ranked),
                                            "kit", kit, "sender", sender.getName()))
                            .append(Component.newline())
                            .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/accept " + sender.getName())))
                            .append(Component.space())
                            .append(Component.text("[DENY]", NamedTextColor.RED)
                                    .decorate(TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/deny " + sender.getName()))));
                }, () -> messageService.send(sender, "duel.could-not-send"));
    }

    private String firstKit() {
        return kitService.enabled().stream().findFirst().map(k -> k.name()).orElse("nodebuff");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        String current = TabCompletions.current(args);
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("cancel");
            out.add("accept");
            out.add("deny");
            // Prioritise players who have sent (or received) a pending request, then the rest of the server.
            duelRequestService.incoming(player.getUniqueId()).forEach(req -> {
                Player p = Bukkit.getPlayer(req.sender());
                if (p != null) {
                    out.add(p.getName());
                }
            });
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) {
                    out.add(online.getName());
                }
            }
            return TabCompletions.limit(TabCompletions.filter(current, out), 50);
        }
        if (args.length == 2) {
            // Only complete kit names when the first arg is a real player name, not a subcommand.
            String first = args[0].toLowerCase(Locale.ROOT);
            if (!first.equals("cancel") && !first.equals("accept") && !first.equals("deny")
                    && Bukkit.getPlayerExact(args[0]) != null) {
                return TabCompletions.filter(current,
                        kitService.enabled().stream().map(k -> k.name()).toList());
            }
            return List.of();
        }
        return List.of();
    }
}
