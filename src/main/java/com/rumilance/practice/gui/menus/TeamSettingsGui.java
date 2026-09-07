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
 * Owner-only team settings & operations — the single second-level screen of the party GUI.
 * Everything that is not daily wanted lives here (old Settings/Manage screens merged into
 * one, so navigation is always Hub → Settings → optional battle config):
 * <ul>
 *   <li>Row 1 — battle rules: public/private, map selection, friendly fire</li>
 *   <li>Row 2 — team operations: per-team battle settings, autosplit, clear sides</li>
 *   <li>Row 3 — disband (destructive, confirmation dialog)</li>
 * </ul>
 * Invite lives on the hub because it is a daily action; back always returns to the hub.
 */
public final class TeamSettingsGui extends AbstractGui {

    private final TeamService teamService;
    private TeamHubGui teamHubGui;
    private TeamsBrowserGui browser;
    private TeamKitSelectGui kitSelect;
    private TeamConfigGui teamConfigGui;
    private ConfirmGui confirmGui;
    private TeamHubGui.ArenaTemplateStoreSupplier arenaStoreSupplier;

    public TeamSettingsGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService) {
        super(registry, sounds, GuiType.TEAM_SETTINGS, 5, true);
        this.teamService = teamService;
    }

    public void setTeamHubGui(TeamHubGui teamHubGui) {
        this.teamHubGui = teamHubGui;
    }

    public void setBrowser(TeamsBrowserGui browser) {
        this.browser = browser;
    }

    public void setKitSelect(TeamKitSelectGui kitSelect) {
        this.kitSelect = kitSelect;
    }

    public void setTeamConfigGui(TeamConfigGui teamConfigGui) {
        this.teamConfigGui = teamConfigGui;
    }

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
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

        // Row 1 — battle rules.
        inventory.setItem(GuiSlots.slot(1, 2),
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
            inventory.setItem(GuiSlots.slot(1, 4),
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
        inventory.setItem(GuiSlots.slot(1, 6),
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

        // Row 2 — team operations.
        if (teamConfigGui != null) {
            inventory.setItem(GuiSlots.slot(2, 2),
                    ItemBuilder.of(Material.COMMAND_BLOCK)
                            .name(t(player, "gui.team-config-open").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.team-config-open-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .action("open_team_config").build());
        }
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.of(Material.ENDER_PEARL)
                        .name(t(player, "party.autosplit").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.autosplit-lore-1")),
                                UiTheme.line(line(player, "party.autosplit-lore-2")),
                                UiTheme.line(line(player, "party.autosplit-lore-3")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.autosplit-hint")))
                        .action("autosplit").build());
        inventory.setItem(GuiSlots.slot(2, 6),
                ItemBuilder.of(Material.WATER_BUCKET)
                        .name(t(player, "party.clear-sides").color(UiTheme.WARNING))
                        .lore(UiTheme.hint(line(player, "party.clear-sides-hint")))
                        .action("clearsides").build());

        // Row 3 — destructive action, alone on the right, behind a confirmation.
        inventory.setItem(GuiSlots.slot(3, 6),
                ItemBuilder.of(Material.BARRIER)
                        .name(t(player, "party.disband").color(UiTheme.DANGER))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.disband-confirm-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.disband-hint")))
                        .action("disband").build());

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

    private void openLater(Player player, AbstractGui gui) {
        if (gui == null) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    if (player.isOnline()) {
                        gui.open(player);
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
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                openLater(player, kitSelect);
            }
            case "open_team_config" -> {
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-open");
                openLater(player, teamConfigGui);
            }
            case "autosplit" -> {
                if (owner) {
                    TeamService.Result r = teamService.autoAssign(player);
                    sounds.play(player, r == TeamService.Result.OK ? "select" : "error");
                    refresh(player, session, inventory);
                }
            }
            case "clearsides" -> {
                if (owner) {
                    teamService.clearSides(player);
                    sounds.play(player, "gui-click");
                    refresh(player, session, inventory);
                }
            }
            case "disband" -> {
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                if (confirmGui != null) {
                    confirmGui.open(player,
                            t(player, "party.disband-confirm").color(UiTheme.DANGER),
                            java.util.List.of(UiTheme.line(line(player, "party.disband-confirm-lore"))),
                            who -> {
                                sounds.play(who, "select");
                                teamService.disband(who);
                                who.closeInventory();
                                if (browser != null) {
                                    browser.open(who);
                                }
                            },
                            who -> {
                                sounds.play(who, "gui-back");
                                openLater(who, this);
                            });
                } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    sounds.play(player, "select");
                    teamService.disband(player);
                    player.closeInventory();
                    if (browser != null) {
                        browser.open(player);
                    }
                } else {
                    sounds.play(player, "error");
                }
            }
            default -> {
            }
        }
    }
}
