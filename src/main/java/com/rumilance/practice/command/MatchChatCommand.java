package com.rumilance.practice.command;

import com.rumilance.practice.match.MatchChatListener;
import com.rumilance.practice.match.MatchRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * {@code /matchchat [local|global]} — picks where a fighter's own chat lines go while a duel
 * or party battle is running: {@code local} (default) keeps them inside the match
 * ({@code [Duel]} tagged, fighters + spectators only), {@code global} lets them reach the
 * normal public chat. The choice sticks for the rest of the server session and can be
 * flipped mid-match at any time. Other player's reading is never affected — rerouting only
 * rewrites the speaker's recipients.
 */
public final class MatchChatCommand implements CommandExecutor, TabCompleter {

    private final MatchRegistry registry;

    public MatchChatCommand(MatchRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        boolean current = MatchChatListener.isGlobal(player.getUniqueId());
        boolean target = current;
        if (args.length > 0) {
            String word = args[0].toLowerCase(Locale.ROOT);
            if (word.equals("global")) {
                target = true;
            } else if (word.equals("local")) {
                target = false;
            } else {
                player.sendMessage(Component.text(
                        "Usage: /matchchat [local|global]  (now: " + (current ? "global" : "local") + ")",
                        NamedTextColor.YELLOW));
                return true;
            }
        } else {
            target = !current;
        }
        MatchChatListener.setGlobal(player.getUniqueId(), target);
        boolean inMatch = registry != null && registry.byPlayer(player.getUniqueId()).isPresent();
        if (!inMatch) {
            player.sendMessage(Component.text(
                    "Match chat set to " + (target ? "GLOBAL" : "LOCAL")
                            + " — it takes effect from your next duel.",
                    NamedTextColor.YELLOW));
            return true;
        }
        player.sendMessage(Component.text(
                target
                        ? "Match chat: GLOBAL — your lines now go to the normal public chat."
                        : "Match chat: LOCAL — your lines stay inside this duel only.",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("local", "global").stream()
                    .filter(w -> w.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
