package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
 * パーティ中継 GUI【完全リビルド 2026-09-29. 「0 から」志向版】。
 *
 * <p>旧来は「メンバーが大きな7列グリッドに混在する自己説明の下手な画面」だった。
 * 新設計は「どの画面でも一目で読める 1 枚紙」に揃えている:</p>
 * <ul>
 *   <li>row0 — チーム名のヘッド(中央) + RED/BLUE の人数チップ(左右対称)</li>
 *   <li>rows1-3 — RED ゾーン(列1-3) ↔ BLUE ゾーン(列5-7)。頭の色はサイドと一致し、
 *       どこに誰が居るかが配一覧で読める</li>
 *   <li>row4 — 未割当メンバー専用ベルト(両サイドの境界線上)</li>
 *   <li>row5 — 下段: リーダーは [招待(2)|自動分割(3)|START(4)|設定(6)|閉(8)]、
 *       メンバーは [退場(4)|閉(8)]。必ず左右対称、破壊系は最遠隅</li>
 * </ul>
 * <p>メンバー頭のクリック=サイドを RED→BLUE→未割当 で回す(OWNER のみ)、
 * 右クリック=キック(OWNER のみ、paging は SIDE 別 9 cap で省略)。</p>
 */
public final class TeamHubGui extends AbstractGui {

    private static final TextColor BLUE = TextColor.color(0x55AAFF);
    /** One side column's slot count (rows1-3 × cols1-3 または 5-7). */
    private static final int SIDE_SLOTS = 9;
    /** Unassigned strip slots on row4 (cols1-7). */
    private static final int UNASSIGNED_SLOTS = 7;

    private final TeamService teamService;
    private final TeamsBrowserGui browser;
    private final TeamKitSelectGui kitSelect;
    private final com.rumilance.practice.locale.MessageService messageService;
    private PartyInviteGui partyInviteGui;
    private PartyMapSelectGui partyMapSelectGui;
    private ArenaTemplateStoreSupplier arenaStoreSupplier;
    private com.rumilance.practice.session.PlayerStateManager stateManager;
    private TeamConfigGui teamConfigGui;
    private TeamSettingsGui teamSettingsGui;
    private com.rumilance.practice.gui.menus.TournamentGui tournamentGui;

    public void setTeamConfigGui(TeamConfigGui teamConfigGui) {
        this.teamConfigGui = teamConfigGui;
    }

    public void setTeamSettingsGui(TeamSettingsGui teamSettingsGui) {
        this.teamSettingsGui = teamSettingsGui;
    }

    public void setTournamentGui(com.rumilance.practice.gui.menus.TournamentGui tournamentGui) {
        this.tournamentGui = tournamentGui;
    }

    public void setStateManager(com.rumilance.practice.session.PlayerStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public interface ArenaTemplateStoreSupplier {
        List<com.rumilance.practice.model.ArenaTemplate> partyArenas();
    }

    public TeamHubGui(GuiSessionRegistry registry, SoundService sounds,
                      TeamService teamService, TeamsBrowserGui browser, TeamKitSelectGui kitSelect) {
        this(registry, sounds, teamService, browser, kitSelect, null);
    }

    public TeamHubGui(GuiSessionRegistry registry, SoundService sounds,
                      TeamService teamService, TeamsBrowserGui browser, TeamKitSelectGui kitSelect,
                      com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAM_HUB, 6, true);
        this.teamService = teamService;
        this.browser = browser;
        this.kitSelect = kitSelect;
        this.messageService = messageService;
    }

    public void setPartyInviteGui(PartyInviteGui partyInviteGui) {
        this.partyInviteGui = partyInviteGui;
    }

    public void setPartyMapSelectGui(PartyMapSelectGui partyMapSelectGui) {
        this.partyMapSelectGui = partyMapSelectGui;
    }

    public void setArenaStoreSupplier(ArenaTemplateStoreSupplier arenaStoreSupplier) {
        this.arenaStoreSupplier = arenaStoreSupplier;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.BEACON;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        boolean isTeam = team != null && team.kind() == com.rumilance.practice.team.GroupKind.TEAM;
        return t(player, isTeam ? "party.hub-title-team" : "party.hub-title")
                .color(UiTheme.PRIMARY);
    }

