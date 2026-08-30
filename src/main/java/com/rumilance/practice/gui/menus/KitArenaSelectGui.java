package com.rumilance.practice.gui.menus;

import com.rumilance.practice.arena.ArenaTemplateStore;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.ArenaTemplate;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.NameDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Admin GUI to toggle duel and party arena pools for a kit.
 * Left columns: duel arenas; right columns: party arenas.
 */
public final class KitArenaSelectGui extends AbstractGui {

    private final KitService kitService;
    private final ArenaTemplateStore arenaStore;
    private KitAdminGui kitAdminGui;

    public KitArenaSelectGui(GuiSessionRegistry registry, SoundService sounds,
                             KitService kitService, ArenaTemplateStore arenaStore) {
        super(registry, sounds, GuiType.KIT_ARENA_SELECT, 6, false);
        this.kitService = kitService;
        this.arenaStore = arenaStore;
    }

    public void setKitAdminGui(KitAdminGui kitAdminGui) {
        this.kitAdminGui = kitAdminGui;
    }

    public void open(Player player, String kitId) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit();
        return t(player, "gui.kit-arena-title", MessageService.tags("kit", kit == null ? "?" : kit))
                .color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        Set<String> duel = new HashSet<>(kit.arenas());
        Set<String> party = new HashSet<>(kit.partyArenas());

        inventory.setItem(GuiSlots.slot(0, 0), ItemBuilder.action(UiTheme.BACK,
                t(player, "menu.back"), "back"));
        inventory.setItem(GuiSlots.slot(0, 4), ItemBuilder.of(Material.BOOK)
                .name(t(player, "gui.kit-arena-hint").color(UiTheme.SECONDARY))
                .lore(
                        UiTheme.line(line(player, "gui.arena-empty-random")),
                        UiTheme.line(line(player, "gui.arena-selected"))
                )
                .action("noop")
                .build());

        List<ArenaTemplate> templates = arenaStore.templates();
        int row = 1;
        int col = 0;
        for (ArenaTemplate t : templates) {
            if (row >= 5) {
                break;
            }
            boolean inDuel = duel.contains(t.name().toLowerCase(Locale.ROOT));
            boolean inParty = party.contains(t.name().toLowerCase(Locale.ROOT));
            Material duelMat = inDuel ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            Material partyMat = inParty ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            String label = NameDisplay.pretty(t.name()) + (t.party() ? " [P]" : "");

            inventory.setItem(GuiSlots.slot(row, col), ItemBuilder.of(duelMat)
                    .name(t(player, "gui.kit-arena-duel", MessageService.tags("name", label))
                            .color(inDuel ? UiTheme.SUCCESS : UiTheme.MUTED))
                    .lore(UiTheme.hint(line(player, "gui.arena-toggle")))
                    .action("duel:" + t.name())
                    .build());
            inventory.setItem(GuiSlots.slot(row, col + 5), ItemBuilder.of(partyMat)
                    .name(t(player, "gui.kit-arena-party", MessageService.tags("name", label))
                            .color(inParty ? UiTheme.SUCCESS : UiTheme.MUTED))
                    .lore(UiTheme.hint(line(player, "gui.arena-toggle")))
                    .action("party:" + t.name())
                    .build());

            col++;
            if (col >= 4) {
                col = 0;
                row++;
            }
        }
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null || "noop".equals(action)) {
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-click");
            if (kitAdminGui != null && session.selectedKit() != null) {
                kitAdminGui.openConfig(player, session.selectedKit());
            } else {
                player.closeInventory();
            }
            return;
        }
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        if (action.startsWith("duel:")) {
            String arena = action.substring(5).toLowerCase(Locale.ROOT);
            List<String> next = new ArrayList<>(kit.arenas());
            if (next.contains(arena)) {
                next.remove(arena);
            } else {
                next.add(arena);
            }
            kitService.save(kit.toBuilder().arenas(next).build());
            sounds.play(player, "select");
            render(player, session, inventory);
            return;
        }
        if (action.startsWith("party:")) {
            String arena = action.substring(6).toLowerCase(Locale.ROOT);
            List<String> next = new ArrayList<>(kit.partyArenas());
            if (next.contains(arena)) {
                next.remove(arena);
            } else {
                next.add(arena);
            }
            kitService.save(kit.toBuilder().partyArenas(next).build());
            sounds.play(player, "select");
            render(player, session, inventory);
        }
    }
}
