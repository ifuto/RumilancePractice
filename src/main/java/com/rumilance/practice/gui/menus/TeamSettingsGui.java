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
 * パーティ設定 (完全リビルド 2026-09-29)。設計思想:
 * <p>「人の画面 = Hub に残す」「ルールの画面 = ここに集約」の一本化。</p>
 * <ul>
 *   <li>row1 — ルール:       公開/非公開 (1,2)・マップ (1,4)・友好ファイア (1,6)</li>
 *   <li>row2 — チーム操作:   サイド白紙 (2,2)・自動分割 (2,4)・上位設定 (2,6)</li>
 *   <li>row3 — 危険:         解散は赤い最遠隅 (3,7) だけ</li>
 *   <li>row4 — 共通:         ハブへ戻る (4,4)。Owner 以外は空画面+バリアのみ</li>
 * </ul>
 * <p>旧画面が抱えていた「やりたい操作が hub と settings に分散」問題は
 * Settings への一本化で解消する(Hub は誰がどちら側かの「人」の可視化に専念)。</p>
 */
public final class TeamSettingsGui extends AbstractGui {

    private final TeamService teamService;
    private TeamHubGui teamHubGui;
    private TeamsBrowserGui browser;
    private TeamKitSelectGui kitSelect;
    private TeamConfigGui teamConfigGui;
    private ConfirmGui confirmGui;
    /** select_map タイルの直 link(キット選択を経ずにマップだけ開いて帰る経路)。 */
    private PartyMapSelectGui directMapSelect;

    public void setDirectMapSelect(PartyMapSelectGui directMapSelect) {
        this.directMapSelect = directMapSelect;
    }

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
        return com.rumilance.practice.gui.GuiFrame.Theme.CYAN;
    }

    @Override
    protected Material titleIcon() {
        return Material.COMPARATOR;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "party-settings-title").color(UiTheme.PRIMARY);
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

        // === row1: ルール（バトルの基本設定、視線の流れは [公開-マップ-FF] ) ===
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

        // === row2: チーム操作(人の編成を整える方) ===
        inventory.setItem(GuiSlots.slot(2, 2),
                ItemBuilder.of(Material.WATER_BUCKET)
                        .name(t(player, "party.clear-sides").color(UiTheme.WARNING))
                        .lore(UiTheme.divider(),
                                UiTheme.hint(line(player, "party.clear-sides-hint")))
                        .action("clearsides").build());
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
        if (teamConfigGui != null) {
            inventory.setItem(GuiSlots.slot(2, 6),
                    ItemBuilder.of(Material.COMMAND_BLOCK)
                            .name(t(player, "gui.team-config-open").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.team-config-open-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.toggle-hint")))
                            .action("open_team_config").build());
        }

        // === row3: 危険系は最遠隅(3,7)だけに置く ===
        inventory.setItem(GuiSlots.slot(3, 7),
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

    /**
     * Reopens the party browser after the disband cue has fully played. That cue is
     * anvil now + item-break 15 ticks later (see {@code TeamService#DISBAND_BREAK_DELAY_TICKS});
     * opening any inventory sooner makes its own open/close sounds land right on the break, so
     * the browser comes back one tick after the break instead.
     */
    private void openBrowserAfterBreak(Player player) {
        if (browser == null) {
            return;
        }
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    if (player.isOnline()) {
                        browser.open(player);
                    }
                },
                16L);
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
                if (!owner) {
                    return;
                }
                sounds.play(player, "gui-click");
                if (directMapSelect != null) {
                    openLater(player, directMapSelect);
                } else {
                    openLater(player, kitSelect);
                }
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
                                openBrowserAfterBreak(who);
                            },
                            who -> {
                                sounds.play(who, "gui-back");
                                openLater(who, this);
                            });
                } else if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    sounds.play(player, "select");
                    teamService.disband(player);
                    player.closeInventory();
                    openBrowserAfterBreak(player);
                } else {
                    player.sendMessage(Component.text(line(player, "party.disband-hint"), UiTheme.DANGER));
                }
            }
            default -> {
            }
        }
    }
}
