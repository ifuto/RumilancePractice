package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.TeamColor;
import com.rumilance.practice.team.Team;
import com.rumilance.practice.team.TeamService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit chooser for a team battle. Opened from the {@link TeamHubGui} "Start Battle" button —
 * clicking a kit immediately launches the RED-vs-BLUE match (no queue). Only the owner of a
 * split-ready team can start; anyone else gets bounced back to the hub.
 */
public final class TeamKitSelectGui extends AbstractGui {

    private final TeamService teamService;
    private final KitService kitService;
    private final com.rumilance.practice.locale.MessageService messageService;
    private PartyMapSelectGui partyMapSelectGui;
    /** Owner's original-kit store (null = original kits unavailable here). */
    private com.rumilance.practice.originalkit.OriginalKitService originalKitService;
    private InnerKitSelectGui innerKitSelectGui;

    public void setOriginalKitService(
            com.rumilance.practice.originalkit.OriginalKitService originalKitService) {
        this.originalKitService = originalKitService;
    }

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService) {
        this(registry, sounds, teamService, kitService, null);
    }

    public TeamKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                            TeamService teamService, KitService kitService,
                            com.rumilance.practice.locale.MessageService messageService) {
        super(registry, sounds, GuiType.TEAM_KIT_SELECT, 6, true);
        this.teamService = teamService;
        this.kitService = kitService;
        this.messageService = messageService;
    }

    /**
     * Party flow: pick a kit here, then pick the party map in {@link PartyMapSelectGui};
     * the match starts when the map is chosen. Wired from bootstrap.
     */
    public void setPartyMapSelectGui(PartyMapSelectGui partyMapSelectGui) {
        this.partyMapSelectGui = partyMapSelectGui;
    }

    /** 中キット (inner kits) — right-click a kit to pick the preset the battle fights with. */
    public void setInnerKitSelectGui(InnerKitSelectGui innerKitSelectGui) {
        this.innerKitSelectGui = innerKitSelectGui;
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.WHITE;
    }

    @Override
    protected Material titleIcon() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.party-kit-title").color(UiTheme.PRIMARY);
    }

    /** Reopen the parent kit list at its original category/page after visiting a child menu. */
    public void openAt(Player player, String category, int page) {
        openWithSession(player, session -> {
            session.setKitCategory(category);
            session.setPage(Math.max(0, page));
        });
    }

    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setKitCategory(null);
        session.setPage(0);
        session.setSelectedKit(null);
        session.put("orig_sel", Boolean.FALSE);
        session.put(InnerKitSelectGui.CHOICE_KEY, null);
    }

    /** バトル開始: START ヒーローボタン直行(選択されたマップはチーム設定のまま利用)。 */
    private void startBattleDirect(Player player, String kitId, String innerKitId) {
        kitId = kitService.playableId(kitId);
        TeamService.Result precheck = teamService.preflightStart(player);
        if (precheck != TeamService.Result.OK) {
            sounds.play(player, "error");
            player.sendMessage(Component.text(teamService.errorMessage(player, precheck), UiTheme.DANGER)
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        sounds.play(player, "gui-click");
        player.closeInventory();
        TeamService.Result r = teamService.start(player, kitId, innerKitId);
        sounds.play(player, r == TeamService.Result.OK ? "match-found" : "error");
        if (r != TeamService.Result.OK) {
            player.sendMessage(Component.text(teamService.errorMessage(player, r), UiTheme.DANGER)
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * マップ確定・中キット確定からの復帰: 選んでいたキットを保持したまま一覧を開き直す。
     */
    public void openResume(Player player, String kitId, String innerKitId) {
        openWithSession(player, session -> {
            if (kitId != null) {
                session.setSelectedKit(kitService.playableId(kitId));
                kitService.get(kitId).ifPresent(k ->
                        session.setKitCategory(k.category().name()));
            }
            if (innerKitId != null
                    && !com.rumilance.practice.kit.InnerKitService.isDefault(innerKitId)) {
                session.put(InnerKitSelectGui.CHOICE_KEY,
                        com.rumilance.practice.kit.InnerKitService.normalizeId(innerKitId));
            }
        });
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);

        Team team = teamService.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.isOwner(player.getUniqueId())) {
            // Only a split-ready team's owner may launch a battle — everyone else sees a
            // locked screen instead of a kit grid they must not act on.
            inventory.setItem(GuiSlots.slot(2, 4),
                    ItemBuilder.of(Material.BARRIER)
                            .name(t(player, "gui.party-owner-only"))
                            .lore(UiTheme.line(line(player, "party.owner-only-lore")))
                            .action("decorate")
                            .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }
        int red = team.side(TeamColor.RED).size();
        int blue = team.side(TeamColor.BLUE).size();
        inventory.setItem(GuiSlots.slot(5, 1),
                ItemBuilder.of(Material.RED_WOOL, Math.max(1, red))
                        .name(t(player, "party.red-vs-blue", MessageService.tags(
                                "red", String.valueOf(red),
                                "blue", String.valueOf(blue))).color(UiTheme.VALUE))
                        .lore(UiTheme.line(line(player, "gui.party-uneven-ok")))
                        .action("decorate").build());

        // Live readiness tile: green glow when the battle can start, otherwise the exact
        // reason (unassigned members, someone queued / in FFA / spectating, ...).
        TeamService.Result precheck = teamService.preflightStart(player);
        boolean ready = precheck == TeamService.Result.OK;
        inventory.setItem(GuiSlots.slot(5, 7),
                ItemBuilder.of(ready ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name(t(player, ready ? "party.status-ready" : "party.status-blocked")
                                .color(ready ? UiTheme.SUCCESS : UiTheme.WARNING))
                        .lore(ready
                                ? UiTheme.line(line(player, "party.status-ready-lore"))
                                : UiTheme.line(teamService.errorMessage(player, precheck)))
                        .glintIf(ready)
                        .action("decorate").build());

        String category = session.kitCategory();
        if (category == null) {
            // 使いやすさ: メイン/サブどちらか空なら 2 択画面は飛ばして直接一覧へ。
            boolean mainEmpty = kitService.enabled(KitCategory.MAIN).isEmpty();
            boolean subEmpty = kitService.enabled(KitCategory.SUB).isEmpty();
            if (mainEmpty != subEmpty) {
                category = mainEmpty ? "SUB" : "MAIN";
                session.setKitCategory(category);
                session.setPage(0);
            }
        }
        if (category == null) {
            // Step 1: the two wooden category buttons (Queue と同じ2択画面)。
            List<KitDefinition> main = kitService.enabled(KitCategory.MAIN);
            List<KitDefinition> sub = kitService.enabled(KitCategory.SUB);
            inventory.setItem(MenuScaffold.gridSlot(9),
                    com.rumilance.practice.gui.KitSections.categoryButton(
                            KitCategory.MAIN,
                            t(player, "gui.kit-main-button").color(UiTheme.SUCCESS),
                            java.util.List.of(
                                    UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.kit-main-button-lore")),
                                    UiTheme.blank(),
                                    UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                            String.valueOf(main.size())),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.party-start-click")))));
            inventory.setItem(MenuScaffold.gridSlot(11),
                    com.rumilance.practice.gui.KitSections.categoryButton(
                            KitCategory.SUB,
                            t(player, "gui.kit-sub-button").color(UiTheme.SECONDARY),
                            java.util.List.of(
                                    UiTheme.divider(),
                                    UiTheme.line(line(player, "gui.kit-sub-button-lore")),
                                    UiTheme.blank(),
                                    UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                            String.valueOf(sub.size())),
                                    UiTheme.blank(),
                                    UiTheme.hint(line(player, "gui.party-start-click")))));
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }

        // Step 2: one category's kit list. Continue onto leftover grid rows so original
        // kits (Owner の Original Kit 星) stay selectable like on the old mixed screen.
        List<KitDefinition> kits = kitService.enabled(
                "SUB".equalsIgnoreCase(category) ? KitCategory.SUB : KitCategory.MAIN);
        List<ItemStack> choices = new ArrayList<>();
        choices.add(com.rumilance.practice.gui.KitSections.header(
                "SUB".equalsIgnoreCase(category) ? KitCategory.SUB : KitCategory.MAIN,
                kits.size(), line(player, "gui.party-start-click")));
        for (KitDefinition kit : kits) {
            choices.add(partyKitTile(player, session, kit));
        }

        // The owner's own original kits stay selectable even with 28+ server kits.
        if (originalKitService != null) {
            com.rumilance.practice.originalkit.OriginalKitService.Plan plan =
                    originalKitService.planOf(player);
            for (int slot = 0; slot < 9; slot++) {
                if (!originalKitService.isSlotUnlocked(plan, slot)
                        || !originalKitService.hasSaved(player.getUniqueId(), slot)) {
                    continue;
                }
                choices.add(ItemBuilder.of(Material.NETHER_STAR)
                        .name(Component.text("Original Kit #" + (slot + 1), UiTheme.HEADER)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(UiTheme.divider(),
                                UiTheme.line(line(player, "gui.party-original-kit-lore")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.party-start-click")))
                        .glint(Boolean.TRUE.equals(session.get("orig_sel", Boolean.class))
                                && team.originalKitSlot() != null
                                && team.originalKitSlot() == slot)
                        .action("origkit:" + slot)
                        .build());
            }
        }
        int perPage = MenuScaffold.gridPageSize();
        int page = Math.min(session.page(), Math.max(0, (choices.size() - 1) / perPage));
        for (int i = 0; i < perPage && page * perPage + i < choices.size(); i++) {
            inventory.setItem(MenuScaffold.gridSlot(i), choices.get(page * perPage + i));
        }
        paintPaging(player, inventory, page, choices.size());

        // [選択 → START 確定] 的新フロー: タップ=選択(光るだけ)、START=合図のクリック。
        // マップを変えたい時だけ 右の MAP チップから選ぶ。バトル開始は START のみ。
        String selectedKit = session.selectedKit();
        boolean origSelected = Boolean.TRUE.equals(session.get("orig_sel", Boolean.class));
        boolean hasSelection = selectedKit != null || origSelected;
        String innerChoice = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);

        inventory.setItem(GuiSlots.slot(5, 2),
                ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
        inventory.setItem(GuiSlots.slot(5, 6),
                ItemBuilder.of(Material.MAP)
                        .name(t(player, "gui.party-map-chip").color(UiTheme.PRIMARY))
                        .lore(UiTheme.divider(),
                                UiTheme.labelValue(line(player, "party.map-label"),
                                        team != null && team.selectedArena() != null
                                                ? com.rumilance.practice.util.NameDisplay
                                                        .pretty(team.selectedArena())
                                                : line(player, "party.random")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.party-map-chip-hint")))
                        .action("open_map").build());
        Component startName;
        java.util.List<Component> startLore;
        if (origSelected) {
            startName = t(player, "gui.party-start-orig").color(ready ? UiTheme.SUCCESS : UiTheme.MUTED);
            startLore = java.util.List.of(UiTheme.divider(),
                    UiTheme.line(line(player, "gui.party-selected-orig")));
        } else if (selectedKit != null) {
            startName = t(player, "gui.party-start-kit", MessageService.tags(
                    "kit", com.rumilance.practice.util.KitNames.pretty(selectedKit)))
                    .color(ready ? UiTheme.SUCCESS : UiTheme.MUTED);
            startLore = new ArrayList<>(java.util.List.of(UiTheme.divider()));
            if (innerChoice != null
                    && !com.rumilance.practice.kit.InnerKitService.isDefault(innerChoice)) {
                ((ArrayList<Component>) startLore).add(UiTheme.line(
                        line(player, "gui.party-selected-inner").replace("<inner>", innerChoice)));
            }
        } else {
            startName = t(player, "gui.party-start").color(UiTheme.MUTED);
            startLore = java.util.List.of(UiTheme.divider(),
                    UiTheme.hint(line(player, "gui.party-pick-first")));
        }
        inventory.setItem(GuiSlots.slot(5, 4),
                ItemBuilder.of(ready && hasSelection ? Material.DIAMOND_SWORD : Material.IRON_SWORD)
                        .name(startName)
                        .lore(startLore.toArray(new Component[0]))
                        .glint(ready && hasSelection)
                        .action("start_battle").build());
    }

    private ItemStack partyKitTile(Player player, GuiSession session, KitDefinition kit) {
        // フォルダの親タイルは元の名前とアイコンを保持。左=既定の子、右=子一覧。
        KitDefinition shown = kitService.tile(kit);
        List<KitDefinition> children = kitService.children(kit.name());
        List<Component> lore = new ArrayList<>(List.of(
                UiTheme.divider(),
                UiTheme.labelValue(line(player, "gui.party-arena"), shown.hasFixedArena()
                        ? com.rumilance.practice.util.KitNames.pretty(shown.arenaName())
                        : line(player, "gui.queue-random"))));
        if (!children.isEmpty()) {
            lore.add(UiTheme.blank());
            lore.add(UiTheme.labelValue(line(player, "gui.innerkit-count-label"),
                    String.valueOf(children.size())));
            lore.add(UiTheme.labelValue(line(player, "gui.innerkit-default-label"),
                    com.rumilance.practice.gui.KitDisplayNames.plain(shown)));
            lore.add(UiTheme.hint(line(player, "gui.innerkit-right-hint")));
        }
        lore.add(UiTheme.blank());
        lore.add(UiTheme.hint(line(player, "gui.party-select-click")));
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(kit.prettyDisplayName())
                .lore(lore.toArray(new Component[0]))
                .glint(kit.name().equals(session.selectedKit()))
                .action("kit:" + kit.name())
                .build();
    }

    /** Folder RIGHT-click opens its child-kit list; LEFT fights with the default child. */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("kit:")
                && innerKitSelectGui != null) {
            String kitId = action.substring("kit:".length());
            if (kitService.isFolder(kitId)) {
                sounds.play(player, "gui-click");
                session.setNavigatingAway(true);
                innerKitSelectGui.openForTeam(player, session, kitId);
                return;
            }
        }
        handleClick(player, session, inventory, slot, action);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if (action.startsWith("page:")) {
            session.setPage(Math.max(0, session.page()
                    + ("page:next".equals(action) ? 1 : -1)));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        switch (action) {
            case "close", "back" -> {
                if (session.kitCategory() != null) {
                    // Back from a category returns to the two wooden buttons, not the hub.
                    session.setKitCategory(null);
                    session.setPage(0);
                    sounds.play(player, "gui-back");
                    refresh(player, session, inventory);
                    return;
                }
                sounds.play(player, "gui-back");
                player.closeInventory();
                player.performCommand("team");
            }
            case "open_map" -> {
                // マップだけ事前に選ぶ(開始はしない)。PartyMapSelect 側で map: クリック = 選択確定。
                sounds.play(player, "gui-click");
                if (partyMapSelectGui == null) {
                    return;
                }
                String selKit = session.selectedKit();
                String inner = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
                session.setNavigatingAway(true);
                if (selKit == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                            () -> { if (player.isOnline()) { partyMapSelectGui.open(player); } });
                } else {
                    final String k = selKit;
                    String in = inner;
                    org.bukkit.Bukkit.getScheduler().runTask(
                            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                            () -> { if (player.isOnline()) { partyMapSelectGui.openForKit(player, k, in); } });
                }
            }
            case "start_battle" -> {
                boolean origSelected = Boolean.TRUE.equals(session.get("orig_sel", Boolean.class));
                Integer origSlot = teamService.teamOf(player.getUniqueId())
                        .map(com.rumilance.practice.team.Team::originalKitSlot).orElse(null);
                if (origSelected && origSlot != null) {
                    proceedWithOriginalKit(player, origSlot);
                } else {
                    String sel = session.selectedKit();
                    if (sel == null) {
                        player.sendMessage(Component.text(
                                line(player, "gui.party-pick-first"), UiTheme.WARNING)
                                .decoration(TextDecoration.ITALIC, false));
                        sounds.play(player, "error");
                        return;
                    }
                    String inner = session.get(InnerKitSelectGui.CHOICE_KEY, String.class);
                    startBattleDirect(player, sel, inner);
                }
            }
            default -> {
                if (action.startsWith("kit:")) {
                    // Selection only — 開始は START ヒーローボタンから(1回の誤タップで始まらない)。
                    String chosen = action.substring("kit:".length());
                    if (!kitService.isFolder(chosen)) {
                        chosen = kitService.playableId(chosen);
                    }
                    session.setSelectedKit(chosen);
                    session.put("orig_sel", Boolean.FALSE);
                    teamService.teamOf(player.getUniqueId())
                            .ifPresent(team -> team.setOriginalKitSlot(null));
                    sounds.play(player, "select");
                    refresh(player, session, inventory);
                } else if (action.startsWith("origkit:")) {
                    // Original kit selected: remember the owner's slot. The match then fights
                    // on that kit alone — loadout AND rules — with no shared match kit at all.
                    int origSlot;
                    try {
                        origSlot = Integer.parseInt(action.substring("origkit:".length()));
                    } catch (NumberFormatException e) {
                        return;
                    }
                    session.setSelectedKit(null);
                    session.put("orig_sel", Boolean.TRUE);
                    teamService.teamOf(player.getUniqueId())
                            .ifPresent(team -> team.setOriginalKitSlot(origSlot));
                    sounds.play(player, "select");
                    refresh(player, session, inventory);
                }
            }
        }
    }

    /**
     * Original-kit selection: validate readiness, then start directly through TeamService with
     * the original kit slot. The owner's kit supplies the whole fight, so no match kit is needed.
     */
    private void proceedWithOriginalKit(Player player, int origSlot) {
        TeamService.Result precheck = teamService.preflightStart(player);
        if (precheck != TeamService.Result.OK) {
            sounds.play(player, "error");
            player.sendMessage(Component.text(teamService.errorMessage(player, precheck), UiTheme.DANGER)
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        sounds.play(player, "gui-click");
        player.closeInventory();
        TeamService.Result r = teamService.startOriginal(player, origSlot);
        sounds.play(player, r == TeamService.Result.OK ? "match-found" : "error");
        if (r != TeamService.Result.OK) {
            player.sendMessage(Component.text(teamService.errorMessage(player, r), UiTheme.DANGER)
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    /**
     * Legacy entry (中キット確定等): 新フローでは「開始」は行わず、選択済み状態で一覧に戻る。
     * START ヒーローボタンからのみバトルが始まる(誤タップ防止 2026-09-29)。
     */
    public void proceedWithKit(Player player, String kitId, String innerKitId) {
        openResume(player, kitId, innerKitId);
    }
}
