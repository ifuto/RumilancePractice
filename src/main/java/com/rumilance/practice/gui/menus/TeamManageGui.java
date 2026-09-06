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
 * ITEM 40b: the operational half of team ownership, split out of {@link TeamSettingsGui}
 * so the settings screen only carries rules. Four big, well-spaced buttons: team battle
 * configuration, autosplit, clear sides and disband (the destructive one sits alone on the
 * right, behind a confirm dialog).
 */
public final class TeamManageGui extends AbstractGui {

    private final TeamService teamService;
    private TeamSettingsGui teamSettingsGui;
    private TeamsBrowserGui browser;
    private TeamConfigGui teamConfigGui;
    private ConfirmGui confirmGui;

    public TeamManageGui(GuiSessionRegistry registry, SoundService sounds,
                         TeamService teamService) {
        super(registry, sounds, GuiType.TEAM_MANAGE, 5, true);
        this.teamService = teamService;
    }

    public void setTeamSettingsGui(TeamSettingsGui teamSettingsGui) {
        this.teamSettingsGui = teamSettingsGui;
    }

    public void setBrowser(TeamsBrowserGui browser) {
        this.browser = browser;
    }

    public void setTeamConfigGui(TeamConfigGui teamConfigGui) {
        this.teamConfigGui = teamConfigGui;
    }

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.CHEST;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.team-manage-title").color(UiTheme.PRIMARY);
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
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }

        // Row 1 — configuration & composition tools.
        if (teamConfigGui != null) {
            inventory.setItem(GuiSlots.slot(1, 2),
                    ItemBuilder.of(Material.COMMAND_BLOCK)
                            .name(t(player, "gui.team-config-open").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.team-config-open-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .action("open_team_config").build());
        }
        inventory.setItem(GuiSlots.slot(1, 6),
                ItemBuilder.of(Material.ENDER_PEARL)
                        .name(t(player, "party.autosplit").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.autosplit-lore-1")),
                                UiTheme.line(line(player, "party.autosplit-lore-2")),
                                UiTheme.line(line(player, "party.autosplit-lore-3")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.autosplit-hint")))
                        .action("autosplit").build());

        // Row 3 — cleanup on the left, the destructive action alone on the right.
        inventory.setItem(GuiSlots.slot(3, 2),
                ItemBuilder.of(Material.WATER_BUCKET)
                        .name(t(player, "party.clear-sides").color(UiTheme.WARNING))
                        .lore(UiTheme.hint(line(player, "party.clear-sides-hint")))
                        .action("clearsides").build());
        inventory.setItem(GuiSlots.slot(3, 6),
                ItemBuilder.of(Material.BARRIER)
                        .name(t(player, "party.disband").color(UiTheme.DANGER))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.disband-confirm-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.disband-hint")))
                        .action("disband").build());

        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private void backToSettings(Player player) {
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    if (player.isOnline() && teamSettingsGui != null) {
                        teamSettingsGui.open(player);
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
                backToSettings(player);
            }
            case "open_team_config" -> {
                if (!owner || teamConfigGui == null) {
                    return;
                }
                sounds.play(player, "gui-open");
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                        () -> {
                            if (player.isOnline()) {
                                teamConfigGui.open(player);
                            }
                        });
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
                                org.bukkit.Bukkit.getScheduler().runTask(
                                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                                        () -> {
                                            if (who.isOnline()) {
                                                open(who);
                                            }
                                        });
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
