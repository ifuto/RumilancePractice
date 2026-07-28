package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.match.MatchService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /leave} ({@code /le}) - lets a player walk away from a match during the pre-match
 * countdown with no penalty (the match is simply cancelled). Repeated dodging is handled inside
 * {@link MatchService#leaveDuringCountdown(Player)} (3-day ChatBan from the third consecutive
 * countdown-leave).
 */
public final class LeaveCommand implements CommandExecutor {

    private final MatchService matchService;
    private final MessageService messageService;

    public LeaveCommand(MatchService matchService, MessageService messageService) {
        this.matchService = matchService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player-only");
            return true;
        }
        MatchService.LeaveOutcome outcome = matchService.leaveDuringCountdown(player);
        String key = switch (outcome) {
            case NOT_COUNTDOWN -> "match.cannot-leave";
            case CANCELLED -> "match.leave-cancelled";
            case CANCELLED_AND_BANNED -> "match.leave-banned";
        };
        messageService.send(player, key);
        return true;
    }
}
