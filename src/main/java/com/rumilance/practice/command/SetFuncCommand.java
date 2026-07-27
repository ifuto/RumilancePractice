package com.rumilance.practice.command;

import com.rumilance.practice.item.FunctionalItemListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class SetFuncCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /setfunc <ranked|unranked|ffa|ekit|settings|spectate|leavequeue>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String type = args[0].toLowerCase(Locale.ROOT);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            hand = new ItemStack(Material.NETHER_STAR);
        }
        Component name = switch (type) {
            case "ranked" -> FunctionalItemListener.rankedName();
            case "unranked" -> FunctionalItemListener.unrankedName();
            case "ffa" -> Component.text("🔥 FFA 🔥", NamedTextColor.GREEN);
            case "ekit" -> Component.text("🪓 Edit Kit 🪓", NamedTextColor.BLUE);
            case "settings" -> Component.text("⚙️ Settings ⚙️", NamedTextColor.YELLOW);
            case "spectate" -> Component.text("👀 Spectate 👀", NamedTextColor.LIGHT_PURPLE);
            case "leavequeue" -> Component.text("Leave Queue", NamedTextColor.RED);
            default -> null;
        };
        if (name == null) {
            player.sendMessage(Component.text("Unknown function type.", NamedTextColor.RED));
            return true;
        }
        ItemStack created = FunctionalItemListener.create(type, hand.getType(), name);
        created.setAmount(Math.max(1, hand.getAmount()));
        player.getInventory().setItemInMainHand(created);
        player.sendMessage(Component.text("Set functional item: " + type, NamedTextColor.GREEN));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("ranked", "unranked", "ffa", "ekit", "settings", "spectate", "leavequeue");
        }
        return List.of();
    }
}
