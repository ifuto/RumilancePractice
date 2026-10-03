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
    /** RED mockup seats: rows1-4 × cols1-3 minus the (1,2) invite pane (owner view). */
    private static final int RED_SEATS = 11;
    /** BLUE mockup seats: rows1-4 × cols5-7. */
    private static final int BLUE_SEATS = 12;
    /** Unassigned members overlay the light-gray separator column (col 4, rows 1-4). */
    private static final int FREE_SEATS = 4;

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

        renderMockup(player, session, inventory, team, owner, red, blue);
    }

    /**
     * 2026-10 mockup layout (docs/design/gui-mockups.md "Party MAIN GUI"): red band down the
     * left edge, blue band down the right, side-count wools top-left/right around the owner
     * head, ender-pearl Random Split top-right, RED seats cols 1-3 with the lime invite pane
     * at (1,2), BLUE seats cols 5-7, a light-gray separator column (col 4) that doubles as
     * the unassigned strip, and a footer of [back | prev sign | +N overflow | START/LEAVE |
     * settings/close | tournament | next sign].
     */
    private void renderMockup(Player player, GuiSession session, Inventory inventory,
                              Team team, boolean owner, TeamColor red, TeamColor blue) {
        // --- bands + top row ---
        for (int row = 0; row <= 5; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), com.rumilance.practice.gui.GuiMockups
                    .deco(player, Material.RED_STAINED_GLASS_PANE, messageService));
            inventory.setItem(GuiSlots.slot(row, 8), com.rumilance.practice.gui.GuiMockups
                    .deco(player, Material.BLUE_STAINED_GLASS_PANE, messageService));
        }
        for (int col : new int[]{1, 3}) {
            inventory.setItem(GuiSlots.slot(0, col), com.rumilance.practice.gui.GuiMockups
                    .deco(player, Material.RED_STAINED_GLASS_PANE, messageService));
        }
        for (int col : new int[]{5, 7}) {
            inventory.setItem(GuiSlots.slot(0, col), com.rumilance.practice.gui.GuiMockups
                    .deco(player, Material.BLUE_STAINED_GLASS_PANE, messageService));
        }
        inventory.setItem(GuiSlots.slot(0, 4), headerItem(player, team));
        inventory.setItem(GuiSlots.slot(0, 2), sideChip(player, team, red));
        inventory.setItem(GuiSlots.slot(0, 6), sideChip(player, team, blue));
        if (owner) {
            inventory.setItem(GuiSlots.slot(0, 8),
                    ItemBuilder.of(Material.ENDER_PEARL)
                            .name(t(player, "gui.party-auto-split").color(UiTheme.PRIMARY))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.party-auto-split-lore")))
                            .action("auto_split").build());
        }

        // --- member paging state ---
        List<UUID> reds = sideMembers(team, red);
        List<UUID> blues = sideMembers(team, blue);
        List<UUID> free = unassignedMembers(team);
        int page = Math.max(0, session.page());
        int totalPages = Math.max(1, Math.max(
                (reds.size() + RED_SEATS - 1) / RED_SEATS,
                Math.max((blues.size() + BLUE_SEATS - 1) / BLUE_SEATS,
                        (free.size() + FREE_SEATS - 1) / FREE_SEATS)));
        if (page >= totalPages) {
            page = totalPages - 1;
            session.setPage(page);
        }

        // --- separator column (col 4): light-gray panes, unassigned members overlay ---
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(GuiSlots.slot(row, 4), com.rumilance.practice.gui.GuiMockups
                    .accent(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        }
        for (int i = 0; i < FREE_SEATS; i++) {
            int idx = page * FREE_SEATS + i;
            int row = 1 + i;
            if (idx < free.size()) {
                inventory.setItem(GuiSlots.slot(row, 4),
                        memberItem(player, team, free.get(idx), owner));
            }
        }

        // --- RED seats (cols 1-3, invite pane fixed at (1,2) for the owner) ---
        List<int[]> redCells = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 3; col++) {
                if (row == 1 && col == 2 && owner) {
                    continue;
                }
                redCells.add(new int[]{row, col});
            }
        }
        for (int[] cell : redCells) {
            inventory.setItem(GuiSlots.slot(cell[0], cell[1]),
                    com.rumilance.practice.gui.GuiMockups.emptySlot(player, messageService));
        }
        if (owner) {
            inventory.setItem(GuiSlots.slot(1, 2),
                    ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                            .name(t(player, "gui.party-quick-invite").color(UiTheme.SUCCESS))
                            .lore(UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.party-quick-invite-lore")))
                            .action("quick_invite").build());
        }
        for (int i = 0; i < RED_SEATS && page * RED_SEATS + i < reds.size(); i++) {
            int[] cell = redCells.get(i);
            inventory.setItem(GuiSlots.slot(cell[0], cell[1]),
                    memberItem(player, team, reds.get(page * RED_SEATS + i), owner));
        }

        // --- BLUE seats (cols 5-7) ---
        for (int i = 0; i < BLUE_SEATS; i++) {
            int row = 1 + i / 3;
            int col = 5 + i % 3;
            int idx = page * BLUE_SEATS + i;
            inventory.setItem(GuiSlots.slot(row, col), idx < blues.size()
                    ? memberItem(player, team, blues.get(idx), owner)
                    : com.rumilance.practice.gui.GuiMockups.emptySlot(player, messageService));
        }

        // --- footer ---
        int overflow = Math.max(0, reds.size() - page * RED_SEATS - RED_SEATS)
                + Math.max(0, blues.size() - page * BLUE_SEATS - BLUE_SEATS)
                + Math.max(0, free.size() - page * FREE_SEATS - FREE_SEATS);
        inventory.setItem(GuiSlots.slot(5, 3), overflow > 0 ? overflowBadge(overflow)
                : com.rumilance.practice.gui.GuiMockups.emptySlot(player, messageService));
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(UiTheme.BACK)
                        .name(t(player, "menu.back").color(UiTheme.WARNING))
                        .action("close").build());
        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.of(Material.OAK_SIGN)
                        .name(t(player, "menu.page-prev").color(UiTheme.MUTED))
                        .action("page:prev").build());
        inventory.setItem(GuiSlots.slot(5, 6),
                ItemBuilder.of(Material.OAK_SIGN)
                        .name(t(player, "menu.page-next").color(UiTheme.MUTED))
                        .action("page:next").build());
        if (owner) {
            ownerBar(player, team, red, blue, inventory);
        } else {
            inventory.setItem(GuiSlots.slot(5, 4),
                    ItemBuilder.of(Material.OAK_DOOR)
                            .name(t(player, "party.leave").color(UiTheme.WARNING))
                            .lore(UiTheme.hint(line(player, "party.leave-hint")))
                            .action("leave").build());
            inventory.setItem(GuiSlots.slot(5, 5),
                    ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
        }
    }

    /** "+N more members" name tag (the mockup's "No more players" barrier slot). */
    private ItemStack overflowBadge(int overflow) {
        return ItemBuilder.of(Material.NAME_TAG)
                .name(Component.text("+" + overflow, UiTheme.MUTED)
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate").build();
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

    /** Owner footer hero row: START (5,4), settings comparator (5,5), tournament (5,6). */
    private void ownerBar(Player player, Team team, TeamColor red, TeamColor blue, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(5, 5),
                ItemBuilder.of(Material.COMPARATOR)
                        .name(t(player, "gui.team-settings-entry").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.team-settings-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.team-settings-hint")))
                        .action("team_settings").build());
        if (team.kind() == com.rumilance.practice.team.GroupKind.PARTY) {
            inventory.setItem(GuiSlots.slot(5, 6),
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