    // ================================================================== render

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null) {
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.COMPASS)
                            .name(t(player, "party.not-in-team").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.not-in-team-lore")))
                            .action("open_browser").build());
            MenuScaffold.closeButton(inventory, t(player, "menu.close"));
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());
        List<TeamColor> activeColors = team.activeColors();
        TeamColor red = !activeColors.isEmpty() ? activeColors.getFirst() : TeamColor.RED;
        TeamColor blue = activeColors.size() >= 2 ? activeColors.get(1) : TeamColor.BLUE;

        // row0: チーム名ヘッド(4) ∥ RED/BLUE チップ(1,2/6,7)
        inventory.setItem(GuiSlots.slot(0, 4), headerItem(player, team));
        inventory.setItem(GuiSlots.slot(0, 2), sideChip(player, team, red));
        inventory.setItem(GuiSlots.slot(0, 6), sideChip(player, team, blue));

        // rows1-3: RED 列 1-3 / BLUE 列 5-7 / row4 未割当ベルト列 1-7
        List<UUID> reds = sideMembers(team, red);
        List<UUID> blues = sideMembers(team, blue);
        List<UUID> free = unassignedMembers(team);
        slotSideGrid(player, inventory, reds, 1, team, owner);
        slotSideGrid(player, inventory, blues, 5, team, owner);
        slotUnassignedStrip(player, inventory, free, team, owner);

        // 側っ端に余る時の「+N」のタグ
        overflowBadge(inventory, GuiSlots.slot(3, 3), reds.size(), SIDE_SLOTS);
        overflowBadge(inventory, GuiSlots.slot(3, 7), blues.size(), SIDE_SLOTS);
        overflowBadge(inventory, GuiSlots.slot(4, 7), free.size(), UNASSIGNED_SLOTS);

        // row5: フッター(Owner: [招待 2 | 自動分割 3 | START 4 | 設定 6 | 閉 8]、他: [退場 4 | 閉 8])
        if (owner) {
            ownerBar(player, team, red, blue, inventory);
        } else {
            inventory.setItem(GuiSlots.slot(5, 4),
                    ItemBuilder.of(Material.OAK_DOOR)
                            .name(t(player, "party.leave").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.leave-hint")))
                            .action("leave").build());
            inventory.setItem(GuiSlots.slot(5, 8),
                    ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
        }
    }

    /** サイド別要素のグリッド配置(rows1-3、cols=3)。 */
    private void slotSideGrid(Player viewer, Inventory inventory, List<UUID> members, int baseCol,
                              Team team, boolean viewerIsOwner) {
        for (int i = 0; i < Math.min(members.size(), SIDE_SLOTS); i++) {
            int row = 1 + i / 3;
            int col = baseCol + i % 3;
            inventory.setItem(GuiSlots.slot(row, col),
                    memberItem(viewer, team, members.get(i), viewerIsOwner));
        }
    }

    /** 未割当ベルト(row4、cols1-7)。 */
    private void slotUnassignedStrip(Player viewer, Inventory inventory, List<UUID> members,
                                     Team team, boolean viewerIsOwner) {
        for (int i = 0; i < Math.min(members.size(), UNASSIGNED_SLOTS); i++) {
            inventory.setItem(GuiSlots.slot(4, 1 + i),
                    memberItem(viewer, team, members.get(i), viewerIsOwner));
        }
    }

    /** 余った人数の +N バッジ(グリッド末尾の角に置く)。 */
    private void overflowBadge(Inventory inventory, int slot, int size, int cap) {
        if (size <= cap) {
            return;
        }
        inventory.setItem(slot, ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("+" + (size - cap), UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate").build());
    }

    /** RED ▸ [[0,2] chip] の人数チップ(装飾のみ)。 */
    private ItemStack sideChip(Player viewer, Team team, TeamColor color) {
        int count = team.side(color).size();
        return ItemBuilder.of(color.wool(), Math.max(1, count))
                .name(Component.text(line(viewer, "party.team-count-chip")
                        .replace("<team>", color.label())
                        .replace("<n>", String.valueOf(count)), color.textColor()))
                .action("decorate").build();
    }

    private List<UUID> sideMembers(Team team, TeamColor color) {
        List<UUID> out = new ArrayList<>();
        for (UUID member : team.members()) {
            if (color == team.sideOf(member)) {
                out.add(member);
            }
        }
        return out;
    }

    private List<UUID> unassignedMembers(Team team) {
        List<UUID> out = new ArrayList<>();
        for (UUID member : team.members()) {
            if (team.sideOf(member) == null) {
                out.add(member);
            }
        }
        return out;
    }

    /** Owner 用のフッター一式。START にはレディネス(実行理由)を lore で。 */
    private void ownerBar(Player player, Team team, TeamColor red, TeamColor blue, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.NETHER_STAR)
                        .name(t(player, "gui.party-quick-invite").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-quick-invite-lore")))
                        .action("quick_invite").build());
        inventory.setItem(GuiSlots.slot(5, 3),
                ItemBuilder.of(Material.ENDER_PEARL)
                        .name(t(player, "gui.party-auto-split").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-auto-split-lore")))
                        .action("auto_split").build());
        inventory.setItem(GuiSlots.slot(5, 6),
                ItemBuilder.of(Material.COMPARATOR)
                        .name(t(player, "gui.team-settings-entry").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.team-settings-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.team-settings-hint")))
                        .action("team_settings").build());
        if (team.kind() == com.rumilance.practice.team.GroupKind.PARTY) {
            inventory.setItem(GuiSlots.slot(5, 1),
                    ItemBuilder.of(Material.GOLDEN_SWORD)
                            .name(t(player, "tournament.hub-button").color(UiTheme.SECONDARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "tournament.hub-button-lore")),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "tournament.hub-button-hint")))
                            .action("open_tournament").build());
        }
        // START ヒーロー: レディネスを lore に明記(未割当/繁忙/準備 Ready)
        int r = team.side(red).size();
        int b = team.side(blue).size();
        int unassigned = team.size() - r - b;
        TeamService.Result precheck = teamService.preflightStart(player);
        boolean ready = precheck == TeamService.Result.OK;
        Component blockedReason = ready ? null
                : UiTheme.line(!team.isSplitReady()
                        ? line(player, "gui.party-need-both")
                        : teamService.errorMessage(player, precheck));
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(Material.DIAMOND_SWORD)
                        .name(t(player, "gui.party-start").color(ready ? UiTheme.SUCCESS : UiTheme.MUTED))
                        .lore(UiTheme.divider(),
                                ready
                                        ? UiTheme.line(line(player, "party.start-ready")
                                                .replace("<red>", String.valueOf(r))
                                                .replace("<blue>", String.valueOf(b)))
                                        : blockedReason,
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.party-start-hint")))
                        .glintIf(ready)
                        .action("choose_kit").build());
        inventory.setItem(GuiSlots.slot(5, 8),
                ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
    }

    private ItemStack headerItem(Player viewer, Team team) {
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(team.owner());
        java.util.List<Component> lore = new ArrayList<>(java.util.List.of(
                UiTheme.divider(),
                UiTheme.labelValue(line(viewer, "gui.party-owner"),
                        ownerPlayer.getName() == null ? "?" : ownerPlayer.getName()),
                UiTheme.labelValue(line(viewer, "gui.party-members"), team.size() + "/30")));
        int pending = teamService.pendingInviteCount(viewer.getUniqueId());
        if (pending > 0) {
            lore.add(UiTheme.labelValue(line(viewer, "party.invite-pending-label"),
                    String.valueOf(pending)));
        }
        lore.add(UiTheme.status(team.isPublic()
                        ? line(viewer, "gui.party-public")
                        : line(viewer, "gui.party-private"),
                team.isPublic() ? UiTheme.SUCCESS : UiTheme.MUTED));
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text(team.name(), UiTheme.HEADER))
                .lore(lore.toArray(Component[]::new))
                .skullOwner(ownerPlayer)
                .action("decorate")
                .build();
    }

    private ItemStack memberItem(Player viewer, Team team, UUID member, boolean viewerIsOwner) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(member);
        TeamColor side = team.sideOf(member);
        String name = p.getName() == null ? "?" : p.getName();
        TextColor nameColor = side == null ? UiTheme.MUTED : side.textColor();
        ItemBuilder b = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Component.text((team.isOwner(member) ? "★ " : "") + name, nameColor))
                .lore(UiTheme.divider(),
                        UiTheme.labelValue(line(viewer, "gui.party-side"),
                                side == null ? line(viewer, "gui.party-unassigned") : side.name()));
        String busyState = busyStateKey(member);
        if (busyState != null) {
            b.lore(UiTheme.status(line(viewer, busyState), UiTheme.WARNING));
        }
        if (team.isOwner(member)) {
            b.lore(UiTheme.status(line(viewer, "gui.party-owner"), UiTheme.SECONDARY));
        }
        if (viewerIsOwner) {
            b.lore(UiTheme.blank(), UiTheme.hint(line(viewer, "gui.party-click-cycle")));
            if (!team.isOwner(member)) {
                b.lore(UiTheme.hint(line(viewer, "party.right-kick")));
            }
        }
        return b.skullOwner(p).action("member:" + member).build();
    }

    /** 忙しさのキー(queue / FFA / match / spectate)、SOON の予定通知用。 */
    private String busyStateKey(UUID member) {
        if (stateManager == null) {
            return null;
        }
        Player online = Bukkit.getPlayer(member);
        if (online == null) {
            return null;
        }
        com.rumilance.practice.state.PlayerState state = stateManager.getState(member);
        return switch (state) {
            case QUEUED_RANKED -> "menu.state-ranked-queue";
            case QUEUED_UNRANKED -> "menu.state-unranked-queue";
            case FIGHTING, PREPARING_MATCH, COUNTDOWN, ENDING -> "menu.state-fighting";
            case SPECTATING -> "menu.state-spectating";
            case FFA -> "menu.state-ffa";
            case EDITING_KIT -> "menu.state-editing";
            case REQUESTING_DUEL -> "menu.state-dueling";
            case PRACTICE_WAIT, PRACTICE_ACTIVE -> "menu.state-fighting";
            default -> null;
        };
    }

    // ================================================================== clicks

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || "open_browser".equals(action)) {
            player.closeInventory();
            browser.open(player);
            return;
        }
        boolean owner = team.isOwner(player.getUniqueId());
        switch (action) {
            case "close", "back" -> {
                sounds.play(player, "gui-back");
                player.closeInventory();
            }
            case "leave" -> {
                sounds.play(player, "select");
                teamService.leave(player);
                player.closeInventory();
                browser.open(player);
            }
            case "quick_invite" -> {
                if (!owner || partyInviteGui == null) {
                    return;
                }
                sounds.play(player, "gui-open");
                Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamHubGui.class),
                        () -> {
                            if (player.isOnline()) {
                                partyInviteGui.openFor(player);
                            }
                        });
            }
            case "auto_split" -> {
                if (!owner) {
                    return;
                }
                TeamService.Result result = teamService.autoAssign(player);
                sounds.play(player, result == TeamService.Result.OK ? "gui-click" : "error");
                if (result != TeamService.Result.OK) {
                    player.sendMessage(Component.text(
                            teamService.errorMessage(player, result), UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                }
                refresh(player, session, inventory);
            }
            case "team_settings" -> {
                if (!owner || teamSettingsGui == null) {
                    return;
                }
                sounds.play(player, "gui-open");
                Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamHubGui.class),
                        () -> {
                            if (player.isOnline()) {
                                teamSettingsGui.open(player);
                            }
                        });
            }
            case "open_tournament" -> {
                if (!owner) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "gui.party-owner-only"));
                    return;
                }
                sounds.play(player, "gui-open");
                Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamHubGui.class),
                        () -> {
                            if (player.isOnline() && tournamentGui != null) {
                                tournamentGui.open(player);
                            }
                        });
            }
            case "choose_kit" -> {
                if (!owner) {
                    sounds.play(player, "error");
                    player.sendMessage(t(player, "gui.party-owner-only"));
                    return;
                }
                TeamService.Result precheck = teamService.preflightStart(player);
                if (precheck != TeamService.Result.OK) {
                    sounds.play(player, "error");
                    player.sendMessage(Component.text(
                            teamService.errorMessage(player, precheck), UiTheme.DANGER)
                            .decoration(TextDecoration.ITALIC, false));
                    refresh(player, session, inventory);
                    return;
                }
                sounds.play(player, "gui-click");
                Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(TeamHubGui.class),
                        () -> {
                            if (player.isOnline()) {
                                kitSelect.open(player);
                            }
                        });
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
                if (action.startsWith("member:") && owner) {
                    UUID target;
                    try {
                        target = UUID.fromString(action.substring("member:".length()));
                    } catch (IllegalArgumentException e) {
                        return;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(target);
                    String name = op.getName();
                    if (name == null) {
                        sounds.play(player, "error");
                        return;
                    }
                    TeamService.Result r;
                    if (click == ClickType.RIGHT || click == ClickType.SHIFT_LEFT
                            || click == ClickType.SHIFT_RIGHT) {
                        r = teamService.kick(player, name);
                    } else {
                        r = teamService.cycleSide(player, name);
                    }
                    sounds.play(player, r == TeamService.Result.OK ? "gui-click" : "error");
                    refresh(player, session, inventory);
                }
            }
        }
    }
}
