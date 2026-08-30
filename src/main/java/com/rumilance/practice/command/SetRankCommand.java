package com.rumilance.practice.command;

import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.rank.PlayerRank;
import com.rumilance.practice.rank.RankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * OP-only {@code /setrank <player> <norm|VIP|VIP+|admin|owner>} (alias: {@code /rank}).
 */
public final class SetRankCommand implements CommandExecutor, TabCompleter {

    private final RankService rankService;
    private final PlayerRepository playerRepository;

    public SetRankCommand(RankService rankService, PlayerRepository playerRepository) {
        this.rankService = rankService;
        this.playerRepository = playerRepository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("OP only.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /rank <player> <norm|VIP|VIP+|admin|owner> [duration e.g. 30d]", NamedTextColor.YELLOW));
            return true;
        }
        PlayerRank rank = PlayerRank.parse(args[1]);
        if (rank == null) {
            sender.sendMessage(Component.text(
                    "Unknown rank. Use: norm, VIP, VIP+, admin, owner", NamedTextColor.RED));
            return true;
        }
        java.time.Duration duration = null;
        String durationToken = null;
        if (args.length >= 3) {
            durationToken = args[2];
            duration = parseRankDuration(durationToken);
            if (duration == null) {
                sender.sendMessage(Component.text(
                        "Invalid duration. Use e.g. 30m, 12h, 7d, 2w, 3mo (or omit for permanent).",
                        NamedTextColor.RED));
                return true;
            }
        }
        UUID targetId = resolve(args[0]);
        if (targetId == null) {
            sender.sendMessage(Component.text("Unknown player: " + args[0], NamedTextColor.RED));
            return true;
        }
        rankService.setRank(targetId, rank, duration);
        String labelName = rank.displayLabel();
        Player online = Bukkit.getPlayer(targetId);
        String name = online != null ? online.getName() : args[0];
        sender.sendMessage(Component.text(
                "Set " + name + " rank to " + labelName
                        + (duration != null ? " for " + durationToken + "." : " (permanent)."),
                NamedTextColor.GREEN));
        if (online != null && !online.equals(sender)) {
            online.sendMessage(Component.text(
                    "Your rank is now " + labelName
                            + (duration != null ? " for " + durationToken + "." : "."),
                    NamedTextColor.AQUA));
        }
        return true;
    }

    /** Accepts {@code 30m}, {@code 12h}, {@code 7d}, {@code 2w}, {@code 3mo}. */
    private static java.time.Duration parseRankDuration(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (token.endsWith("mo")) {
                long n = Long.parseLong(token.substring(0, token.length() - 2));
                return n > 0 ? java.time.Duration.ofDays(n * 30L) : null;
            }
            char unit = token.charAt(token.length() - 1);
            long n = Long.parseLong(token.substring(0, token.length() - 1));
            if (n <= 0) {
                return null;
            }
            return switch (unit) {
                case 'm' -> java.time.Duration.ofMinutes(n);
                case 'h' -> java.time.Duration.ofHours(n);
                case 'd' -> java.time.Duration.ofDays(n);
                case 'w' -> java.time.Duration.ofDays(n * 7L);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return playerRepository.findByUsername(name).map(d -> d.uuid()).orElse(null);
        } catch (Exception ignored) {
            // fall through
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && offline.getUniqueId() != null) {
            return offline.getUniqueId();
        }
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(p.getName());
                }
            }
            return names;
        }
        if (args.length == 2) {
            return TabCompletions.filter(TabCompletions.current(args),
                    "norm", "VIP", "VIP+", "admin", "owner");
        }
        return List.of();
    }
}
