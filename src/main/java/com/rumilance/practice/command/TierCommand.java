package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.tier.TierService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /tier} — shows the player's auto skill tier as evaluated from practice-bot fights
 * (best rung beaten at ≥60% over 5+ samples), plus the per-rung win/loss ladder record.
 * Self-view only; admin introspection stays for later tooling.
 */
public final class TierCommand implements CommandExecutor {

    private final TierService tierService;
    private final MessageService messageService;

    public TierCommand(TierService tierService, MessageService messageService) {
        this.tierService = tierService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("In-game only.");
            return true;
        }
        TierService.Tier tier = tierService.tierOf(player.getUniqueId());
        NamedTextColor color = NamedTextColor.NAMES.value(tier.color().name().toLowerCase());
        if (color == null) {
            color = NamedTextColor.WHITE;
        }
        player.sendMessage(messageService.render(player, "tier.header"));
        player.sendMessage(messageService.render(player, "tier.current")
                .append(Component.text(tier.label(), color)));
        player.sendMessage(messageService.render(player, "tier.samples",
                MessageService.tags("n", String.valueOf(tierService.samples(player.getUniqueId())))));
        player.sendMessage(messageService.render(player, "tier.progress-title"));
        for (String line : tierService.progressLine(player.getUniqueId()).split("\n")) {
            player.sendMessage(Component.text("  " + line, NamedTextColor.GRAY));
        }
        player.sendMessage(messageService.render(player, "tier.hint"));
        return true;
    }
}
