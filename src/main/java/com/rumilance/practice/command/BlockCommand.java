package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.social.BlockListService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /block <player>} (alias {@code /ignore}) — toggles a player on the sender's block list.
 *
 * <p>Blocking is one-directional for the person who ran the command, but the queue treats the
 * pair as blocked when <em>either</em> side has the other blocked, so a single block is enough
 * to keep two players from ever being matched.</p>
 */
public final class BlockCommand implements CommandExecutor, TabCompleter {

    private final BlockListService blocks;
    private final MessageService messages;

    public BlockCommand(BlockListService blocks, MessageService messages) {
        this.blocks = blocks;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "Players only.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            messages.send(player, "block.usage");
            return true;
        }
        String name = args[0];
        if (name.equalsIgnoreCase(player.getName())) {
            messages.send(player, "block.self");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            messages.send(player, "block.unknown", MessageService.tags("name", name));
            return true;
        }
        UUID targetId = target.getUniqueId();
        boolean nowBlocked = blocks.toggle(player.getUniqueId(), targetId);
        messages.send(player, nowBlocked ? "block.added" : "block.removed",
                MessageService.tags("name", target.getName() == null ? name : target.getName()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
