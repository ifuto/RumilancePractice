package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kb.KbProfileService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Admin picker for the default KB profile ({@code /practiceadmin kbdefault}): every
 * {@code kb/*.json} profile as a tile, the live default glinting, plus an OFF tile that
 * falls back to knockback.json rules. Selection runs through the command bridge (it owns
 * the config.yml persistence + service reconfiguration).
 */
public final class KbDefaultGui extends AbstractGui {

    /** Executes a /practiceadmin subcommand as the admin (wired from FeatureBootstrap). */
    public interface CommandBridge {
        void run(Player admin, String... args);
    }

    private final KbProfileService kbProfileService;
    private CommandBridge bridge = (p, args) -> { };

    public KbDefaultGui(GuiSessionRegistry registry, SoundService sounds,
                        KbProfileService kbProfileService) {
        super(registry, sounds, GuiType.ADMIN_KB_DEFAULT, 6, false);
        this.kbProfileService = kbProfileService;
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
        return Material.IRON_HORSE_ARMOR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: Default KB profile").color(NamedTextColor.AQUA);
    }

    private String currentDefault() {
        try {
            return kbProfileService == null ? null : kbProfileService.defaultProfileName();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> names() {
        try {
            return kbProfileService == null ? List.of() : kbProfileService.names();
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String current = currentDefault();
        boolean off = current == null || current.isBlank();
        List<String> profiles = names();
        int page = Math.max(0, session.page());
        int pageSize = MenuScaffold.gridPageSize() - 1; // keep a slot for the OFF tile
        int start = page * pageSize;
        int end = Math.min(profiles.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            String name = profiles.get(i);
            boolean active = !off && name.equalsIgnoreCase(current);
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(active ? Material.IRON_HORSE_ARMOR : Material.LEATHER_HORSE_ARMOR)
                            .name(Component.text(name,
                                    active ? NamedTextColor.AQUA : UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Source", "kb/" + name + ".json"),
                                    active ? UiTheme.labelValue("Default", "YES")
                                           : UiTheme.line("not the default"),
                                    UiTheme.blank(),
                                    UiTheme.hint(active ? "Click: re-apply"
                                            : "Click: make default"))
                            .glint(active)
                            .action("kb:" + name)
                            .build());
        }

        inventory.setItem(GuiSlots.slot(4, 7),
                ItemBuilder.of(off ? Material.BARRIER : Material.GRAY_DYE)
                        .name(Component.text("(none / knockback.json rules)",
                                off ? NamedTextColor.RED : UiTheme.MUTED))
                        .lore(UiTheme.line("Clears the fixed default so global"),
                                UiTheme.line("knockback.json rules apply again."),
                                UiTheme.blank(),
                                UiTheme.hint("Click: turn default profile off"))
                        .glint(off)
                        .action("kboff")
                        .build());

        if (profiles.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(Component.text("No kb/*.json profiles found", UiTheme.DANGER))
                            .lore(UiTheme.line("Drop profile JSONs into kb/ and"),
                                    UiTheme.line("run /practiceadmin reload."))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, profiles.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (profiles.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
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
        if ("kboff".equals(action)) {
            sounds.play(player, "gui-click");
            bridge.run(player, "kbdefault", "off");
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("kb:")) {
            sounds.play(player, "gui-click");
            bridge.run(player, "kbdefault", action.substring(3));
            refresh(player, session, inventory);
        }
    }
}
