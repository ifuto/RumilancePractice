package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.InnerKitService;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 中メニュー (sub-menu) picker — the list of CHILD KITS a right-click on a folder kit opens in
 * Duel Request, Party Fight and Kit Edit.
 *
 * <p>A child kit is a normal kit in every respect (its own name, icon, contents, rules, personal
 * layout and stats); it only differs in that it is not listed on the top level — you reach it
 * through its parent's tile. The child the admin marked as the folder's {@code default-child} is
 * badged {@code [Default]} and is what the parent's own tile uses.</p>
 *
 * <p>Where the choice goes depends on the screen that opened this one ({@code session "origin"}):
 * a duel carries the child into the duel request, a party battle carries it into the team match,
 * and the kit editor opens that child's layout for editing. Queue never comes here — clicking a
 * folder in the queue GUI fights its default child, exactly as a plain kit would.</p>
 */
public final class InnerKitSelectGui extends AbstractGui {

    /** Session key holding which screen opened this picker. */
    public static final String ORIGIN_KEY = "innerkit-origin";
    /** Legacy key used by old requests. Child choices now travel as normal kit ids. */
    public static final String CHOICE_KEY = "innerkit";

    public static final String ORIGIN_DUEL = "duel";
    public static final String ORIGIN_TEAM = "team";
    public static final String ORIGIN_EDIT = "edit";
    public static final String ORIGIN_VIEW = "view";

    private final KitService kitService;
    private KitSelectGui kitSelectGui;
    private DuelRequestGui duelRequestGui;
    private TeamKitSelectGui teamKitSelectGui;
    private EkitSelectGui ekitSelectGui;
    private EditKitGui editKitGui;
    private InnerKitAdminGui adminGui;

    public void setAdminGui(InnerKitAdminGui adminGui) {
        this.adminGui = adminGui;
    }

    public InnerKitSelectGui(GuiSessionRegistry registry, SoundService sounds,
                             KitService kitService) {
        super(registry, sounds, GuiType.INNER_KIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setKitSelectGui(KitSelectGui kitSelectGui) {
        this.kitSelectGui = kitSelectGui;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void setTeamKitSelectGui(TeamKitSelectGui teamKitSelectGui) {
        this.teamKitSelectGui = teamKitSelectGui;
    }

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    /** Right-click in the duel kit picker: choose the ordinary child kit. */
    public void openForDuel(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_DUEL);
        if (session == null) {
            return;
        }
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFirstTo(parent.firstTo());
        session.setFromBattleMenu(parent.fromBattleMenu());
        finish(player, session);
    }

    /** Right-click in the party kit picker: choose the ordinary child kit. */
    public void openForTeam(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_TEAM);
        if (session == null) {
            return;
        }
        finish(player, session);
    }

    /** Right-click in /ekit: each child opens the ordinary personal kit editor. */
    public void openForEdit(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_EDIT);
        if (session != null) {
            finish(player, session);
        }
    }

    /** Hidden tester viewing another player's kit — children must stay read-only too. */
    public void openForViewer(Player player, GuiSession parent, String kitId) {
        GuiSession session = begin(player, parent, kitId, ORIGIN_VIEW);
        if (session != null && parent != null) {
            session.setTargetPlayer(parent.targetPlayer());
            session.put("viewer-target-name", parent.get("viewer-target-name", String.class));
            finish(player, session);
        }
    }

    private GuiSession begin(Player player, GuiSession parent, String kitId, String origin) {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put(ORIGIN_KEY, origin);
        session.setSelectedKit(kitId);
        session.setPage(0);
        if (parent != null) {
            // Remember where "back" returns to, including the parent's kit-list page.
            session.setKitCategory(parent.kitCategory());
            session.put("origin-page", parent.page());
            // Show the caller's current choice as selected (a reopen must not look reset): the
            // caller's session kit is a child of this folder once one has been picked.
            String chosen = parent.selectedKit();
            if (chosen != null && kitService.get(chosen)
                    .map(k -> kitId.equalsIgnoreCase(k.parent())).orElse(false)) {
                session.put(CHOICE_KEY, chosen);
            }
        }
        return session;
    }

