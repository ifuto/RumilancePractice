package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * パーティ管理 GUI【完全リビルド 2026-09-29。「0 から」再設計版 — 旧 TeamSettingsGui】。
 *
 * <p>旧設定画面は「ルール・破壊系・管理系・マップ…何でもごちゃまぜ」だった。
 * 新設計は 3 つの ri FUNCTION 帯で構成される(機能配置から考え直し):</p>
 * <ul>
 *   <li><b>row1 ルール(バトル設定)</b> — [公開/非公開 (1,2) | マップ (1,4) | 友好ファイア (1,6)]:
 *       毎回試合の互みを変えるものだけを置く</li>
 *   <li><b>row2 運営(メンバー)動作</b> — [サイド白紙 (2,4) | <b>権限移譲 (2,3)</b> 新機能 |
 *       チーム設定パネル (2,5)]: メンバーコントロールをここに集約
 *       (自動分割はハブの日常磴へ一本化 — ⑨ 重複監査により撤去)</li>
 *   <li><b>row3 危険ゾーン</b> — 解散ボタンのみ最遠隅 (3,7) (確認ダイアログ)</li>
 * </ul>
 * <p><b>権限移譲</b> 押下で画面がモード切替(下部が「メンバー選択」一覧になり、
 * クリックした相手にリーダーを渡す)。視線の流れは「上=今日の試合の設定 → 下=危険」。
 * 非 OWNER には鉄壁:CLOSE ボタンのみ(従来どおり)。</p>
 */
public final class TeamSettingsGui extends AbstractGui {

    /** Transfer mode flag (session key). */
    private static final String K_TRANSFER = "transfer_mode";
    /** Danger subscreen flag (session key) — the mockup's red "Danger Settings" panel. */
    private static final String K_DANGER = "danger_mode";

    private final TeamService teamService;
    private TeamHubGui teamHubGui;
    private TeamsBrowserGui browser;
    private TeamKitSelectGui kitSelect;
    private TeamConfigGui teamConfigGui;
    private ConfirmGui confirmGui;
    /** The mockup's BAN List tile (2,6) on the danger screen opens the global ban list. */
    private BanListGui banListGui;
    /** select_map タイルの直 link(キット選択を経ずにマップだけ開いて帰る経路)。 */
    private PartyMapSelectGui directMapSelect;

    public void setDirectMapSelect(PartyMapSelectGui directMapSelect) {
        this.directMapSelect = directMapSelect;
    }

    private TeamHubGui.ArenaTemplateStoreSupplier arenaStoreSupplier;

    public TeamSettingsGui(GuiSessionRegistry registry, SoundService sounds,
                           TeamService teamService) {
        super(registry, sounds, GuiType.TEAM_SETTINGS, 6, true);
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

    public void setBanListGui(BanListGui banListGui) {
        this.banListGui = banListGui;
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
        return t(player, "gui.party-settings-title").color(UiTheme.PRIMARY);
    }

    // ================================================================== render

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

        if (Boolean.TRUE.equals(session.get(K_TRANSFER, Boolean.class))) {
            renderTransferPicker(player, session, inventory, team);
            return;
        }
        if (Boolean.TRUE.equals(session.get(K_DANGER, Boolean.class))) {
            renderDanger(player, inventory, team);
            return;
        }
        renderMain(player, inventory, team);
    }

