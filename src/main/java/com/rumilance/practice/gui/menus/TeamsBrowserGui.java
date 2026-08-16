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

    private final TeamService teamService;
    private TeamHubGui teamHubGui;

    public void setHub(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public TeamsBrowserGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService, TeamHubGui teamHubGui) {
        super(registry, sounds, GuiType.TEAMS_BROWSER, 6, true);
        this.teamService = teamService;
        this.teamHubGui = teamHubGui;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return Component.text("✦ Teams", UiTheme.PRIMARY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        // Create buttons (top of content area).
        inventory.setItem(GuiSlots.slot(1, 2),
                ItemBuilder.of(Material.LIME_DYE)
                        .name(Component.text("Create Public Team", UiTheme.SUCCESS))
                        .lore(UiTheme.hint("Anyone can join without an invite"))
                        .action("create_public").build());
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(Material.GRAY_DYE)
                        .name(Component.text("Create Private Team", UiTheme.MUTED))
                        .lore(UiTheme.hint("Invite-only — only invited players can join"))
                        .action("create_private").build());
        inventory.setItem(GuiSlots.slot(1, 6),
                ItemBuilder.of(Material.PAPER)
                        .name(Component.text("How teams work", UiTheme.SECONDARY))
                        .lore(
                                UiTheme.line("Owner invites players and splits RED/BLUE."),
                                UiTheme.line("Public teams: anyone can join."),
                                UiTheme.line("Private teams: invite required."),
                                UiTheme.line("Start a battle with /team start <kit>.")
                        )
                        .action("decorate").build());

        // Public teams grid (rows 2-4 of the standard content grid), paged.
        int page = session.page();
        var publicTeams = teamService.publicTeams();
        int perPage = 21; // grid rows 2-4 (the create buttons occupy row 1)
        int from = Math.min(page * perPage, publicTeams.size());
        int to = Math.min(from + perPage, publicTeams.size());
        int index = 7; // skip the first grid row (7 slots) used by the create buttons
        for (int i = from; i < to; i++) {
            Team team = publicTeams.get(i);
            inventory.setItem(MenuScaffold.gridSlot(index++), teamIcon(team));
        }
        if (publicTeams.isEmpty()) {
            inventory.setItem(GuiSlots.slot(3, 4),
                    ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS)
                            .name(Component.text("No public teams yet", UiTheme.MUTED))
                            .lore(UiTheme.hint("Create one with the buttons above!"))
                            .action("decorate").build());
        }

        // Paging.
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(5, 2), ItemBuilder.of(Material.ARROW)
                    .name(Component.text("◀ Previous", UiTheme.PRIMARY)).action("page_prev").build());
        }
        if (to < publicTeams.size()) {
            inventory.setItem(GuiSlots.slot(5, 6), ItemBuilder.of(Material.ARROW)
                    .name(Component.text("Next ▶", UiTheme.PRIMARY)).action("page_next").build());
        }

        MenuScaffold.closeButton(inventory);
    }

    private ItemStack teamIcon(Team team) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(team.owner());
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(team.name(), UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue("Owner", owner.getName() == null ? "?" : owner.getName()),
                        UiTheme.labelValue("Members", team.size() + "/30"),
                        UiTheme.status("PUBLIC", UiTheme.SUCCESS),
                        UiTheme.blank(),
                        UiTheme.hint("Click to join")
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
                teamService.create(player, player.getName() + "'s Team", true);
                teamHubGui.open(player);
            }
            case "create_private" -> {
                sounds.play(player, "select");
                teamService.create(player, player.getName() + "'s Team", false);
                teamHubGui.open(player);
            }
            case "page_prev" -> {
                session.setPage(session.page() - 1);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            case "page_next" -> {
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
                        refresh(player, session, inventory);
                    }
                }
            }
        }
    }
}
