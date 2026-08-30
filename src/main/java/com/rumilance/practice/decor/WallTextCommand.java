package com.rumilance.practice.decor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /walltext} — admin tool for wall-mounted labels.
 * <ul>
 *   <li>{@code /walltext set <id> <text...>} — snap a label onto the block face in sight.
 *       Text is MiniMessage; plain text defaults to the aqua accent.</li>
 *   <li>{@code /walltext scale <id> <0.25-16> <text...>} — same, with explicit scale.</li>
 *   <li>{@code /walltext remove <id>} / {@code list} / {@code reload}</li>
 * </ul>
 */
public final class WallTextCommand implements CommandExecutor, TabCompleter {

    private static final float DEFAULT_SCALE = 2.0f;

    private final WallTextService service;

    public WallTextCommand(WallTextService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("/walltext set <id> <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                place(player, args[1], DEFAULT_SCALE,
                        String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
            }
            case "scale" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("/walltext scale <id> <0.25-16> <text...>", NamedTextColor.YELLOW));
                    return true;
                }
                float scale;
                try {
                    scale = Float.parseFloat(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Scale must be a number (e.g. 2.5).", NamedTextColor.RED));
                    return true;
                }
                place(player, args[1], scale,
                        String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("/walltext remove <id>", NamedTextColor.YELLOW));
                    return true;
                }
                boolean removed = service.remove(args[1]);
                player.sendMessage(removed
                        ? Component.text("Wall text '" + args[1] + "' removed.", NamedTextColor.GREEN)
                        : Component.text("No wall text with that id.", NamedTextColor.RED));
            }
            case "list" -> {
                var all = service.all();
                player.sendMessage(Component.text("Wall texts (" + all.size() + "):", NamedTextColor.AQUA));
                all.values().forEach(p -> player.sendMessage(Component.text(
                        "  " + p.id() + " @ " + p.location().getBlockX() + "," + p.location().getBlockY()
                                + "," + p.location().getBlockZ() + " (" + p.face() + ")",
                        NamedTextColor.GRAY)));
            }
            case "reload" -> {
                service.load();
                player.sendMessage(Component.text("Wall texts reloaded.", NamedTextColor.GREEN));
            }
            default -> help(player);
        }
        return true;
    }

    private void place(Player player, String id, float scale, String rawText) {
        // Plain text (no MiniMessage tags) gets the house aqua accent automatically.
        String text = rawText.contains("<") ? rawText : "<aqua>" + rawText + "</aqua>";
        WallTextService.Placement placement = service.placeAtSight(player, id, text, scale);
        if (placement == null) {
            player.sendMessage(Component.text(
                    "Look at a wall face (N/S/E/W) within 6 blocks and try again.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Wall text '" + placement.id() + "' placed.", NamedTextColor.GREEN));
    }

    private void help(Player player) {
        player.sendMessage(Component.text("/walltext set <id> <text...> - 見ている壁面に文字を貼る", NamedTextColor.AQUA));
        player.sendMessage(Component.text("/walltext scale <id> <0.25-16> <text...> - サイズ指定", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/walltext remove <id> | list | reload", NamedTextColor.GRAY));
        player.sendMessage(Component.text("例: /walltext set arena1 N Arena  (自動で水色)", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("set", "scale", "remove", "list", "reload"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove"))) {
            return filter(args[1], new ArrayList<>(service.all().keySet()));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("scale")) {
            return filter(args[2], List.of("1", "2", "3", "4", "6", "8"));
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).sorted().toList();
    }
}
