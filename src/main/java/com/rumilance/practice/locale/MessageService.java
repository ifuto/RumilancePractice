package com.rumilance.practice.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Function;

/**
 * Bukkit/Adventure-facing companion to {@link LocaleService}: renders MiniMessage strings into
 * {@link Component}s and sends them to a {@link CommandSender}, automatically resolving the
 * correct locale for players (falling back to the plugin's default locale for consoles/other
 * senders).
 *
 * <p>Chat messages carry no prefix by design ({@code general.prefix} is intentionally blank):
 * messages are short, self-explanatory and locale-localised, so a leading tag would only add
 * noise.</p>
 */
public final class MessageService {

    public static final String PREFIX_KEY = "general.prefix";

    private final LocaleService localeService;
    private final MiniMessage miniMessage;
    private final Function<Player, String> playerLocaleResolver;

    public MessageService(LocaleService localeService, Function<Player, String> playerLocaleResolver) {
        this.localeService = Objects.requireNonNull(localeService, "localeService");
        this.playerLocaleResolver = Objects.requireNonNull(playerLocaleResolver, "playerLocaleResolver");
        this.miniMessage = MiniMessage.miniMessage();
    }

    public LocaleService localeService() {
        return localeService;
    }

    public String resolveLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            String resolved = playerLocaleResolver.apply(player);
            return resolved != null ? resolved : localeService.defaultLocale();
        }
        return localeService.defaultLocale();
    }

    public Component render(String locale, String key, TagResolver... resolvers) {
        String raw = localeService.rawMessage(locale, key);
        return miniMessage.deserialize(raw, resolvers);
    }

    public Component renderWithPrefix(String locale, String key, TagResolver... resolvers) {
        Component prefix = miniMessage.deserialize(localeService.rawMessage(locale, PREFIX_KEY));
        return prefix.append(render(locale, key, resolvers));
    }

    /**
     * Sends the message for {@code key}, prefixed with {@code general.prefix}, to {@code target}
     * using {@code target}'s resolved locale.
     */
    public void send(CommandSender target, String key, TagResolver... resolvers) {
        String locale = resolveLocale(target);
        target.sendMessage(renderWithPrefix(locale, key, resolvers));
    }

    /**
     * Sends the message for {@code key} without the {@code general.prefix} component (useful for
     * multi-line displays such as leaderboards where a per-line prefix would be noisy).
     */
    public void sendRaw(CommandSender target, String key, TagResolver... resolvers) {
        String locale = resolveLocale(target);
        target.sendMessage(render(locale, key, resolvers));
    }

    /**
     * Convenience: builds MiniMessage {@link Placeholder#unparsed(String, String)} resolvers from
     * alternating {@code name, value} pairs, e.g.
     * {@code tags("target", name, "kit", kitId)}. Keeps call sites terse and locale-agnostic.
     */
    public static TagResolver[] tags(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Placeholder pairs must be name/value, got odd count: "
                    + keyValuePairs.length);
        }
        TagResolver[] resolvers = new TagResolver[keyValuePairs.length / 2];
        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = Placeholder.unparsed(keyValuePairs[i * 2], keyValuePairs[i * 2 + 1]);
        }
        return resolvers;
    }

    /** Localised word for the match mode ("ranked"/"unranked") for {@code target}'s locale. */
    public String modeWord(CommandSender target, boolean ranked) {
        return modeWord(resolveLocale(target), ranked);
    }

    /** Localised word for the match mode ("ranked"/"unranked") for an explicit locale. */
    public String modeWord(String locale, boolean ranked) {
        return localeService.rawMessage(locale, "general.mode-" + (ranked ? "ranked" : "unranked"));
    }
}
