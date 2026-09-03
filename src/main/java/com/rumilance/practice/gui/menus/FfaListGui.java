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
        return t(player, "gui.ffa-title").color(UiTheme.DANGER);
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
            inventory.setItem(MenuScaffold.gridSlot(placed), arenaIcon(player, arenas.get(i)));
        }

        if (arenas.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.ffa-empty").color(UiTheme.MUTED))
                            .lore(UiTheme.line(line(player, "gui.ffa-empty-lore")))
                            .action("decorate")
                            .build());
        }

        paintPaging(player, inventory, page, arenas.size());
        paintNav(player, session, inventory);
    }

    private ItemStack arenaIcon(Player viewer, FfaService.FfaArena arena) {
        if (!arena.enabled()) {
            return ItemBuilder.of(Material.BARRIER)
                    .name(Component.text(com.rumilance.practice.util.NameDisplay.pretty(arena.id()), UiTheme.MUTED))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.status(line(viewer, "gui.ffa-disabled"), UiTheme.DANGER),
                            UiTheme.labelValue(line(viewer, "gui.ffa-kit"), arena.kitId()),
                            UiTheme.blank()
                    )
                    .action("decorate")
                    .build();
        }
        int live = occupantsIn(arena.id());
        return ItemBuilder.of(resolveIcon(arena))
                .name(Component.text(com.rumilance.practice.util.NameDisplay.pretty(arena.id()), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.ffa-kit"), arena.kitId()),
                        UiTheme.labelValue(line(viewer, "gui.ffa-count"),
                                line(viewer, "gui.ffa-players").replace("<n>", String.valueOf(live))),
                        UiTheme.status(line(viewer, "gui.ffa-open"), UiTheme.SUCCESS),
                        UiTheme.blank(),
                        UiTheme.hint(line(viewer, "gui.ffa-join-hint"))
                )
                .glint(live > 0)
                .action("ffa:" + arena.id())
                .build();
    }

    /** Number of players currently inside the given FFA arena. */
    private int occupantsIn(String arenaId) {
        int count = 0;
        for (java.util.UUID occupant : ffaService.occupantIds()) {
            if (ffaService.arenaOf(occupant).map(id -> id.equalsIgnoreCase(arenaId)).orElse(false)) {
                count++;
            }
        }
        return count;
    }

    private static Material resolveIcon(FfaService.FfaArena arena) {
        Material mat = Material.matchMaterial(arena.iconMaterial());
        return mat == null ? Material.IRON_SWORD : mat;
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
