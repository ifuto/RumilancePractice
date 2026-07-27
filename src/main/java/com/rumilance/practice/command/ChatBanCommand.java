package com.rumilance.practice.command;

import com.rumilance.practice.punishment.ChatBanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public final class ChatBanCommand implements CommandExecutor {

    private final ChatBanService chatBanService;

    public ChatBanCommand(ChatBanService chatBanService) {
        this.chatBanService = chatBanService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
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
                sender.sendMessage(Component.text("Invalid target.", NamedTextColor.RED));
                return true;
            }
            chatBanService.unban(id);
            sender.sendMessage(Component.text("Unban requested.", NamedTextColor.GREEN));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("/chatban <player> <duration> <reason>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        Duration duration = parseDuration(args[1]);
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        UUID staff = sender instanceof Player p ? p.getUniqueId() : null;
        chatBanService.issue(target.getUniqueId(), staff, "CHATBAN", reason, duration);
        sender.sendMessage(Component.text("ChatBan issued.", NamedTextColor.GREEN));
        return true;
    }

    private static Duration parseDuration(String raw) {
        raw = raw.toLowerCase(Locale.ROOT);
        if (raw.endsWith("d")) {
            return Duration.ofDays(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        if (raw.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        if (raw.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }
        return Duration.ofDays(7);
    }
}
