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
 * {@code /lang} — opens the language picker GUI; {@code /lang <en|uk|ja|ko|zh|es|fr>}
 * switches the practice locale directly. Combat titles that should stay English
 * (Match Found, START, WIN, LOSE) are already hard-coded as English in the locale files.
 */
public final class LangCommand implements CommandExecutor, TabCompleter {

    private final SessionManager sessionManager;
    private final SettingsService settingsService;
    private final MessageService messageService;
    /** Opens the language picker GUI (wired from bootstrap; null = GUI disabled). */
    private volatile java.util.function.Consumer<Player> pickerOpener;

    public LangCommand(SessionManager sessionManager, SettingsService settingsService,
                       MessageService messageService) {
        this.sessionManager = sessionManager;
        this.settingsService = settingsService;
        this.messageService = messageService;
    }

    public void setPickerOpener(java.util.function.Consumer<Player> pickerOpener) {
        this.pickerOpener = pickerOpener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 1) {
            // No argument: open the language picker GUI (falls back to usage when GUI-less).
            if (pickerOpener != null) {
                pickerOpener.accept(player);
                return true;
            }
            String current = sessionManager.getSession(player.getUniqueId())
                    .map(PlayerSession::locale).orElse("en_us");
            player.sendMessage(Component.text("Usage: /lang <" + String.join("|", SHORT_TOKENS)
                    + ">  (now: " + current + ")", NamedTextColor.YELLOW));
            return true;
        }
        String locale = resolveToken(args[0]);
        if (locale == null) {
            player.sendMessage(Component.text("Usage: /lang <" + String.join("|", SHORT_TOKENS) + ">",
                    NamedTextColor.RED));
            return true;
        }
        sessionManager.getSession(player.getUniqueId()).ifPresent(s -> s.setLocale(locale));
        var settings = settingsService.get(player);
        settingsService.update(settings.withLocale(locale));
        messageService.send(player, "settings.locale-changed",
                MessageService.tags("locale", locale));
        return true;
    }

    /** Tab-completable shorthand tokens, one per supported locale (display order). */
    private static final List<String> SHORT_TOKENS = List.of(
            "en", "uk", "ja", "ko", "zh", "es", "fr");

    /** Maps a chat token (short form, full locale code or native name) to a locale code. */
    private static String resolveToken(String raw) {
        if (raw == null) {
            return null;
        }
        String token = raw.toLowerCase(Locale.ROOT).trim();
        return switch (token) {
            case "en", "eng", "english", "en_us", "us" -> "en_us";
            case "uk", "gb", "british", "en_gb" -> "en_gb";
            case "ja", "jp", "japanese", "日本語", "ja_jp" -> "ja_jp";
            case "ko", "kr", "korean", "한국어", "ko_kr" -> "ko_kr";
            case "zh", "cn", "chinese", "中文", "简体中文", "zh_cn" -> "zh_cn";
            case "es", "spanish", "español", "espanol", "es_es" -> "es_es";
            case "fr", "french", "français", "francais", "fr_fr" -> "fr_fr";
            default -> null;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return TabCompletions.filter(TabCompletions.current(args), SHORT_TOKENS);
        }
        return List.of();
    }
}
