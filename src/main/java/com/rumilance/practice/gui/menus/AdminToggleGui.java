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
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin switchboard for {@code /practiceadmin toggle}: queue on/off per kit and map
 * on/off per arena, one tile each, live state in the lore. Toggling runs through the
 * command bridge so the behaviour can never drift from the command.
 */
public final class AdminToggleGui extends AbstractGui {

    /** Executes a /practiceadmin subcommand as the admin (wired from FeatureBootstrap). */
    public interface CommandBridge {
        void run(Player admin, String... args);
    }

    private final KitService kitService;
    private final ArenaTemplateStore arenaStore;
    private CommandBridge bridge = (p, args) -> { };

    public AdminToggleGui(GuiSessionRegistry registry, SoundService sounds,
                          KitService kitService, ArenaTemplateStore arenaStore) {
        super(registry, sounds, GuiType.ADMIN_TOGGLE, 6, false);
        this.kitService = kitService;
        this.arenaStore = arenaStore;
    }

    private java.util.function.Consumer<Player> backToAdminMenu = p -> { };

    /** Reopens the admin menu on Back (same flow as the data screen). */
    public void setBackToAdminMenu(java.util.function.Consumer<Player> back) {
        this.backToAdminMenu = back == null ? p -> { } : back;
    }

    public void setCommandBridge(CommandBridge bridge) {
        this.bridge = bridge == null ? (p, args) -> { } : bridge;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.LEVER;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: Queues & maps").color(NamedTextColor.AQUA);
    }

    private record Entry(String id, String label, Material icon, boolean on,
                         boolean queueToggle) { }

    private List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        try {
            for (KitDefinition kit : kitService.all()) {
                entries.add(new Entry(kit.name(), kit.displayName() == null
                        ? kit.name() : kit.displayName(),
                        material(kit.icon()), kitService.isQueueEnabled(kit.name()), true));
            }
        } catch (Exception ignored) {
            // kits unavailable — maps still render
        }
        try {
            for (ArenaTemplate arena : arenaStore.templates()) {
                entries.add(new Entry(arena.name(), arena.displayNameOrName(),
                        material(arena.iconMaterial()), arena.enabled(), false));
            }
        } catch (Exception ignored) {
            // arenas unavailable — kits still render
        }
        return entries;
    }

    private Material material(String name) {
        if (name != null && !name.isBlank()) {
            Material m = Material.matchMaterial(name.trim().toUpperCase(java.util.Locale.ROOT));
            if (m != null && !m.isAir() && m.isItem()) {
                return m;
            }
        }
        return Material.PAPER;
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<Entry> entries = entries();
        int page = Math.max(0, session.page());
        int pageSize = MenuScaffold.gridPageSize();
        int start = page * pageSize;
        int end = Math.min(entries.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            Entry entry = entries.get(i);
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(entry.on() ? Material.LIME_DYE : Material.GRAY_DYE)
                            .name(Component.text(entry.label(),
                                    entry.on() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue(entry.queueToggle() ? "Queue" : "Map",
                                            entry.on() ? "ENABLED" : "disabled"),
                                    UiTheme.labelValue("Type", entry.queueToggle()
                                            ? "kit queue" : "arena map"),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: "
                                            + (entry.on() ? "disable" : "enable")))
                            .glint(entry.on())
                            .action((entry.queueToggle() ? "q:" : "m:")
                                    + entry.id() + ":" + entry.on())
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, entries.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (entries.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
                Component.text("Go to page " + page, UiTheme.MUTED),
                Component.text("Go to page " + (page + 2), UiTheme.MUTED));

        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(UiTheme.BACK)
                        .name(Component.text("Back", UiTheme.WARNING))
                        .action("back_admin")
                        .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if ("back_admin".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            player.closeInventory();
            if ("back_admin".equals(action)) {
                backToAdminMenu.accept(player);
            }
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
        if (action == null || action.length() < 2) {
            return;
        }
        boolean queueToggle = action.startsWith("q:");
        String rest = action.substring(2);
        int cut = rest.lastIndexOf(':');
        if (cut < 0) {
            return;
        }
        String id = rest.substring(0, cut);
        boolean currentlyOn = Boolean.parseBoolean(rest.substring(cut + 1));
        sounds.play(player, "gui-click");
        if (queueToggle) {
            bridge.run(player, "toggle", "queue", currentlyOn ? "disable" : "enable", id);
        } else {
            bridge.run(player, "toggle", "map", currentlyOn ? "disable" : "enable", id);
        }
        refresh(player, session, inventory);
    }
}
