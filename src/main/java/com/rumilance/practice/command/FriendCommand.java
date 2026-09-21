package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * {@code /friend} — the friend system's front door.
 *
 * <p>Right now the system itself is not built, so this only announces that: a title and one
 * chat line saying it is planned (requests / accept / deny, no DM by design). The command is
 * registered already so the name is taken and players get an answer instead of "unknown
 * command" — when the feature lands, only the body of this class changes.</p>
 */
public final class FriendCommand implements CommandExecutor {

    private final MessageService messageService;

    public FriendCommand(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player-only");
            return true;
        }
        Component title = messageService.render(player, "friend.title");
        Component subtitle = messageService.render(player, "friend.subtitle");
        player.showTitle(Title.title(title, subtitle,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400))));
        messageService.sendRaw(player, "friend.coming-soon");
        return true;
    }
}
