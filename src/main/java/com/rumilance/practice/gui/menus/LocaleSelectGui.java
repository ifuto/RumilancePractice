package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.locale.LocaleService;
import com.rumilance.practice.model.PlayerSettings;
import com.rumilance.practice.session.PlayerSession;
import com.rumilance.practice.session.SessionManager;
import com.rumilance.practice.settings.SettingsService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Language picker, opened automatically on first join (and from {@code /lang} with no
 * arguments). Each supported locale is one banner icon labelled in its OWN language so the
 * option is readable even before any translation is active; clicking applies the locale to
 * the session and persists it.
 */
public final class LocaleSelectGui extends AbstractGui {

    private record LangOption(String code, Material icon, String nativeName, String englishName) {
    }

    /** Display order matches the user-facing requirement list. */
    private static final List<LangOption> OPTIONS = List.of(
            new LangOption("en_us", Material.LIGHT_BLUE_BANNER, "English (US)", "English (US)"),
            new LangOption("en_gb", Material.BLUE_BANNER, "English (UK)", "English (UK)"),
            new LangOption("ja_jp", Material.WHITE_BANNER, "日本語", "Japanese"),
            new LangOption("ko_kr", Material.PURPLE_BANNER, "한국어", "Korean"),
            new LangOption("zh_cn", Material.RED_BANNER, "中文（简体）", "Simplified Chinese"),
            new LangOption("es_es", Material.YELLOW_BANNER, "Español", "Spanish"),
            new LangOption("fr_fr", Material.CYAN_BANNER, "Français", "French")
    );

    private final SessionManager sessionManager;
    private final SettingsService settingsService;
    private final MessageService messageService;

    public LocaleSelectGui(GuiSessionRegistry registry, SoundService sounds,
                           SessionManager sessionManager, SettingsService settingsService,
                           MessageService messageService) {
        super(registry, sounds, GuiType.LOCALE_SELECT, 3, false);
        this.sessionManager = sessionManager;
        this.settingsService = settingsService;
        this.messageService = messageService;
    }

    /** The locale codes this picker offers, in display order. */
    public static List<String> offeredLocales() {
        return OPTIONS.stream().map(LangOption::code).toList();
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.language-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        String current = sessionManager.getSession(player.getUniqueId())
                .map(PlayerSession::locale)
                .map(LocaleService::normalize)
                .orElse(LocaleService.normalize(null));
        for (int i = 0; i < OPTIONS.size(); i++) {
            LangOption option = OPTIONS.get(i);
            inventory.setItem(GuiSlots.slot(1, 1 + i), languageItem(player, option,
                    LocaleService.normalize(option.code()).equals(current)));
        }
        paintNav(player, session, inventory);
    }

    private org.bukkit.inventory.ItemStack languageItem(Player player, LangOption option, boolean current) {
        boolean known = messages() != null && messages().localeService().isSupported(option.code());
        Component name = Component.text(option.nativeName(), current ? UiTheme.SUCCESS : NamedTextColor.WHITE)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        ItemBuilder builder = ItemBuilder.of(option.icon())
                .name(name)
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Language", option.englishName()),
                        UiTheme.blank(),
                        current
                                ? UiTheme.status(line(player, "gui.language-current"), UiTheme.SUCCESS)
                                : UiTheme.hint(line(player, "gui.language-select-hint"))
                )
                .glint(current);
        if (!known) {
            builder.lore(UiTheme.line(line(player, "gui.language-partial")));
        }
        return builder.action("lang:" + option.code()).build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if (action == null || !action.startsWith("lang:")) {
            return;
        }
        String code = LocaleService.normalize(action.substring("lang:".length()));
        if (messageService == null || !messageService.localeService().isSupported(code)) {
            return;
        }
        sessionManager.getSession(player.getUniqueId()).ifPresent(s -> s.setLocale(code));
        PlayerSettings settings = settingsService.get(player);
        settingsService.update(settings.withLocale(code));
        sounds.play(player, "select");
        player.closeInventory();
        messageService.send(player, "settings.locale-changed", MessageService.tags("locale", code));
    }
}
