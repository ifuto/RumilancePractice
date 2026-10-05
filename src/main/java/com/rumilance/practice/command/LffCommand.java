package com.rumilance.practice.command;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /lff} — toggle Looking For Fight while inside an FFA arena.
 *
 * <p>Only works in an arena whose LFF switch is ON (per-arena, default OFF). It is the same
 * toggle as the 9th hotbar slot item such an arena forces into the kit, so the command exists
 * for players who would rather type than click.</p>
 */
public final class LffCommand implements CommandExecutor {

    private final FfaService ffaService;
    private final MessageService messages;

    public LffCommand(FfaService ffaService, MessageService messages) {
        this.ffaService = ffaService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        String arenaId = ffaService.arenaOf(player.getUniqueId()).orElse(null);
        if (arenaId == null || !ffaService.lffEnabled(arenaId)) {
            messages.send(player, "ffa.lff-disabled");
            return true;
        }
        boolean nowLooking = ffaService.lookingForFight().toggle(player);
        player.playSound(player.getLocation(),
                nowLooking ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        messages.send(player, nowLooking ? "ffa.lff-on" : "ffa.lff-off");
        return true;
    }
}
