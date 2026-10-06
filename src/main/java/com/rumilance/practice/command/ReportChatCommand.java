package com.rumilance.practice.command;

import com.rumilance.practice.chat.ChatLogService;
import com.rumilance.practice.chat.ChatReportService;
import com.rumilance.practice.locale.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /reportchat <id>} — the callback behind the "click to report" chat hover.
 *
 * <p>Not really meant to be typed: every chat line carries a click event pointing here with its
 * own log id. The id is resolved against the live chat buffer, so reporting a line that has
 * already scrolled out fails cleanly instead of filing an empty report.</p>
 */
public final class ReportChatCommand implements CommandExecutor {

    private final ChatReportService reports;
    private final ChatLogService log;
    private final MessageService messages;

    public ReportChatCommand(ChatReportService reports, ChatLogService log,
                             MessageService messages) {
        this.reports = reports;
        this.log = log;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            return true;
        }
        long lineId;
        try {
            lineId = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            return true;
        }
        ChatLogService.ChatLine line = log.find(lineId).orElse(null);
        if (line == null) {
            messages.send(player, "report.expired");
            return true;
        }
        if (line.senderId().equals(player.getUniqueId())) {
            messages.send(player, "report.self");
            return true;
        }
        boolean filed = reports.report(lineId, player.getUniqueId(), line.senderId(),
                line.senderName());
        messages.send(player, filed ? "report.filed" : "report.duplicate",
                MessageService.tags("target", line.senderName()));
        return true;
    }
}
