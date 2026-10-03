package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * KB profile picker for player duels: press the KB tile on the request GUI and this lists
 * "既定", "KBの変更無し" and every {@code plugins/n-arena/kb/*.json} profile as clickable
 * tiles. Picking one (or pressing back) returns to the request GUI carrying every choice —
 * kit, 中キット, map, FT, KB and combat mode — via
 * {@link DuelRequestGui#reopenCarrying}. The old interaction cycled choices on every click
 * (with only a glint as feedback), which read like the button did nothing.
 */
public final class DuelKbSelectGui extends AbstractGui {

    private final DuelRequestGui duelRequestGui;
    private final MessageService messageService;
    private volatile com.rumilance.practice.kb.KbProfileService kbProfileService;

    public void setKbProfileService(com.rumilance.practice.kb.KbProfileService service) {
        this.kbProfileService = service;
    }

    public DuelKbSelectGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            DuelRequestGui duelRequestGui,
            MessageService messageService
    ) {
        super(registry, sounds, GuiType.DUEL_KB_SELECT, 6, true);
        this.duelRequestGui = duelRequestGui;
        this.messageService = messageService;
    }

    /** Opens the picker carrying the request GUI's full choice set. */
    public void openFor(Player player, GuiSession parent) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setFirstTo(parent.firstTo());
        session.put(DuelRequestGui.KB_KEY, parent.get(DuelRequestGui.KB_KEY, String.class));
        session.put(DuelRequestGui.COMBAT_MODE_KEY,
                parent.get(DuelRequestGui.COMBAT_MODE_KEY, String.class));
        session.put(InnerKitSelectGui.CHOICE_KEY,
                parent.get(InnerKitSelectGui.CHOICE_KEY, String.class));
        session.setFromBattleMenu(parent.fromBattleMenu());
        session.setFromGameMenu(parent.fromGameMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.SLIME_BALL;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return text(player, "duel-gui.kb-title").color(UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        String selected = session.get(DuelRequestGui.KB_KEY, String.class);
        boolean defaultSelected = selected == null
                || com.rumilance.practice.kb.KbProfileService.CHOICE_DEFAULT.equals(selected);
        boolean noneSelected = com.rumilance.practice.kb.KbProfileService.CHOICE_NONE.equals(selected);

        // 既定 — the server's configured default profile (or plain vanilla when none is set).
        String defaultName = kbProfileService == null ? "" : kbProfileService.defaultProfileName();
        String defaultDisplayName = defaultName == null || defaultName.isBlank() ? "—" : defaultName;
        inventory.setItem(MenuScaffold.gridSlot(0), ItemBuilder.of(Material.SLIME_BLOCK)
                .name(text(player, "duel-gui.kb-default-name",
                                MessageService.tags("name", defaultDisplayName))
                        .color(UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(raw(player, "duel-gui.kb-default-lore")),
                        UiTheme.blank(),
                        defaultSelected
                                ? UiTheme.status(raw(player, "duel-gui.selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(raw(player, "menu.click"))
                )
                .glint(defaultSelected)
                .action("kb:" + com.rumilance.practice.kb.KbProfileService.CHOICE_DEFAULT)
                .build());

        // KBの変更無し — explicit "no profile layer at all".
        inventory.setItem(MenuScaffold.gridSlot(1), ItemBuilder.of(Material.MILK_BUCKET)
                .name(text(player, "duel-gui.kb-off-name").color(UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(raw(player, "duel-gui.kb-off-lore")),
                        UiTheme.blank(),
                        noneSelected
                                ? UiTheme.status(raw(player, "duel-gui.selected"), UiTheme.SUCCESS)
                                : UiTheme.hint(raw(player, "menu.click"))
                )
                .glint(noneSelected)
                .action("kb:" + com.rumilance.practice.kb.KbProfileService.CHOICE_NONE)
                .build());

        // One tile per profile, in the service's sorted display order.
        List<String> profiles = kbProfileService == null ? List.of() : kbProfileService.names();
        int index = 2;
        for (String profile : profiles) {
            if (index >= MenuScaffold.gridPageSize() - 1) {
                break; // the grid holds far more profiles than any kb folder realistically has
            }
            boolean isSelected = profile.equals(selected);
            String lore = raw(player, "duel-gui.kb-profile-lore", MessageService.tags(
                    "h", factor(kbProfileService.find(profile), 0),
                    "v", factor(kbProfileService.find(profile), 1)));
            inventory.setItem(MenuScaffold.gridSlot(index++), ItemBuilder.of(Material.PAPER)
                    .name(Component.text(profile, UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.line(lore),
                            UiTheme.blank(),
                            isSelected
                                    ? UiTheme.status(raw(player, "duel-gui.selected"), UiTheme.SUCCESS)
                                    : UiTheme.hint(raw(player, "menu.click"))
                    )
                    .glint(isSelected)
                    .action("kb:" + profile)
                    .build());
        }

        if (profiles.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(2), ItemBuilder.of(Material.BOOK)
                    .name(text(player, "duel-gui.kb-empty").color(UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.line(raw(player, "duel-gui.kb-empty-lore"))
                    )
                    .action("decorate")
                    .build());
        }

        MenuScaffold.returnButton(inventory);
    }

    private static String factor(java.util.Optional<double[]> factors, int i) {
        return String.format(java.util.Locale.ROOT, "%.2f",
                factors.map(f -> f[Math.min(i, f.length - 1)]).orElse(1.0d));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            duelRequestGui.reopenCarrying(player, session);
            return;
        }
        if (action != null && action.startsWith("kb:")) {
            String choice = action.substring(3);
            // Unknown profile (json removed since the render): keep the pick inert.
            boolean valid = com.rumilance.practice.kb.KbProfileService.CHOICE_DEFAULT.equals(choice)
                    || com.rumilance.practice.kb.KbProfileService.CHOICE_NONE.equals(choice)
                    || (kbProfileService != null && kbProfileService.exists(choice));
            if (!valid) {
                sounds.play(player, "error");
                return;
            }
            // 既定 is stored as null (= selector untouched) so the request carries no KB name.
            session.put(DuelRequestGui.KB_KEY,
                    com.rumilance.practice.kb.KbProfileService.CHOICE_DEFAULT.equals(choice)
                            ? null : choice);
            sounds.play(player, "select");
            duelRequestGui.reopenCarrying(player, session);
        }
    }

    private Component text(Player player, String key, TagResolver... tags) {
        return messageService.render(messageService.resolveLocale(player), key, tags);
    }

    /**
     * Plain-text of a rendered locale line: {@code <tag>} placeholders resolved, markup
     * stripped — the same embed style {@link DuelRequestGui#kbLabel} uses for UiTheme lines.
     */
    private String raw(Player player, String key, TagResolver... tags) {
        return PlainTextComponentSerializer.plainText()
                .serialize(messageService.render(messageService.resolveLocale(player), key, tags));
    }
}
