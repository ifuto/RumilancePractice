package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaService;
import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.ArenaType;
import com.rumilance.practice.util.NameDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Admin arena list GUI ({@code /arena} / {@code /arena gui}).
 */
public final class ArenaAdminGui extends AbstractGui {

    private final ArenaTemplateStore arenaStore;
    private final ArenaService arenaService;
    private BiConsumer<Player, String> partyIconPrompt = (p, n) -> { };
    private volatile java.util.function.Function<String, ArenaDetailGui> detailGuiFactory;

    public ArenaAdminGui(GuiSessionRegistry registry, SoundService sounds,
                         ArenaTemplateStore arenaStore, ArenaService arenaService) {
        super(registry, sounds, GuiType.ARENA_ADMIN, 6, false);
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
    }

    public void setPartyIconPrompt(BiConsumer<Player, String> partyIconPrompt) {
        this.partyIconPrompt = partyIconPrompt == null ? (p, n) -> { } : partyIconPrompt;
    }

    /** Factory for creating detail GUIs for individual arenas. Wired at bootstrap. */
    public void setDetailGuiFactory(java.util.function.Function<String, ArenaDetailGui> factory) {
        this.detailGuiFactory = factory;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.GRASS_BLOCK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.arena-admin-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<ArenaTemplate> list = new ArrayList<>(arenaStore.templates());
        int page = session.page();
        int pageSize = MenuScaffold.gridPageSize();
        int from = Math.min(page * pageSize, list.size());
        int to = Math.min(from + pageSize, list.size());
        int index = 0;
        for (int i = from; i < to; i++) {
            ArenaTemplate t = list.get(i);
            Material icon = Material.matchMaterial(t.iconMaterial() == null ? "" : t.iconMaterial());
            if (icon == null || icon.isAir()) {
                icon = t.enabled() ? Material.GRASS_BLOCK : Material.BARRIER;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), ItemBuilder.of(icon)
                    .name(Component.text(NameDisplay.pretty(t.name()), UiTheme.VALUE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.labelValue(line(player, "gui.arena-id"), t.name()),
                            UiTheme.labelValue(line(player, "gui.arena-type"), t.type().name()),
                            UiTheme.status(line(player, t.enabled() ? "gui.arena-enabled" : "gui.arena-disabled"),
                                    t.enabled() ? UiTheme.SUCCESS : UiTheme.MUTED),
                            UiTheme.status(line(player, t.party() ? "gui.arena-party-map" : "gui.arena-normal"),
                                    t.party() ? UiTheme.SECONDARY : UiTheme.MUTED),
                            UiTheme.blank(),
                            UiTheme.hint(line(player, "menu.click"))
                    )
                    .glint(t.enabled())
                    .action("arena:" + t.name())
                    .build());
        }
        paintPaging(player, inventory, page, list.size());
        inventory.setItem(MenuScaffold.gridSlot(MenuScaffold.gridPageSize() - 1), ItemBuilder.of(Material.BOOK)
                .name(t(player, "gui.arena-pos-tools").color(UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("/arena pos1 · pos2"),
                        UiTheme.line("/arena selection apply <draft>"),
                        UiTheme.line("/arena draft <Name> → p1/p2 → save")
                )
                .action("decorate")
                .build());
        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(Math.max(0, session.page() - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("page:next".equals(action)) {
            session.setPage(session.page() + 1);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action == null || !action.startsWith("arena:")) {
            return;
        }
        String name = action.substring("arena:".length());
        ArenaTemplate t = arenaStore.findExact(name).orElse(null);
        if (t == null) {
            sounds.play(player, "error");
            return;
        }
        // Q (drop) still works as a quick-delete shortcut
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            arenaStore.delete(name);
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "delete");
            refresh(player, session, inventory);
            return;
        }
        // Any click opens the detail management page
        sounds.play(player, "gui-click");
        java.util.function.Function<String, ArenaDetailGui> factory = detailGuiFactory;
        if (factory != null) {
            ArenaDetailGui detail = factory.apply(name);
            if (detail != null) {
                detail.open(player);
                return;
            }
        }
        // Fallback: old toggle behavior if factory not wired
        arenaStore.setEnabled(name, !t.enabled());
        arenaService.setTemplates(arenaStore.templates());
        refresh(player, session, inventory);
    }
}
