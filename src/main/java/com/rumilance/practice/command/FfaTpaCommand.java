package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaTpaService;
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

/**
 * {@code /tpa <player>}, {@code /tpahere <player>}, {@code /tpaccept}, {@code /tpadeny} —
 * FFA in-arena teleport requests (enabled per arena via the FFA settings GUI).
 */
public final class FfaTpaCommand implements CommandExecutor, TabCompleter {

    private final FfaTpaService service;

    public FfaTpaCommand(FfaTpaService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "tpaccept" -> {
                service.sendAccept(player);
                return true;
            }
            case "tpadeny" -> {
                service.deny(player);
                return true;
            }
            default -> { }
        }
        boolean here = cmd.equals("tpahere");
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /" + cmd + " <player>", NamedTextColor.YELLOW));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            service.sendResult(player, null, here, FfaTpaService.SendResult.TARGET_OFFLINE);
            return true;
        }
        service.sendRequest(player, target, here);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) {
            return out;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        return out;
    }
}
