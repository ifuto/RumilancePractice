package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaRtpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /rtp} — teleport to a random safe spot inside your current FFA arena (enabled per
 * arena via the FFA settings GUI, default OFF). Combat-aware: refused while fighting.
 */
public final class FfaRtpCommand implements CommandExecutor {

    private final FfaRtpService service;

    public FfaRtpCommand(FfaRtpService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        service.rtp(player);
        return true;
    }
}
