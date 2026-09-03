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
    protected Component title(Player player, GuiSession session) {
        return t(player, "party.browser-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.chrome(inventory);

        // Create buttons (top of content area).
        inventory.setItem(GuiSlots.slot(1, 2),
                ItemBuilder.of(Material.LIME_DYE)
                        .name(t(player, "party.create-public").color(UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.create-public-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("create_public").build());
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(Material.GRAY_DYE)
                        .name(t(player, "party.create-private").color(UiTheme.MUTED))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.create-private-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("create_private").build());
        inventory.setItem(GuiSlots.slot(1, 6),
                ItemBuilder.of(Material.PAPER)
                        .name(t(player, "party.how-title").color(UiTheme.SECONDARY))
                        .lore(
                                UiTheme.line(line(player, "gui.party-how-1")),
                                UiTheme.line(line(player, "gui.party-how-2")),
                                UiTheme.line(line(player, "gui.party-how-3"))
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
            inventory.setItem(MenuScaffold.gridSlot(index++), teamIcon(player, team));
        }
        if (publicTeams.isEmpty()) {
            inventory.setItem(GuiSlots.slot(3, 4),
                    ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS)
                            .name(t(player, "gui.party-none").color(UiTheme.MUTED))
                            .lore(UiTheme.hint(line(player, "gui.party-none-lore")))
                            .action("decorate").build());
        }

        // Paging (standard chrome buttons + page indicator).
        paintPaging(player, inventory, page, Math.max(publicTeams.size(), 1));

        MenuScaffold.closeButton(inventory, t(player, "menu.close"));
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
                teamService.create(player, player.getName() + "'s Team", true);
                teamHubGui.open(player);
            }
            case "create_private" -> {
                sounds.play(player, "select");
                teamService.create(player, player.getName() + "'s Team", false);
                teamHubGui.open(player);
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
