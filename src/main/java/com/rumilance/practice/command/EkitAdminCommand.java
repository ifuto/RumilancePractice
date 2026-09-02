package com.rumilance.practice.command;

import com.rumilance.practice.gui.menus.EkitAdminGui;
import com.rumilance.practice.originalkit.OriginalKitRoomService;
import com.rumilance.practice.admin.AdminTools;
import com.rumilance.practice.util.Cuboid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class EkitAdminCommand implements CommandExecutor {

    private final EkitAdminGui gui;
    private final OriginalKitRoomService roomService;

    public EkitAdminCommand(EkitAdminGui gui, OriginalKitRoomService roomService) {
        this.gui = gui;
        this.roomService = roomService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!sender.hasPermission("rumilance.admin") && !sender.isOp()) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "spawn" -> {
                    roomService.setSpawn(player.getLocation());
                    player.sendMessage(Component.text("Original-kit room spawn set.", NamedTextColor.GREEN));
                    return true;
                }
                case "region" -> {
                    var p1 = AdminTools.pos1(player);
                    var p2 = AdminTools.pos2(player);
                    if (p1 == null || p2 == null) {
                        player.sendMessage(Component.text("Set both /admintool positions first.", NamedTextColor.RED));
                        return true;
                    }
                    roomService.setRegion(Cuboid.of(p1, p2));
                    player.sendMessage(Component.text("Original-kit room region set.", NamedTextColor.GREEN));
                    return true;
                }
                default -> {
                    // fall through to GUI
                }
            }
        }

        gui.openAdmin(player);
        return true;
    }
}
