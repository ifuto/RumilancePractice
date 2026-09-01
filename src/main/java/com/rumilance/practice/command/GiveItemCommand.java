package com.rumilance.practice.command;

import com.rumilance.practice.item.GoldenHeadItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Operator-only utility command that hands out special items (golden heads, etc.) directly to
 * the sender. Designed for setup/testing, not for regular gameplay: every sub-command requires
 * {@code rumilance.admin} (default op), and each item is added in the main hand or dropped into
 * the inventory if the hand is occupied.
 *
 * <p>Usage: {@code /giveitem <ghead>}</p>
 */
public final class GiveItemCommand implements CommandExecutor, TabCompleter {

    private final Map<String, Supplier<ItemStack>> items = new HashMap<>();

    public GiveItemCommand() {
        items.put("ghead", GoldenHeadItems::create);
        items.put("goldenhead", GoldenHeadItems::create);
        items.put("save-sign", com.rumilance.practice.item.SaveSignItem::create);
        items.put("savesign", com.rumilance.practice.item.SaveSignItem::create);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /giveitem <ghead>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Available: " + String.join(", ", items.keySet()),
                    NamedTextColor.GRAY));
            return true;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        Supplier<ItemStack> supplier = items.get(key);
        if (supplier == null) {
            player.sendMessage(Component.text("Unknown item '" + args[0] + "'.", NamedTextColor.RED));
            player.sendMessage(Component.text("Available: " + String.join(", ", items.keySet()),
                    NamedTextColor.GRAY));
            return true;
        }
        ItemStack item = supplier.get();
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(extra ->
                player.getWorld().dropItemNaturally(player.getLocation(), extra));
        player.sendMessage(Component.text("Gave you " + prettyName(key) + ".", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return items.keySet().stream()
                    .filter(k -> k.startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private static String prettyName(String key) {
        return switch (key) {
            case "ghead", "goldenhead" -> "a Golden Head";
            default -> key;
        };
    }
}
