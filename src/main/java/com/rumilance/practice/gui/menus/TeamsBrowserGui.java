package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Landing GUI when the player is not in a team. Lists public teams they can join and offers a
 * "create team" button. Private teams do not appear here (players must be invited first).
 */
public final class TeamsBrowserGui extends AbstractGui {

    /** Interior grid seats for party tiles: rows 1-4 × cols 1-7. */
    private static final int GRID_SEATS = 28;

    private final TeamService teamService;
    private TeamHubGui teamHubGui;
    private final com.rumilance.practice.locale.MessageService messageService;

    public void setHub(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public TeamsBrowserGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService, TeamHubGui teamHubGui) {
        this(registry, sounds, teamService, teamHubGui, null);
    }

    public TeamsBrowserGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService, TeamHubGui teamHubGui,
                           com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAMS_BROWSER, 6, true);
        this.teamService = teamService;
        this.teamHubGui = teamHubGui;
        this.messageService = messageService;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "party.browser-title").color(UiTheme.PRIMARY);
    }

    /**
     * 2026-10 mockup (docs/design/gui-mockups.md "Party setfunc-item-main GUI"): yellow
     * frame with a white 装飾 notch top-right, public parties as owner heads filling the
     * interior grid, gray "No party available" panes in every free seat and down the right
     * edge, and the footer [clock Update Data | prev arrow | next arrow | writable book
     * Create a Party] with the two secondary create options folded beside the book.
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        // docs/design/gui.json「Party setfunc-item-main GUI」row 0: white corners with
        // yellow across the middle — (0,0) and (0,8), not a single notch at col 7.
        inventory.setItem(0, com.rumilance.practice.gui.GuiMockups.deco(player,
                Material.WHITE_STAINED_GLASS_PANE, messageService));
        for (int col = 1; col <= 7; col++) {
            inventory.setItem(col, ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                    .action("decorate").build());
        }
        inventory.setItem(8, com.rumilance.practice.gui.GuiMockups.deco(player,
                Material.WHITE_STAINED_GLASS_PANE, messageService));
        // Rows 1-4: yellow down BOTH edges (the right edge is yellow, not a No-party pane).
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                    .action("decorate").build());
            inventory.setItem(GuiSlots.slot(row, 8), ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                    .action("decorate").build());
        }

        var publicTeams = teamService.publicTeams();
        int page = Math.max(0, session.page());
        int perPage = GRID_SEATS;
        int totalPages = Math.max(1, (publicTeams.size() + perPage - 1) / perPage);
        if (page >= totalPages) {
            page = totalPages - 1;
            session.setPage(page);
        }
        // Interior grid cols 1-7, rows 1-4: parties first, gray No-party panes elsewhere.
        for (int i = 0; i < GRID_SEATS; i++) {
            int row = 1 + i / 7;
            int col = 1 + i % 7;
            int idx = page * perPage + i;
            inventory.setItem(GuiSlots.slot(row, col), idx < publicTeams.size()
                    ? teamIcon(player, publicTeams.get(idx))
                    : noParty(player));
        }

        // Footer (docs/design/gui.json): clock Update Data (5,0), prev arrow (5,2),
        // next arrow (5,6), Create a Party book (5,8), yellow panes everywhere else.
        for (int col : new int[]{1, 3, 4, 5, 7}) {
            inventory.setItem(GuiSlots.slot(5, col), ItemBuilder.of(Material.YELLOW_STAINED_GLASS_PANE)
                    .action("decorate").build());
        }
        inventory.setItem(GuiSlots.slot(5, 0),
                ItemBuilder.of(Material.CLOCK)
                        .name(t(player, "gui.update-data").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(), UiTheme.hint(line(player, "menu.click")))
                        .action("update_data").build());
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(UiTheme.BACK)
                        .name(t(player, "menu.page-prev").color(UiTheme.WARNING))
                        .action("page:prev").build());
        inventory.setItem(GuiSlots.slot(5, 6),
                ItemBuilder.of(UiTheme.NEXT_PAGE)
                        .name(t(player, "menu.page-next").color(UiTheme.WARNING))
                        .action("page:next").build());
        // The mockup carries a single "Create a Party" book. The public/private pair is
        // folded into it: the Team concept is retired and every new group is a public
        // party, so one tile is all there is to offer.
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.of(Material.WRITABLE_BOOK)
                        .name(t(player, "party.create-party").color(UiTheme.SECONDARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.create-public-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .glint(true)
                        .action("create_public").build());
    }

    /** The mockup's gray "No party available" filler pane. */
    private ItemStack noParty(Player player) {
        return ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(t(player, "gui.party-none").color(UiTheme.MUTED))
                .lore(UiTheme.hint(line(player, "gui.party-none-lore")))
                .action("decorate").build();
    }

    private ItemStack teamIcon(Player player, Team team) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(team.owner());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(team.name(), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "party.owner"), owner.getName() == null ? "?" : owner.getName()),
                        UiTheme.labelValue(line(player, "party.members"), team.size() + "/30"),
                        UiTheme.status(line(player, "party.public-status"), UiTheme.SUCCESS),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "party.join-hint"))
                )
                .skullOwner(owner)
                .action("join:" + team.id())
                .build();
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType click) {
        switch (action) {
            case "close" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "create_public" -> {
                sounds.play(player, "select");
                teamService.create(player, player.getName() + "'s Party", true,
                        com.rumilance.practice.team.GroupKind.PARTY);
                teamHubGui.open(player);
            }
            case "create_private" -> {
                sounds.play(player, "select");
                teamService.create(player, player.getName() + "'s Party", false,
                        com.rumilance.practice.team.GroupKind.PARTY);
                teamHubGui.open(player);
            }
            case "update_data" -> {
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "page:prev" -> {
                session.setPage(session.page() - 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "page:next" -> {
                session.setPage(session.page() + 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            default -> {
                if (action.startsWith("join:")) {
                    String id = action.substring("join:".length());
                    Team team;
                    try {
                        team = teamService.byId(java.util.UUID.fromString(id)).orElse(null);
                    } catch (IllegalArgumentException e) {
                        team = null;
                    }
                    if (team == null) {
                        sounds.play(player, "error");
                        refresh(player, session, inventory);
                        return;
                    }
                    var r = teamService.join(player, team.name());
                    sounds.play(player, r == TeamService.Result.OK ? "select" : "error");
                    if (r == TeamService.Result.OK) {
                        teamHubGui.open(player);
                    } else {
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                                teamService.errorMessage(player, r), UiTheme.DANGER)
                                .decoration(TextDecoration.ITALIC, false));
                        refresh(player, session, inventory);
                    }
                }
            }
        }
    }
}