    private void finish(Player player, GuiSession session) {
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.CHEST;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        String name = kit == null ? KitNames.pretty(session.selectedKit())
                : com.rumilance.practice.gui.KitDisplayNames.plain(kit);
        return t(player, "gui.innerkit-title").color(UiTheme.PRIMARY)
                .append(Component.text(" " + name, UiTheme.VALUE));
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        String kitId = session.selectedKit();
        KitDefinition kit = kitId == null ? null : kitService.get(kitId).orElse(null);
        if (kit == null) {
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.kit-none").color(UiTheme.DANGER))
                    .action("decorate")
                    .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }
        // Player-facing menu mirrors an ordinary kit list: disabled children are only shown
        // in the admin manager, not offered as duel/party/edit choices.
        List<KitDefinition> children = kitService.children(kitId).stream()
                .filter(KitDefinition::enabled).toList();
        if (children.isEmpty()) {
            // フォルダではなくなった(子を全部消した)キット: 開く意味が無いので親へ戻す。
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.kit-none").color(UiTheme.DANGER))
                    .action("decorate")
                    .build());
            MenuScaffold.returnButton(inventory, t(player, "menu.back"));
            return;
        }
        String defaultChild = kitService.defaultChild(kitId)
                .map(KitDefinition::name).orElse(null);
        String current = kitService.playableId(session.get(CHOICE_KEY, String.class) == null
                ? kitId : session.get(CHOICE_KEY, String.class));
        int perPage = MenuScaffold.gridPageSize();
        int page = Math.min(session.page(), Math.max(0, (children.size() - 1) / perPage));
        for (int index = 0; index < perPage && page * perPage + index < children.size(); index++) {
            KitDefinition child = children.get(page * perPage + index);
            inventory.setItem(MenuScaffold.gridSlot(index), childTile(player, session, child,
                    child.name().equalsIgnoreCase(defaultChild),
                    child.name().equalsIgnoreCase(current)));
        }
        if (ORIGIN_EDIT.equals(session.get(ORIGIN_KEY, String.class))
                && player.hasPermission("rumilance.admin") && adminGui != null) {
            inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(5, 7),
                    ItemBuilder.of(Material.SHULKER_BOX)
                            .name(t(player, "gui.innerkit-admin-title").color(UiTheme.SECONDARY))
                            .action("manage").build());
        }
        paintPaging(player, inventory, page, children.size());
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /**
     * One child kit. The folder's {@code default-child} carries the {@code [Default]} badge: it is
     * what the parent's own tile fights with, and what Queue always uses.
     */
    private ItemStack childTile(Player player, GuiSession session, KitDefinition child,
                                boolean isDefault, boolean selected) {
        Material icon = Material.matchMaterial(child.icon());
        String label = child.prettyDisplayName()
                + (isDefault ? " " + InnerKitService.DEFAULT_BADGE : "");
        List<Component> lore = new ArrayList<>(List.of(
                UiTheme.divider(),
                UiTheme.line(line(player, originLoreKey(session)))));
        if (isDefault) {
            lore.add(UiTheme.blank());
            lore.add(UiTheme.status(line(player, "gui.innerkit-default-lore"), UiTheme.SUCCESS));
        }
        lore.add(UiTheme.blank());
        lore.add(UiTheme.labelValue(line(player, "gui.innerkit-id-label"), child.name()));
        lore.add(UiTheme.blank());
        lore.add(selected
                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                : UiTheme.hint(line(player, "gui.innerkit-click-select")));
        // 表示名は MiniMessage を通す(色や装飾を書いたキット名でもそのまま出る)。色を自分で
        // 指定しているキット名はそれを優先し、無ければ選択状態の色を付ける。
        Component name = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(label)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .colorIfAbsent(selected ? UiTheme.SUCCESS : UiTheme.VALUE);
        return ItemBuilder.of(icon == null ? Material.DIAMOND_SWORD : icon)
                .name(name)
                .lore(lore.toArray(new Component[0]))
                .glint(selected)
                .action("pick:" + child.name())
                .build();
    }

    /** One hint line per origin, so the list says what the click will do. */
    private String originLoreKey(GuiSession session) {
        String origin = session.get(ORIGIN_KEY, String.class);
        if (ORIGIN_TEAM.equals(origin)) {
            return "gui.innerkit-team-lore";
        }
        if (ORIGIN_EDIT.equals(origin)) {
            return "gui.innerkit-edit-lore";
        }
        if (ORIGIN_VIEW.equals(origin)) {
            return "gui.kit-view-only";
        }
        return "gui.innerkit-duel-lore";
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            sounds.play(player, "gui-back");
            backToOrigin(player, session);
            return;
        }
        if ("page:prev".equals(action) || "page:next".equals(action)) {
            int pages = Math.max(1, (kitService.children(session.selectedKit()).size()
                    + MenuScaffold.gridPageSize() - 1) / MenuScaffold.gridPageSize());
            session.setPage(Math.min(pages - 1, Math.max(0,
                    session.page() + ("page:next".equals(action) ? 1 : -1))));
            refresh(player, session, inventory);
            return;
        }
        if ("manage".equals(action) && player.hasPermission("rumilance.admin")
                && adminGui != null) {
            session.setNavigatingAway(true);
            adminGui.open(player, session.selectedKit(), InnerKitAdminGui.ORIGIN_EKIT);
            return;
        }
        if (!action.startsWith("pick:")) {
            return;
        }
        String chosen = action.substring("pick:".length()).toLowerCase(Locale.ROOT);
        if (kitService.get(chosen).filter(KitDefinition::enabled)
                .filter(k -> session.selectedKit().equalsIgnoreCase(k.parent())).isEmpty()) {
            sounds.play(player, "error");
            return;
        }
        String origin = session.get(ORIGIN_KEY, String.class);
        sounds.play(player, "select");
        switch (origin == null ? ORIGIN_DUEL : origin) {
            case ORIGIN_TEAM -> {
                player.closeInventory();
                if (teamKitSelectGui != null) {
                    teamKitSelectGui.proceedWithKit(player, chosen, null);
                }
            }
            case ORIGIN_EDIT -> {
                if (editKitGui != null) {
                    session.setNavigatingAway(true);
                    editKitGui.openKitEditor(player, chosen);
                } else {
                    player.closeInventory();
                }
            }
            case ORIGIN_VIEW -> {
                if (editKitGui != null && session.targetPlayer() != null) {
                    session.setNavigatingAway(true);
                    editKitGui.openKitViewer(player, session.targetPlayer(),
                            session.get("viewer-target-name", String.class), chosen);
                } else {
                    player.closeInventory();
                }
            }
            default -> returnToDuel(player, session, chosen, null);
        }
    }

    /** Duel: reopen the request GUI with the selected CHILD KIT on the fresh session. */
    private void returnToDuel(Player player, GuiSession session, String kitId, String inner) {
        Player target = session.targetPlayer() == null
                ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        boolean ranked = session.ranked();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean fromBattle = session.fromBattleMenu();
        player.closeInventory();
        // The child id is on the session before the first render and starts that kit.
        duelRequestGui.openFor(player, target, ranked, kitId, map, bestOf, inner);
        registry.get(player.getUniqueId()).ifPresent(fresh -> fresh.setFromBattleMenu(fromBattle));
    }

    /** Back returns to the picker that opened this one, on the category page the player was on. */
    private void backToOrigin(Player player, GuiSession session) {
        String origin = session.get(ORIGIN_KEY, String.class);
        String category = session.kitCategory();
        Integer previousPage = session.get("origin-page", Integer.class);
        int page = previousPage == null ? 0 : previousPage;
        if (ORIGIN_VIEW.equals(origin) && ekitSelectGui != null
                && session.targetPlayer() != null) {
            session.setNavigatingAway(true);
            ekitSelectGui.openViewerAt(player, session.targetPlayer(),
                    session.get("viewer-target-name", String.class), category, page);
        } else if (ORIGIN_EDIT.equals(origin) && ekitSelectGui != null) {
            session.setNavigatingAway(true);
            ekitSelectGui.openAt(player, category, page);
        } else if (ORIGIN_TEAM.equals(origin) && teamKitSelectGui != null) {
            session.setNavigatingAway(true);
            teamKitSelectGui.openAt(player, category, page);
        } else if (kitSelectGui != null) {
            session.setNavigatingAway(true);
            kitSelectGui.openFor(player, session, category, page);
        } else {
            player.closeInventory();
        }
    }

}
