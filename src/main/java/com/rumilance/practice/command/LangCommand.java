package com.rumilance.practice.command;

import com.rumilance.practice.locale.LocaleService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.session.PlayerSession;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * {@code /lang <ja|en>} — switches the player's practice locale (Japanese or English).
 * Combat titles that should stay English (Match Found, START, WIN, LOSE) are already
 * hard-coded as English in the locale files.
 */
public final class LangCommand implements CommandExecutor, TabCompleter {

    private final SessionManager sessionManager;
    private final SettingsService settingsService;
    private final MessageService messageService;

    public LangCommand(SessionManager sessionManager, SettingsService settingsService,
                       MessageService messageService) {
        this.sessionManager = sessionManager;
        this.settingsService = settingsService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            String current = sessionManager.getSession(player.getUniqueId())
                    .map(PlayerSession::locale).orElse("en_us");
            player.sendMessage(Component.text("Usage: /lang <ja|en>  (now: " + current + ")", NamedTextColor.YELLOW));
            return true;
        }
        String token = args[0].toLowerCase(Locale.ROOT);
        String locale = switch (token) {
            case "ja", "jp", "japanese", "日本語", "ja_jp" -> "ja_jp";
            case "en", "eng", "english", "en_us", "us" -> "en_us";
            default -> null;
        };
        if (locale == null) {
            player.sendMessage(Component.text("Usage: /lang <ja|en>", NamedTextColor.RED));
            return true;
        }
        sessionManager.getSession(player.getUniqueId()).ifPresent(s -> s.setLocale(locale));
        var settings = settingsService.get(player);
        settingsService.update(settings.withLocale(locale));
        messageService.send(player, "settings.locale-changed",
                MessageService.tags("locale", locale));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(TabCompletions.current(args), "ja", "en");
        }
        return List.of();
    }
}
