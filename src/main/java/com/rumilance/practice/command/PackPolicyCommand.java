package com.rumilance.practice.command;

import com.rumilance.practice.resourcepack.ResourcePackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Console / admin switch for the resource-pack policy — mirrors the admin-GUI toggle:
 * {@code required} kicks players who decline the pack, {@code recommended} lets them join
 * (their rank badges fall back to the N / N+ / OWNER text prefixes).
 */
public final class PackPolicyCommand implements CommandExecutor, TabCompleter {

    private final ResourcePackService packService;

    public PackPolicyCommand(ResourcePackService packService) {
        this.packService = packService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Console (any non-player sender) and admins only.
        if (sender instanceof org.bukkit.entity.Player
                && !sender.hasPermission("rumilance.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            status(sender);
            return true;
        }
        String mode = args[0].toLowerCase(java.util.Locale.ROOT);
        switch (mode) {
            case "required", "must", "on" -> {
                packService.setRequired(true);
                sender.sendMessage(Component.text("Resource pack policy: REQUIRED"
                        + " — players who decline/fail the pack are kicked.", NamedTextColor.RED));
            }
            case "recommended", "optional", "off" -> {
                packService.setRequired(false);
                sender.sendMessage(Component.text("Resource pack policy: RECOMMENDED"
                        + " — players may decline and keep playing (text rank badges).",
                        NamedTextColor.GREEN));
            }
            case "status", "info" -> status(sender);
            default -> sender.sendMessage(Component.text(
                    "Usage: /packpolicy [required|recommended|status]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private void status(CommandSender sender) {
        boolean required = packService.required();
        sender.sendMessage(Component.text("Resource pack policy: ", NamedTextColor.GRAY)
                .append(Component.text(required ? "REQUIRED" : "RECOMMENDED",
                        required ? NamedTextColor.RED : NamedTextColor.GREEN))
                .append(Component.text(required
                        ? " (decline/fail = kick)"
                        : " (decline = join, text badges N / N+ / OWNER)", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : new String[]{"required", "recommended", "status"}) {
                if (option.startsWith(prefix)) {
                    out.add(option);
                }
            }
            return out;
        }
        return List.of();
    }
}
