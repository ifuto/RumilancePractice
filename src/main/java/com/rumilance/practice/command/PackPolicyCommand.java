package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.resourcepack.ResourcePackService;
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
    private final MessageService messageService;

    public PackPolicyCommand(ResourcePackService packService, MessageService messageService) {
        this.packService = packService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Console (any non-player sender) and admins only.
        if (sender instanceof org.bukkit.entity.Player
                && !sender.hasPermission("rumilance.admin")) {
            messageService.send(sender, "general.no-permission");
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
                messageService.send(sender, "packpolicy.set-required");
            }
            case "recommended", "optional", "off" -> {
                packService.setRequired(false);
                messageService.send(sender, "packpolicy.set-recommended");
            }
            case "status", "info" -> status(sender);
            default -> messageService.send(sender, "packpolicy.usage");
        }
        return true;
    }

    private void status(CommandSender sender) {
        boolean required = packService.required();
        messageService.send(sender, required
                ? "packpolicy.status-required"
                : "packpolicy.status-recommended");
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
