package com.rumilance.practice.command;

import com.rumilance.practice.match.MatchRegistry;
import com.rumilance.practice.settings.SettingsService;
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
 * {@code /matchchat [local|global]} — command-line shortcut for the send-toggle that also
 * lives on the Chat Settings screen of {@code /setting}. It picks where a fighter's own chat
 * lines go while a duel or party battle is running: {@code local} (default) keeps them inside
 * the match ({@code [Duel]} tagged, fighters + spectators only), {@code global} lets them reach
 * the normal public chat.
 *
 * <p>The choice is stored in the player's settings, so — unlike the old in-memory flag — it
 * survives a restart. Receiving is unaffected: that is the Duel Chat / Global Chat reception
 * pair on the same settings screen. Flipping it mid-match takes effect immediately.</p>
 */
public final class MatchChatCommand implements CommandExecutor, TabCompleter {

    private final MatchRegistry registry;
    private final SettingsService settingsService;

    public MatchChatCommand(MatchRegistry registry, SettingsService settingsService) {
        this.registry = registry;
        this.settingsService = settingsService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (settingsService == null) {
            sender.sendMessage(Component.text("Settings are not available.", NamedTextColor.RED));
            return true;
        }
        boolean current = settingsService.get(player).duelChatGlobal();
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
        settingsService.update(settingsService.get(player).withDuelChatGlobal(target));
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
