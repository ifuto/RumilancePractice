package com.rumilance.practice.command;

import com.rumilance.practice.config.ConfigService;
import com.rumilance.practice.lobby.FloatingQueueService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /float} — the floating lobby items that put you straight into a kit's queue.
 *
 * <pre>
 *   /float spawn queue &lt;kit&gt;   the kit's own icon, spinning where you stand
 *   /float remove [kit]        remove one kit's item, or all of them
 *   /float list                what is currently floating
 * </pre>
 *
 * <p>Admin-only: it places persistent world entities.</p>
 */
public final class FloatCommand implements CommandExecutor, TabCompleter {

    private final FloatingQueueService floatingQueueService;
    private final ConfigService configService;

    public FloatCommand(FloatingQueueService floatingQueueService, ConfigService configService) {
        this.floatingQueueService = floatingQueueService;
        this.configService = configService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            default -> usage(sender);
        }
        return true;
    }

    private void spawn(CommandSender sender, String[] args) {
        Player player = sender instanceof Player p ? p : null;
        if (player == null) {
            sender.sendMessage(Component.text("Only a player can spawn one — it goes where you"
                    + " are standing.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || !"queue".equalsIgnoreCase(args[1])) {
            sender.sendMessage(Component.text("Usage: /float spawn queue <kit>",
                    NamedTextColor.YELLOW));
            return;
        }
        String kit = args[2];
        if (!floatingQueueService.spawn(player.getLocation(), kit)) {
            sender.sendMessage(Component.text("Unknown kit (or it has no usable icon): " + kit,
                    NamedTextColor.RED));
            return;
        }
        floatingQueueService.saveToConfig(configService.lobby());
        configService.save(com.rumilance.practice.config.ConfigService.LOBBY);
        sender.sendMessage(Component.text("Floating queue item spawned for " + kit
                + ". Click it to join that kit's unranked queue.", NamedTextColor.GREEN));
    }

    private void remove(CommandSender sender, String[] args) {
        int removed;
        if (args.length >= 2) {
            removed = floatingQueueService.removeKit(args[1]);
            sender.sendMessage(removed == 0
                    ? Component.text("No floating item for " + args[1] + ".", NamedTextColor.YELLOW)
                    : Component.text("Removed " + removed + " floating item(s) for " + args[1] + ".",
                            NamedTextColor.GREEN));
        } else {
            removed = floatingQueueService.size();
            floatingQueueService.removeAll();
            sender.sendMessage(Component.text("Removed " + removed + " floating item(s).",
                    NamedTextColor.GREEN));
        }
        floatingQueueService.saveToConfig(configService.lobby());
        configService.save(com.rumilance.practice.config.ConfigService.LOBBY);
    }

    private void list(CommandSender sender) {
        List<String> kits = floatingQueueService.kitNames();
        if (kits.isEmpty()) {
            sender.sendMessage(Component.text("No floating queue items yet. Use"
                    + " /float spawn queue <kit>.", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("Floating queue items (" + kits.size() + "):",
                NamedTextColor.GOLD));
        for (String kit : kits) {
            sender.sendMessage(Component.text("  - " + kit, NamedTextColor.GRAY));
        }
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /float spawn queue <kit>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /float remove [kit]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /float list", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias,
                                      String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("rumilance.admin")) {
            return out;
        }
        String partial = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            addMatching(out, partial, "spawn", "remove", "list");
        } else if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
            addMatching(out, partial, "queue");
        } else if (args.length == 3 && "spawn".equalsIgnoreCase(args[0])) {
            addKits(out, partial);
        } else if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
            addKits(out, partial);
        }
        return out;
    }

    /** Every kit that could be floated, including ones without an item yet. */
    private void addKits(List<String> out, String partial) {
        java.util.Collection<String> kits = floatingQueueService.kitIds();
        if (kits == null) {
            return;
        }
        for (String kit : kits) {
            if (kit != null && kit.toLowerCase(Locale.ROOT).startsWith(partial)) {
                out.add(kit);
            }
        }
    }

    private static void addMatching(List<String> out, String partial, String... options) {
        for (String option : options) {
            if (option.startsWith(partial)) {
                out.add(option);
            }
        }
    }
}
