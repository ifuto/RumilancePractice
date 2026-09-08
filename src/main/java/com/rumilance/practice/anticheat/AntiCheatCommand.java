package com.rumilance.practice.anticheat;

import com.rumilance.practice.locale.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * {@code /anticheat} — mandate + attestation management for the client anti-cheat mod.
 * Operators (or console) only:
 * <ul>
 *   <li>{@code /anticheat require|unrequire <player>} — per-player mod mandate.</li>
 *   <li>{@code /anticheat status <player>} — verified/version/brand/findings/jar state.</li>
 *   <li>{@code /anticheat list} — mandated players.</li>
 *   <li>{@code /anticheat trust|untrust <sha256>} — pin/remove accepted mod builds;
 *       once any pin exists, required users must present a pinned jar hash
 *       ({@code /anticheat trustlist} shows pins). Get the hash from the CI build or run
 *       {@code sha256sum rumilance-ac-*.jar} locally.</li>
 * </ul>
 */
public final class AntiCheatCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "require", "unrequire", "status", "list", "trust", "untrust", "trustlist");

    private final AntiCheatService service;
    private final MessageService messageService;

    public AntiCheatCommand(AntiCheatService service, MessageService messageService) {
        this.service = service;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(messageService.render(senderOrNull(sender), "anticheat.no-permission"));
            return true;
        }
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "require", "unrequire" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return true;
                }
                applyMandate(sender, args[0].equalsIgnoreCase("require"),
                        Bukkit.getOfflinePlayer(args[1]));
                return true;
            }
            case "status" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return true;
                }
                showStatus(sender, Bukkit.getOfflinePlayer(args[1]));
                return true;
            }
            case "list" -> {
                showList(sender);
                return true;
            }
            case "trust", "untrust" -> {
                if (args.length < 2 || !args[1].matches("[0-9a-fA-F]{64}")) {
                    sender.sendMessage(messageService.render(senderOrNull(sender),
                            "anticheat.trust-invalid"));
                    return true;
                }
                boolean add = args[0].equalsIgnoreCase("trust");
                boolean changed = add ? service.trust(args[1]) : service.untrust(args[1]);
                sender.sendMessage(messageService.render(senderOrNull(sender),
                        add ? "anticheat.trust-set" : "anticheat.trust-unset",
                        MessageService.tags("sha", args[1].substring(0, 16),
                                "n", String.valueOf(service.trustedHashes().size()))));
                if (!changed) {
                    sender.sendMessage(messageService.render(senderOrNull(sender),
                            "anticheat.trust-note-unchanged"));
                }
                return true;
            }
            case "trustlist" -> {
                showTrustList(sender);
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void applyMandate(CommandSender sender, boolean make, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        if (make) {
            service.require(id);
        } else {
            service.unrequire(id);
        }
        sender.sendMessage(messageService.render(senderOrNull(sender),
                make ? "anticheat.required-set" : "anticheat.required-unset",
                MessageService.tags("target", String.valueOf(target.getName()))));
    }

    private void showStatus(CommandSender sender, OfflinePlayer target) {
        Player viewer = senderOrNull(sender);
        String required = service.isRequired(target.getUniqueId()) ? "yes" : "no";
        String[] tokens = service.statusTokens(target.getUniqueId());
        sender.sendMessage(messageService.render(viewer, "anticheat.status-header",
                MessageService.tags("target", String.valueOf(target.getName()))));
        sender.sendMessage(messageService.render(viewer, "anticheat.status-line",
                MessageService.tags(
                        "required", required,
                        "verified", tokens[0],
                        "version", tokens[1],
                        "flags", tokens[2],
                        "mods", tokens[3],
                        "brand", tokens[4],
                        "jar", tokens[5],
                        "trusted", tokens[6])));
    }

    private void showList(CommandSender sender) {
        Player viewer = senderOrNull(sender);
        var required = service.requiredPlayers();
        if (required.isEmpty()) {
            sender.sendMessage(messageService.render(viewer, "anticheat.list-empty"));
            return;
        }
        sender.sendMessage(messageService.render(viewer, "anticheat.list-header",
                MessageService.tags("n", String.valueOf(required.size()))));
        List<String> names = new ArrayList<>();
        for (UUID id : required) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
            names.add(offline.getName() != null ? offline.getName() : id.toString());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            sender.sendMessage(messageService.render(viewer, "anticheat.list-line",
                    MessageService.tags("target", name)));
        }
    }

    private void showTrustList(CommandSender sender) {
        Player viewer = senderOrNull(sender);
        var pins = service.trustedHashes();
        if (pins.isEmpty()) {
            sender.sendMessage(messageService.render(viewer, "anticheat.trust-list-empty"));
            return;
        }
        sender.sendMessage(messageService.render(viewer, "anticheat.trust-list-header",
                MessageService.tags("n", String.valueOf(pins.size()))));
        List<String> sorted = new ArrayList<>(pins);
        sorted.sort(String::compareTo);
        for (String pin : sorted) {
            sender.sendMessage(messageService.render(viewer, "anticheat.trust-list-line",
                    MessageService.tags("sha", pin)));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(messageService.render(senderOrNull(sender), "anticheat.usage"));
    }

    private static Player senderOrNull(CommandSender sender) {
        return sender instanceof Player p ? p : null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2
                && List.of("require", "unrequire", "status").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
