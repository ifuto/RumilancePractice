package com.rumilance.practice.gui.menus;

import com.rumilance.practice.admin.AdminPlayerLookupListener;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.RealPlayers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Admin player browser: every online player as a head. Click opens the full data editor,
 * right-click kicks, shift-click force-ends their match — the exact {@code /practiceadmin
 * kick|forceend} behaviour via the command bridge. The "find by name" tile reuses the admin
 * chat lookup so offline players can be inspected too.
 */
public final class AdminPlayersGui extends AbstractGui {

    /** Executes a /practiceadmin subcommand as the admin (wired from FeatureBootstrap). */
    public interface CommandBridge {
        void run(Player admin, String... args);
    }

    private final MatchService matchService;
    private AdminPlayerDataGui dataGui;
    private CommandBridge bridge = (p, args) -> { };

    public AdminPlayersGui(GuiSessionRegistry registry, SoundService sounds,
                           MatchService matchService) {
        super(registry, sounds, GuiType.ADMIN_PLAYERS, 6, false);
        this.matchService = matchService;
    }

    public void setDataGui(AdminPlayerDataGui dataGui) {
        this.dataGui = dataGui;
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
        return Material.PLAYER_HEAD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: Players").color(NamedTextColor.AQUA);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<Player> online = RealPlayers.online();
        int page = Math.max(0, session.page());
        int pageSize = com.rumilance.practice.gui.MenuScaffold.gridPageSize();
        int start = page * pageSize;
        int end = Math.min(online.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            Player target = online.get(i);
            String busy = matchService == null ? null : matchService.busyReason(target.getUniqueId());
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(Material.PLAYER_HEAD)
                            .skullOwner(target)
                            .name(Component.text(target.getName(), UiTheme.HEADER)
                                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Status", busy == null ? "free" : busy),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: open data editor"),
                                    UiTheme.hint("Right-click: kick"),
                                    UiTheme.hint("Shift-click: force-end match"))
                            .glint(busy != null)
                            .action("p:" + target.getUniqueId())
                            .build());
        }

        com.rumilance.practice.gui.MenuScaffold.pagingButtons(inventory, page, online.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (online.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
                Component.text("Go to page " + page, UiTheme.MUTED),
                Component.text("Go to page " + (page + 2), UiTheme.MUTED));

        inventory.setItem(GuiSlots.slot(5, 0),
                ItemBuilder.of(Material.SPYGLASS)
                        .name(Component.text("Find by name / UUID", UiTheme.PRIMARY))
                        .lore(UiTheme.line("Opens the chat lookup — works for"),
                                UiTheme.line("offline players too."),
                                UiTheme.blank(),
                                UiTheme.hint("Click: type name in chat"))
                        .action("find")
                        .build());

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
        if ("find".equals(action)) {
            sounds.play(player, "gui-click");
            session.put(AdminPlayerLookupListener.AWAIT_LOOKUP, Boolean.TRUE);
            player.closeInventory();
            player.sendMessage(Component.text(
                    "Type a UUID or MCID (player name) in chat to open their data editor.",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (action != null && action.startsWith("p:")) {
            java.util.UUID target;
            try {
                target = java.util.UUID.fromString(action.substring(2));
            } catch (IllegalArgumentException e) {
                return;
            }
            Player online = Bukkit.getPlayer(target);
            if (online == null) {
                sounds.play(player, "error");
                player.sendMessage(Component.text("Player went offline.", NamedTextColor.RED));
                refresh(player, session, inventory);
                return;
            }
            boolean shift = clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                    || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            if (shift) {
                sounds.play(player, "gui-click");
                bridge.run(player, "forceend", online.getName());
                refresh(player, session, inventory);
            } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                    || clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                sounds.play(player, "gui-click");
                bridge.run(player, "kick", online.getName());
                refresh(player, session, inventory);
            } else {
                sounds.play(player, "gui-click");
                if (dataGui != null) {
                    player.closeInventory();
                    dataGui.openFor(player, target);
                }
            }
        }
    }
}
