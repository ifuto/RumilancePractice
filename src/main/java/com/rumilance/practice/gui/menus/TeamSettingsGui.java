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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

/**
 * Owner-only team RULES panel (ITEM 40b slim-down): membership and battle rules only —
 * invite, visibility, map and friendly fire, plus one door to {@link TeamManageGui} where
 * the operational tools live (team composition, autosplit, side cleanup, disband).
 */
public final class TeamSettingsGui extends AbstractGui {

    private final TeamService teamService;
    private TeamHubGui teamHubGui;
    private PartyInviteGui partyInviteGui;
    private TeamKitSelectGui kitSelect;
    private TeamManageGui teamManageGui;
    private TeamHubGui.ArenaTemplateStoreSupplier arenaStoreSupplier;

    public TeamSettingsGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService) {
        super(registry, sounds, GuiType.TEAM_SETTINGS, 5, true);
        this.teamService = teamService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public void setPartyInviteGui(PartyInviteGui partyInviteGui) {
        this.partyInviteGui = partyInviteGui;
    }

    public void setKitSelect(TeamKitSelectGui kitSelect) {
        this.kitSelect = kitSelect;
    }

    public void setTeamManageGui(TeamManageGui teamManageGui) {
        this.teamManageGui = teamManageGui;
    }

    public void setArenaStoreSupplier(TeamHubGui.ArenaTemplateStoreSupplier arenaStoreSupplier) {
        this.arenaStoreSupplier = arenaStoreSupplier;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMPARATOR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.team-settings-title").color(UiTheme.PRIMARY);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "party.owner-only-lore")))
                            .action("decorate")
                            .build());
            MenuScaffold.returnButton(inventory, t(player, "gui.back-to-hub"));
            return;
        }

        // Row 1 — membership & discovery (three buttons, generous gaps).
        inventory.setItem(GuiSlots.slot(1, 2),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(t(player, "party.invite").color(UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.invite-lore-heads")),
                                UiTheme.line(line(player, "party.invite-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.invite-hint")))
                        .action("invite").build());
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(team.isPublic() ? UiTheme.TOGGLE_ON : UiTheme.TOGGLE_OFF)
                        .name(t(player, team.isPublic() ? "party.public-team" : "party.private-team")
                                .color(team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(UiTheme.divider(),
                                UiTheme.line(team.isPublic()
                                        ? line(player, "party.public-lore")
                                        : line(player, "party.private-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.toggle-hint")))
                        .action("toggle_public").build());
        if (arenaStoreSupplier != null && !arenaStoreSupplier.partyArenas().isEmpty()) {
            inventory.setItem(GuiSlots.slot(1, 6),
                    ItemBuilder.of(Material.MAP)
                            .name(t(player, "party.select-map").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue(line(player, "party.map-label"),
                                            team.selectedArena() == null
                                                    ? line(player, "party.random")
                                                    : com.rumilance.practice.util.NameDisplay
                                                            .pretty(team.selectedArena())),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "party.select-map-hint")))
                            .action("select_map").build());
        }

        // Row 2 — battle rules on the left, the door to the management tools on the right.
        inventory.setItem(GuiSlots.slot(2, 2),
                ItemBuilder.of(team.friendlyFire() ? Material.TNT : Material.SHIELD)
                        .name(t(player, team.friendlyFire() ? "party.ff-on-label" : "party.ff-off-label")
                                .color(team.friendlyFire() ? UiTheme.DANGER : UiTheme.SUCCESS))
                        .lore(UiTheme.divider(),
                                UiTheme.line(team.friendlyFire()
                                        ? line(player, "gui.party-ff-on")
                                        : line(player, "gui.party-ff-off")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("toggle_ff").build());
        if (teamManageGui != null) {
            inventory.setItem(GuiSlots.slot(2, 6),
                    ItemBuilder.of(Material.CHEST)
                            .name(t(player, "gui.team-manage-open").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.team-manage-open-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .action("open_team_manage").build());
        }

        MenuScaffold.returnButton(inventory, t(player, "gui.back-to-hub"));
    }

    private void backToHub(Player player) {
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    if (player.isOnline() && teamHubGui != null) {
                        teamHubGui.open(player);
                    }
                });
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null) {
            player.closeInventory();
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                backToHub(player);
            }
            case "invite" -> {
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                if (partyInviteGui != null) {
                    org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                            () -> {
                                if (player.isOnline()) {
                                    partyInviteGui.openFor(player);
                                }
                            });
                } else {
                    player.closeInventory();
                    player.sendMessage(t(player, "party.invite-unavailable"));
                }
            }
            case "toggle_public" -> {
                if (owner) {
                    teamService.togglePublic(player);
                    sounds.play(player, "gui-click");
                    refresh(player, session, inventory);
                }
            }
            case "toggle_ff" -> {
                if (owner) {
                    TeamService.Result r = teamService.toggleFriendlyFire(player);
                    sounds.play(player, r == TeamService.Result.OK ? "select" : "error");
                    refresh(player, session, inventory);
                }
            }
            case "select_map" -> {
                // Map selection goes through kit selection first: picking a kit opens that
                // kit's party-map list, and choosing a map starts the battle.
                if (!owner || kitSelect == null) {
                    return;
                }
                sounds.play(player, "gui-click");
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> {
                            if (player.isOnline()) {
                                kitSelect.open(player);
                            }
                        });
            }
            case "open_team_manage" -> {
                if (!owner || teamManageGui == null) {
                    return;
                }
                sounds.play(player, "gui-open");
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> {
                            if (player.isOnline()) {
                                teamManageGui.open(player);
                            }
                        });
            }
            default -> {
            }
        }
    }
}
