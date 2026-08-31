package com.rumilance.practice.command;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.model.PlayerData;
import com.rumilance.practice.punishment.ChatBanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Administrator commands for issuing and removing chat bans. Supports human-friendly durations
 * ({@code 30m}, {@code 6h}, {@code 7d}) and tab-completes online player names plus the duration
 * shortcuts.
 */
public final class ChatBanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> DURATIONS = List.of("5m", "30m", "1h", "6h", "12h", "1d", "3d", "7d", "30d");

    private final ChatBanService chatBanService;
    private final PlayerRepository playerRepository;

    public ChatBanCommand(ChatBanService chatBanService) {
        this(chatBanService, null);
    }

    public ChatBanCommand(ChatBanService chatBanService, PlayerRepository playerRepository) {
        this.chatBanService = chatBanService;
        this.playerRepository = playerRepository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("chatunban")) {
            if (args.length < 1) {
                sender.sendMessage(Component.text("/chatunban <player|uuid>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            UUID id;
            try {
                id = target != null ? target.getUniqueId() : UUID.fromString(args[0]);
            } catch (Exception e) {
                sender.sendMessage(Component.text("Invalid target (use a player name or UUID).", NamedTextColor.RED));
                return true;
            }
            chatBanService.unban(id);
            sender.sendMessage(Component.text("ChatBan removed.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("/chatban <player> <duration> <reason>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Durations: e.g. 30m, 6h, 7d (default 7d if invalid)", NamedTextColor.GRAY));
            return true;
        }
        Duration duration = parseDuration(args[1]);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            sender.sendMessage(Component.text("Invalid duration. Use e.g. 30m, 6h, 7d.", NamedTextColor.RED));
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        UUID staff = sender instanceof Player p ? p.getUniqueId() : null;

        // Resolve the target: online player first, otherwise an offline player by name (Paper's
        // non-blocking offline-lookup) or an explicit UUID. Offline bans are persisted and the
        // target is notified the next time they join.
        Player online = Bukkit.getPlayerExact(args[0]);
        UUID targetId;
        String targetName;
        if (online != null) {
            targetId = online.getUniqueId();
            targetName = online.getName();
        } else {
            UUID parsed = parseUuid(args[0]);
            if (parsed != null) {
                targetId = parsed;
                targetName = args[0];
            } else {
                // Resolve offline names against our own player table (most reliable for players who
                // have been on the server), then Bukkit's offline cache.
                UUID byName = null;
                if (playerRepository != null) {
                    try {
                        byName = playerRepository.findByUsername(args[0])
                                .map(PlayerData::uuid).orElse(null);
                    } catch (Exception ignored) {
                    }
                }
                if (byName == null) {
                    org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (off != null && (off.hasPlayedBefore() || off.isOnline())) {
                        byName = off.getUniqueId();
                    }
                }
                if (byName == null) {
                    sender.sendMessage(Component.text(
                            "Player not found (use an online name, a name seen before, or a UUID for offline bans).",
                            NamedTextColor.RED));
                    return true;
                }
                targetId = byName;
                targetName = args[0];
            }
        }

        chatBanService.issueWithNotice(targetId, staff, "CHATBAN", reason, duration);
        boolean wasOnline = online != null;
        sender.sendMessage(Component.text("ChatBan issued to " + targetName + " for " + args[1]
                + (wasOnline ? " (notified online)." : " (offline: will be notified on next join)."),
                NamedTextColor.GREEN));
        return true;
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("chatunban")) {
            if (args.length == 1) {
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
                return TabCompletions.filter(TabCompletions.current(args), names);
            }
            return List.of();
        }
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return TabCompletions.filter(TabCompletions.current(args), names);
        }
        if (args.length == 2) {
            return TabCompletions.filter(TabCompletions.current(args), DURATIONS);
        }
        if (args.length == 3) {
            return TabCompletions.filter(TabCompletions.current(args),
                    List.of("Spam", "Advertising", "Toxicity", "Insults", "Other"));
        }
        return List.of();
    }

    private static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            if (lower.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            if (lower.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
            if (lower.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(lower.substring(0, lower.length() - 1)));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }
}
