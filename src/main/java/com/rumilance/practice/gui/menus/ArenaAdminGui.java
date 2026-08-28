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

    public ArenaAdminGui(GuiSessionRegistry registry, SoundService sounds,
                         ArenaTemplateStore arenaStore, ArenaService arenaService) {
        super(registry, sounds, GuiType.ARENA_ADMIN, 6, false);
        this.arenaStore = arenaStore;
        this.arenaService = arenaService;
    }

    public void setPartyIconPrompt(BiConsumer<Player, String> partyIconPrompt) {
        this.partyIconPrompt = partyIconPrompt == null ? (p, n) -> { } : partyIconPrompt;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Arena Admin", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
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
                            UiTheme.labelValue("Id", t.name()),
                            UiTheme.labelValue("Type", t.type().name()),
                            UiTheme.status(t.enabled() ? "ENABLED" : "DISABLED",
                                    t.enabled() ? UiTheme.SUCCESS : UiTheme.MUTED),
                            UiTheme.status(t.party() ? "PARTY MAP" : "NORMAL",
                                    t.party() ? UiTheme.SECONDARY : UiTheme.MUTED),
                            UiTheme.blank(),
                            UiTheme.hint("Left: enable/disable"),
                            UiTheme.hint("Right: cycle type"),
                            UiTheme.hint("Shift-Left: toggle party"),
                            UiTheme.hint("Shift-Right: rename (chat)"),
                            UiTheme.hint("Q: delete")
                    )
                    .glint(t.enabled())
                    .action("arena:" + t.name())
                    .build());
        }
        MenuScaffold.pagingButtons(inventory, page, list.size());
        inventory.setItem(MenuScaffold.gridSlot(MenuScaffold.gridPageSize() - 1), ItemBuilder.of(Material.BOOK)
                .name(Component.text("Pos tools", UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line("/arena pos1 · pos2"),
                        UiTheme.line("/arena selection apply <draft>"),
                        UiTheme.line("/arena draft <Name> → p1/p2 → save")
                )
                .action("decorate")
                .build());
        MenuScaffold.closeButton(inventory);
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
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            arenaStore.delete(name);
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "delete");
            refresh(player, session, inventory);
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            player.closeInventory();
            player.sendMessage(Component.text("Type the new arena name (or 'cancel'):", UiTheme.PRIMARY)
                    .decoration(TextDecoration.ITALIC, false));
            String old = name;
            PendingInput.await(player, text -> {
                if (text.equalsIgnoreCase("cancel") || text.isBlank()) {
                    player.sendMessage(Component.text("Rename cancelled.", UiTheme.MUTED));
                } else {
                    ArenaTemplateStore.RenameResult r = arenaStore.rename(old, text);
                    arenaService.setTemplates(arenaStore.templates());
                    player.sendMessage(Component.text(r == ArenaTemplateStore.RenameResult.OK
                            ? "Renamed to " + text : "Rename failed: " + r.name(),
                            r == ArenaTemplateStore.RenameResult.OK ? UiTheme.SUCCESS : UiTheme.DANGER));
                }
                open(player);
            });
            return;
        }
        if (click == ClickType.SHIFT_LEFT) {
            boolean next = !t.party();
            arenaStore.setParty(name, next);
            arenaService.setTemplates(arenaStore.templates());
            if (next) {
                player.sendMessage(Component.text("Party map ON. Place the icon block in the center.",
                        UiTheme.SUCCESS).decoration(TextDecoration.ITALIC, false));
                partyIconPrompt.accept(player, name);
            }
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (click == ClickType.RIGHT) {
            ArenaType[] values = ArenaType.values();
            ArenaType next = values[(t.type().ordinal() + 1) % values.length];
            arenaStore.setType(name, next);
            arenaService.setTemplates(arenaStore.templates());
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        arenaStore.setEnabled(name, !t.enabled());
        arenaService.setTemplates(arenaStore.templates());
        sounds.play(player, "gui-click");
        refresh(player, session, inventory);
    }
}
