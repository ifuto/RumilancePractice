package com.rumilance.practice.command;

import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.punishment.ChatBanService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Private messages: {@code /tell /msg /w /whisper <player> <message>} and
 * {@code /reply|/r <message>}. Format (lang keys {@code tell.to} / {@code tell.from}):
 * the sender sees "To {Player} » {message}", the receiver "From {Player} » {message}".
 * Chat-banned players cannot whisper.
 */
public final class TellCommand implements CommandExecutor, TabCompleter, Listener {

    private final MessageService messageService;
    private final ChatBanService chatBanService;
    /** Last whisper partner per player, for /reply (updated in both directions). */
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public TellCommand(MessageService messageService, ChatBanService chatBanService) {
        this.messageService = messageService;
        this.chatBanService = chatBanService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            // Console can still /tell: /tell <player> <msg>.
            if (args.length >= 2 && label.equalsIgnoreCase("tell")) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                deliver(null, target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            } else {
                sender.sendMessage(Component.text("Usage: /tell <player> <message>",
                        NamedTextColor.YELLOW));
            }
            return true;
        }
        boolean reply = label.equalsIgnoreCase("r") || label.equalsIgnoreCase("reply");
        Player target;
        String message;
        if (reply) {
            if (args.length < 1) {
                player.sendMessage(messageService.render(player, "tell.usage-reply"));
                return true;
            }
            UUID partnerId = lastPartner.get(player.getUniqueId());
            target = partnerId == null ? null : Bukkit.getPlayer(partnerId);
            if (target == null) {
                player.sendMessage(messageService.render(player, "tell.no-reply"));
                return true;
            }
            message = String.join(" ", args);
        } else {
            if (args.length < 2) {
                player.sendMessage(messageService.render(player, "tell.usage"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(messageService.render(player, "tell.offline",
                        MessageService.tags("target", args[0])));
                return true;
            }
            message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        }
        if (message.isBlank()) {
            player.sendMessage(messageService.render(player, "tell.usage"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(messageService.render(player, "tell.self"));
            return true;
        }
        if (chatBanService != null && chatBanService.isChatBanned(player.getUniqueId())
                && !player.hasPermission("rumilance.punishment.bypass")) {
            player.sendMessage(Component.text("You are ChatBanned. /objection <reason>",
                    NamedTextColor.RED));
            return true;
        }
        deliver(player, target, message);
        return true;
    }

    private void deliver(Player from, Player to, String message) {
        if (from != null) {
            from.sendMessage(messageService.render(from, "tell.to",
                    MessageService.tags("target", to.getName(), "message", message)));
            lastPartner.put(from.getUniqueId(), to.getUniqueId());
        } else {
            to.sendMessage(Component.text("Console", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(" » ", NamedTextColor.GRAY))
                    .append(Component.text(message, NamedTextColor.WHITE)));
        }
        to.sendMessage(messageService.render(to, "tell.from",
                MessageService.tags("target", from == null ? "Console" : from.getName(),
                        "message", message)));
        if (from != null) {
            lastPartner.put(to.getUniqueId(), from.getUniqueId());
        }
    }

    /** Forget reply targets on quit so /r never messages a stale player id. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastPartner.remove(playerId);
        lastPartner.values().removeIf(id -> id.equals(playerId));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        boolean reply = alias.equalsIgnoreCase("r") || alias.equalsIgnoreCase("reply");
        if (!reply && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player senderPlayer
                        && !senderPlayer.canSee(online)) {
                    continue;
                }
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
            return out;
        }
        return List.of();
    }
}
