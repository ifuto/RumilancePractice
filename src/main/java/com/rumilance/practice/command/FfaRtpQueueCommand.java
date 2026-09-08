package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaRtpQueueService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /rtpqueue [join|leave]} — random-teleport duel queue for the FFA arena you are in
 * (enabled per arena via the FFA settings GUI).
 */
public final class FfaRtpQueueCommand implements CommandExecutor, TabCompleter {

    private final FfaRtpQueueService service;

    public FfaRtpQueueCommand(FfaRtpQueueService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> service.join(player);
            case "leave" -> service.leave(player);
            default -> service.toggle(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("join", "leave").stream()
                    .filter(c -> c.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