    /**
     * docs/design/gui.json「Party Config GUI」: three 3-wide vertical bands. Rows 0-2 are
     * light-gray in the outer bands and white in the centre; rows 3-5 flip to white in the
     * outer bands and red in the centre. Tiles: TNT Friendly Fire (1,1), COMPARATOR Team
     * Settings (1,4), LIME_DYE Public Party (1,7), REDSTONE_BLOCK Danger Settings (3,4),
     * NAME_TAG Party ID (4,1), PLAYER_HEAD Player List (4,7), ARROW Back (5,4).
     */
    private void renderMain(Player player, Inventory inventory, Team team) {
        // --- ring (docs/design/gui.json「Party Config GUI」, three 3-wide vertical bands) ---
        //   left band  cols 0-2 : light-gray rows 0-2, white rows 3-5
        //   centre band cols 3-5: white rows 0-2, red/danger rows 3-5
        //   right band cols 6-8 : light-gray rows 0-2, white rows 3-5
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 9; c++) {
                Material pane;
                if (r <= 2) {
                    pane = (c >= 3 && c <= 5)
                            ? Material.WHITE_STAINED_GLASS_PANE
                            : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                } else if (r == 3) {
                    pane = (c >= 3 && c <= 5)
                            ? Material.RED_STAINED_GLASS_PANE
                            : Material.WHITE_STAINED_GLASS_PANE;
                } else if (r == 4) {
                    pane = (c == 3 || c == 5)
                            ? Material.LIGHT_GRAY_STAINED_GLASS_PANE
                            : (c == 4 ? Material.RED_STAINED_GLASS_PANE
                            : Material.WHITE_STAINED_GLASS_PANE);
                } else {
                    pane = (c == 3 || c == 5)
                            ? Material.LIGHT_GRAY_STAINED_GLASS_PANE
                            : Material.WHITE_STAINED_GLASS_PANE;
                }
                inventory.setItem(GuiSlots.slot(r, c), deco(player, pane));
            }
        }

        // --- row1: rule tiles ---
        inventory.setItem(GuiSlots.slot(1, 1),
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
        inventory.setItem(GuiSlots.slot(1, 4),
                ItemBuilder.of(Material.COMPARATOR)
                        .name(t(player, "gui.team-config-open").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.team-config-open-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.toggle-hint")))
                        .action("open_team_config").build());
        inventory.setItem(GuiSlots.slot(1, 7),
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

        // --- row3: danger band only; the party ID chip moved to (4,1) per the mockup ---
        inventory.setItem(GuiSlots.slot(3, 4),
                ItemBuilder.of(Material.REDSTONE_BLOCK)
                        .name(t(player, "gui.party-danger").color(UiTheme.DANGER))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-danger-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("danger_mode").build());

        // --- row4: party ID chip (4,1) + Player List (4,7); row5: Back (5,4) ---
        String shortId = team.id().toString().replace("-", "").substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        inventory.setItem(GuiSlots.slot(4, 1),
                ItemBuilder.of(Material.NAME_TAG)
                        .name(t(player, "party.party-id", MessageService.tags("id", shortId))
                                .color(UiTheme.VALUE))
                        .action("decorate").build());
        inventory.setItem(GuiSlots.slot(4, 7),
                ItemBuilder.of(Material.PLAYER_HEAD)
                        .name(t(player, "gui.party-player-list").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "gui.party-members"),
                                        String.valueOf(team.size())),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("open_hub").build());
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(UiTheme.BACK)
                        .name(t(player, "menu.back").color(UiTheme.WARNING))
                        .action("back").build());
    }

    /**
     * The mockup's red "Danger Settings GUI" (docs/design/gui.json): row 0 solid red, rows 1
     * and 3 edges only, row 2 carries Transfer OWNER (2,2) / Disband Party (2,4) / BAN List
     * (2,6), row 4 solid red, row 5 black 存在しないマス. There is no back arrow — Esc exits.
     */
    private void renderDanger(Player player, Inventory inventory, Team team) {
        // docs/design/gui.json「Danger Settings GUI」: row 0 solid red, rows 1 and 3 are
        // EDGES ONLY (the interior stays empty), row 2 carries the three tiles at cols
        // 2 / 4 / 6 (centred on col 4), row 4 solid red, row 5 black 存在しないマス.
        for (int col = 0; col < 9; col++) {
            inventory.setItem(col, deco(player, Material.RED_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(4, col), deco(player, Material.RED_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(5, col), com.rumilance.practice.gui.GuiMockups
                    .noCell(player, messages()));
        }
        for (int row : new int[]{1, 2, 3}) {
            inventory.setItem(GuiSlots.slot(row, 0), deco(player, Material.RED_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(row, 8), deco(player, Material.RED_STAINED_GLASS_PANE));
        }

        inventory.setItem(GuiSlots.slot(2, 2),
                ItemBuilder.of(Material.BLAZE_ROD)
                        .name(t(player, "party.transfer-title").color(UiTheme.SECONDARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.transfer-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.transfer-hint")))
                        .action("transfer_mode").build());
        inventory.setItem(GuiSlots.slot(2, 4),
                ItemBuilder.of(Material.BARRIER)
                        .name(t(player, "party.disband").color(UiTheme.DANGER))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.disband-confirm-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "party.disband-hint")))
                        .action("disband").build());
        // The mockup's third tile is an OAK_SIGN named BAN List (the plugin's global ban
        // list). It replaces the old clear-sides bucket, which had no mockup cell.
        inventory.setItem(GuiSlots.slot(2, 6),
                ItemBuilder.of(Material.OAK_SIGN)
                        .name(t(player, "gui.party-ban-list").color(UiTheme.WARNING))
                        .lore(UiTheme.divider(),
                                UiTheme.hint(line(player, "menu.click")))
                        .action("banlist").build());
        // NOTE: the mockup has no back arrow — row 4 is nine red panes. Exiting is Esc,
        // which closes the inventory and ends the session.
    }

    private ItemStack deco(Player player, Material material) {
        return com.rumilance.practice.gui.GuiMockups.deco(player, material, messages());
    }

    /** 権限移譲モードの描画: メンバー(自分以外)の選択グリッド + 戻るでメイン画面。 */
    private void renderTransferPicker(Player player, GuiSession session, Inventory inventory, Team team) {
        inventory.setItem(GuiSlots.slot(0, 4),
                ItemBuilder.of(Material.NETHER_STAR)
                        .name(t(player, "party.transfer-title").color(UiTheme.SECONDARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "party.transfer-pick-lore")))
                        .action("decorate").build());
        List<UUID> others = new ArrayList<>();
        for (UUID member : team.members()) {
            if (!member.equals(team.owner())) {
                others.add(member);
            }
        }
        if (others.isEmpty()) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "party.transfer-nobody").color(UiTheme.MUTED))
                            .lore(UiTheme.hint(line(player, "party.transfer-nobody-lore")))
                            .action("decorate").build());
        }
        int page = session.page();
        int perPage = MenuScaffold.gridPageSize();
        int from = Math.min(page * perPage, others.size());
        int to = Math.min(from + perPage, others.size());
        int index = 0;
        for (int i = from; i < to; i++) {
            inventory.setItem(MenuScaffold.gridSlot(index++), head(player, others.get(i)));
        }
        // 戻る=メイン画面へ(逃す板場)
        inventory.setItem(GuiSlots.slot(4, 4),
                ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "transfer_exit"));
    }

    private ItemStack head(Player viewer, UUID member) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(member);
        String name = p.getName() == null ? "?" : p.getName();
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(name, UiTheme.VALUE).decoration(TextDecoration.ITALIC, false))
                .lore(UiTheme.divider(),
                        UiTheme.hint(line(viewer, "party.transfer-pick-lore")))
                .skullOwner(p)
                .action("transfer:" + member)
                .build();
    }

    // ================================================================== helpers

    private void backToHub(Player player) {
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamSettingsGui.class),
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
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamSettingsGui.class),
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
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamSettingsGui.class),
                () -> {
                    if (player.isOnline()) {
                        browser.open(player);
                    }
                },
                16L);
    }

    // ================================================================== clicks

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
            case "back", "close" -> {
                session.put(K_TRANSFER, Boolean.FALSE);
                session.put(K_DANGER, Boolean.FALSE);
                sounds.play(player, "gui-back");
                backToHub(player);
            }
            case "danger_mode" -> {
                if (owner) {
                    session.put(K_DANGER, Boolean.TRUE);
                    sounds.play(player, "gui-open");
                    refresh(player, session, inventory);
                }
            }
            case "danger_exit" -> {
                session.put(K_DANGER, Boolean.FALSE);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
            }
            case "open_hub" -> {
                sounds.play(player, "gui-back");
                backToHub(player);
            }
            case "transfer_exit" -> {
                session.put(K_TRANSFER, Boolean.FALSE);
                session.setPage(0);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
            }
            case "transfer_mode" -> {
                if (!owner) {
                    return;
                }
                session.put(K_TRANSFER, Boolean.TRUE);
                session.setPage(0);
                sounds.play(player, "gui-open");
                refresh(player, session, inventory);
            }
            default -> {
                if (action.startsWith("transfer:")) {
                    if (!owner) {
                        return;
                    }
                    UUID target;
                    try {
                        target = UUID.fromString(action.substring("transfer:".length()));
                    } catch (IllegalArgumentException e) {
                        return;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(target);
                    String name = op.getName();
                    if (name == null) {
                        sounds.play(player, "error");
                        return;
                    }
                    TeamService.Result r = teamService.transferOwnership(player, name);
                    sounds.play(player, r == TeamService.Result.OK ? "match-found" : "error");
                    if (r != TeamService.Result.OK) {
                        player.sendMessage(Component.text(
                                teamService.errorMessage(player, r), UiTheme.DANGER)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                    session.put(K_TRANSFER, Boolean.FALSE);
                    session.put(K_DANGER, Boolean.FALSE);
                    // 旧オーナーは呼び出し元に戻る(管理画面の権限は移譲先へ)
                    player.closeInventory();
                    if (r == TeamService.Result.OK) {
                        player.sendMessage(Component.text(
                                line(player, "party.transfer-done").replace("<player>", name),
                                UiTheme.SUCCESS).decoration(TextDecoration.ITALIC, false));
                    }
                    return;
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
            case "banlist" -> {
                if (!player.hasPermission("rumilance.admin")) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "general.no-permission"));
                    return;
                }
                sounds.play(player, "gui-open");
                banListGui.open(player);
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
        }
    }
}
