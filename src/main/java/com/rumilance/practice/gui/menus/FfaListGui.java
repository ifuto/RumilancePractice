package com.rumilance.practice.gui.menus;

import com.rumilance.practice.ffa.FfaService;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * FFA arena picker. Each enabled arena is a sword icon with its kit and a join hint; disabled
 * arenas show a barrier. The list uses the standard 28-slot content grid and is paged when
 * more than 28 arenas are configured.
 */
public final class FfaListGui extends AbstractGui {

    private final FfaService ffaService;

    public FfaListGui(GuiSessionRegistry registry, SoundService sounds, FfaService ffaService) {
        super(registry, sounds, GuiType.FFA_LIST, 6, false);
        this.ffaService = ffaService;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("⚔ FFA Arenas", UiTheme.DANGER).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        MenuScaffold.header(inventory, 0, title(player, session));

        var arenas = ffaService.list();
        int pageSize = MenuScaffold.gridPageSize();
        int page = session.page();
        int offset = page * pageSize;

        int placed = 0;
        for (int i = offset; i < arenas.size() && placed < pageSize; i++, placed++) {
            inventory.setItem(MenuScaffold.gridSlot(placed), arenaIcon(arenas.get(i)));
        }

        if (arenas.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No FFA arenas configured", UiTheme.MUTED))
                            .lore(UiTheme.line("Ask an admin to set one up."))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, arenas.size());
        MenuScaffold.closeButton(inventory);
    }

    private ItemStack arenaIcon(FfaService.FfaArena arena) {
        if (!arena.enabled()) {
            return ItemBuilder.of(Material.BARRIER)
                    .name(Component.text(arena.id(), UiTheme.MUTED))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.status("DISABLED", UiTheme.DANGER),
                            UiTheme.labelValue("Kit", arena.kitId()),
                            UiTheme.blank()
                    )
                    .action("decorate")
                    .build();
        }
        return ItemBuilder.of(Material.IRON_SWORD)
                .name(Component.text(arena.id(), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Kit", arena.kitId()),
                        UiTheme.status("ONLINE", UiTheme.SUCCESS),
                        UiTheme.blank(),
                        UiTheme.hint("Click to join FFA")
                )
                .glint(true)
                .action("ffa:" + arena.id())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if ("close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if ("page:prev".equals(action)) {
            session.setPage(session.page() - 1);
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
        if (action.startsWith("ffa:")) {
            sounds.play(player, "select");
            player.closeInventory();
            ffaService.join(player, action.substring(4));
        }
    }
}
