package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.match.MatchService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin live-match browser: every active match with participants, kit and state. Click
 * force-ends (draw-end) the match — identical to {@code /practiceadmin forceend <player>}.
 */
public final class AdminMatchesGui extends AbstractGui {

    private final MatchService matchService;

    private java.util.function.Consumer<Player> backToAdminMenu = p -> { };
    /** Reopens the admin menu on Back (same flow as the data screen). */
    public void setBackToAdminMenu(java.util.function.Consumer<Player> back) {
        this.backToAdminMenu = back == null ? p -> { } : back;
    }


    public AdminMatchesGui(GuiSessionRegistry registry, SoundService sounds,
                           MatchService matchService) {
        super(registry, sounds, GuiType.ADMIN_MATCHES, 6, false);
        this.matchService = matchService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.WRITABLE_BOOK;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("Admin: Live matches").color(NamedTextColor.AQUA);
    }

    private List<com.rumilance.practice.session.MatchSession> matches() {
        try {
            List<com.rumilance.practice.session.MatchSession> all = new ArrayList<>(
                    matchService.registry().all());
            all.sort((a, b) -> a.id().compareTo(b.id()));
            return all;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String raw = uuid.toString();
        return raw.substring(0, 8) + "…";
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        List<com.rumilance.practice.session.MatchSession> matches = matches();
        int page = Math.max(0, session.page());
        int pageSize = MenuScaffold.gridPageSize();
        int start = page * pageSize;
        int end = Math.min(matches.size(), start + pageSize);

        for (int i = start; i < end; i++) {
            com.rumilance.practice.session.MatchSession match = matches.get(i);
            List<UUID> participants = match.participants();
            List<String> names = participants.stream().map(this::nameOf).toList();
            String endAction = participants.isEmpty()
                    ? "decorate" : "end:" + participants.get(0);
            inventory.setItem(GuiSlots.slot(1 + (i - start) / 7, 1 + (i - start) % 7),
                    ItemBuilder.of(Material.NAME_TAG)
                            .name(Component.text("#" + match.id().toString().substring(0, 8),
                                    UiTheme.HEADER))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue("Mode", match.mode().name()),
                                    UiTheme.labelValue("State", match.state().name()),
                                    UiTheme.labelValue("Kit", match.kitName()),
                                    UiTheme.labelValue("Players",
                                            String.join(", ", names)),
                                    UiTheme.blank(),
                                    UiTheme.hint("Click: force-end (draw)"))
                            .action(endAction)
                            .build());
        }

        if (matches.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                            .name(Component.text("No live matches", UiTheme.SUCCESS))
                            .action("decorate")
                            .build());
        }

        MenuScaffold.pagingButtons(inventory, page, matches.size(),
                Component.text("◀ Previous", UiTheme.PRIMARY),
                Component.text("Next ▶", UiTheme.PRIMARY),
                Component.text((page + 1) + " / "
                        + Math.max(1, (matches.size() + pageSize - 1) / pageSize), UiTheme.MUTED),
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
        if (action != null && action.startsWith("end:")) {
            UUID first;
            try {
                first = UUID.fromString(action.substring(4));
            } catch (IllegalArgumentException e) {
                return;
            }
            sounds.play(player, "gui-click");
            if (matchService.forceEndMatch(first)) {
                player.sendMessage(Component.text("Match force-ended.", NamedTextColor.GREEN));
            } else {
                sounds.play(player, "error");
                player.sendMessage(Component.text(
                        "Match already ended.", NamedTextColor.RED));
            }
            refresh(player, session, inventory);
        }
    }
}
