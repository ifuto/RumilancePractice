package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.KillEffectGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the paid kill-effect picker ({@code /killeffect}, alias {@code /killeffects}).
 * The GUI itself enforces the VIP+ gate; "None" remains available to everyone so effects can be
 * turned off.
 */
public final class KillEffectCommand implements CommandExecutor {

    private final KillEffectGui gui;

    public KillEffectCommand(KillEffectGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        gui.open(player);
        return true;
    }
}
