package com.rumilance.practice.command;

import com.rumilance.practice.gui.GuiSnapshotService;
import com.rumilance.practice.gui.GuiType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /guisnapshot} — renders menus offscreen into {@code plugins/n-arena/gui-snapshots/*.json}
 * so {@code tools/gui_diff.py} can diff them against {@code docs/design/gui.json}.
 */
public final class GuiSnapshotCommand implements CommandExecutor, TabCompleter {

    private final GuiSnapshotService service;

    public GuiSnapshotCommand(GuiSnapshotService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only — a render needs a viewer.",
                    NamedTextColor.RED));
            return true;
        }
        String which = args.length > 0 ? args[0] : "all";
        String category = args.length > 1 && !"-".equals(args[1]) ? args[1] : null;
        int page = args.length > 2 ? parseInt(args[2]) : 0;
        try {
            if ("all".equalsIgnoreCase(which)) {
                List<Path> written = service.dumpAll(player);
                sender.sendMessage(Component.text("Wrote " + written.size() + " snapshots to "
                        + service.outputDir(), NamedTextColor.GREEN));
                return true;
            }
            GuiType type;
            try {
                type = GuiType.valueOf(which.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("Unknown GuiType: " + which, NamedTextColor.RED));
                return true;
            }
            Path file = service.dump(player, type, category, page);
            sender.sendMessage(file == null
                    ? Component.text("No registered handler for " + type, NamedTextColor.RED)
                    : Component.text("Wrote " + file, NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Snapshot failed: " + e.getMessage(),
                    NamedTextColor.RED));
        }
        return true;
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("rumilance.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("all"));
            for (GuiType type : GuiType.values()) {
                options.add(type.name());
            }
            return filter(args[0], options);
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
