package com.rumilance.practice.command;

import com.rumilance.practice.ban.BanAnnounce;
import com.rumilance.practice.ban.BanDuration;
import com.rumilance.practice.ban.BanService;
import com.rumilance.practice.database.repository.PlayerRepository;
import com.rumilance.practice.gui.menus.BanListGui;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /ban}, {@code /kick}, {@code /banlist}, {@code /unban}. {@code /ban} in plugin.yml
 * wins over vanilla {@code /minecraft:ban}.
 */
public final class BanCommand implements CommandExecutor, TabCompleter {

    private final BanService banService;
    private final BanListGui banListGui;
    private final PlayerRepository playerRepository;

    public BanCommand(BanService banService, BanListGui banListGui, PlayerRepository playerRepository) {
        this.banService = banService;
        this.banListGui = banListGui;
        this.playerRepository = playerRepository;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "ban" -> handleBan(sender, args);
            case "kick" -> handleKick(sender, args);
            case "banlist" -> handleBanlist(sender);
            case "unban" -> handleUnban(sender, args);
            case "testban" -> handleTestBan(sender);
            case "testkick" -> handleTestKick(sender);
            default -> true;
        };
    }

    private boolean handleBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ban <player> <reason> [duration<d|w|mo>]", NamedTextColor.YELLOW));
            return true;
        }
        String targetName = args[0];
        UUID targetId = resolve(targetName);
        if (targetId == null) {
            sender.sendMessage(Component.text("Unknown player: " + targetName, NamedTextColor.RED));
            return true;
        }
        Duration duration = null;
        String durationToken = null;
        int reasonEnd = args.length;
        if (args.length >= 3 && BanDuration.looksLike(args[args.length - 1])) {
            durationToken = args[args.length - 1];
            reasonEnd = args.length - 1;
            if (!durationToken.equalsIgnoreCase("auto")) {
                duration = BanDuration.parse(durationToken).orElse(null);
            }
        }
        StringBuilder reason = new StringBuilder();
        for (int i = 1; i < reasonEnd; i++) {
            if (i > 1) {
                reason.append(' ');
            }
            reason.append(args[i]);
        }
        if (reason.isEmpty()) {
            sender.sendMessage(Component.text("Reason is required.", NamedTextColor.RED));
            return true;
        }
        String storedName = BanService.nameOf(targetId, targetName);
        if (durationToken != null && durationToken.equalsIgnoreCase("auto")) {
            int offense = banService.banCount(targetId) + 1;
            duration = BanDuration.forOffenseNumber(offense);
            durationToken = BanDuration.autoToken(offense);
        }
        banService.ban(targetId, storedName, reason.toString(), duration, durationToken, sender.getName());
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /kick <player> [reason]", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player is not online: " + args[0], NamedTextColor.RED));
            return true;
        }
        StringBuilder reason = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                reason.append(' ');
            }
            reason.append(args[i]);
        }
        banService.kick(target, sender.getName(), reason.toString());
        return true;
    }

    private boolean handleTestBan(CommandSender sender) {
        Bukkit.broadcast(BanAnnounce.ban("TestPlayer", "For test", "2 weeks"));
        sender.sendMessage(Component.text("Test ban broadcast sent (not stored).", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleTestKick(CommandSender sender) {
        Bukkit.broadcast(BanAnnounce.kick("TestPlayer"));
        sender.sendMessage(Component.text("Test kick broadcast sent (not stored).", NamedTextColor.GRAY));
        return true;
    }

    private boolean handleBanlist(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        banListGui.open(player);
        return true;
    }

    private boolean handleUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /unban <player>", NamedTextColor.YELLOW));
            return true;
        }
        UUID targetId = resolve(args[0]);
        if (targetId == null) {
            sender.sendMessage(Component.text("Unknown player: " + args[0], NamedTextColor.RED));
            return true;
        }
        if (banService.unban(targetId)) {
            sender.sendMessage(Component.text("Unbanned " + args[0] + ".", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(args[0] + " is not banned.", NamedTextColor.YELLOW));
        }
        return true;
    }

    private UUID resolve(String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // MCID path  Epersist and look up by UUID only.
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        if (playerRepository != null) {
            try {
                var row = playerRepository.findByUsername(name);
                if (row.isPresent()) {
                    return row.get().uuid();
                }
            } catch (Exception ignored) {
            }
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return cached.getUniqueId();
        }
        return banService.store().uuidByName(name);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        String current = TabCompletions.current(args);
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (name.equals("ban") || name.equals("kick") || name.equals("unban"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return TabCompletions.filter(current, names);
        }
        // Duration is optional and usually last; only hint when the token looks like a duration.
        if (name.equals("ban") && args.length >= 2
                && (current.isEmpty() || BanDuration.looksLike(current)
                || current.equalsIgnoreCase("a") || current.equalsIgnoreCase("au")
                || current.equalsIgnoreCase("aut") || current.toLowerCase(Locale.ROOT).startsWith("auto"))) {
            return TabCompletions.filter(current, "1d", "7d", "1w", "2w", "1mo", "2mo", "3mo", "auto");
        }
        return List.of();
    }
}
