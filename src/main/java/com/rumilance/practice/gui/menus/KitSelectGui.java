package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class KitSelectGui extends AbstractGui {

    private final KitService kitService;
    private DuelRequestGui duelRequestGui;
    private InnerKitSelectGui innerKitSelectGui;
    private volatile com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker;

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
    }

    public void setLastKitTracker(com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker) {
        this.lastKitTracker = lastKitTracker;
    }

    public void setDuelRequestGui(DuelRequestGui duelRequestGui) {
        this.duelRequestGui = duelRequestGui;
    }

    public void setInnerKitSelectGui(InnerKitSelectGui innerKitSelectGui) {
        this.innerKitSelectGui = innerKitSelectGui;
    }

    public void openFor(Player player, GuiSession parent) {
        openFor(player, parent, null, 0);
    }

    /** Return from a child menu to the SAME parent category/page before first render. */
    public void openFor(Player player, GuiSession parent, String category, int page) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setKitCategory(category);
        session.setPage(Math.max(0, page));
        session.setRanked(parent.ranked());
        session.setTargetPlayer(parent.targetPlayer());
        session.setSelectedKit(parent.selectedKit());
        session.setSelectedMap(parent.selectedMap());
        session.setBestOf(parent.bestOf());
        session.setFirstTo(parent.firstTo());
        session.setFromBattleMenu(parent.fromBattleMenu());
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.kit-select-title").color(UiTheme.PRIMARY);
    }

    /**
     * Two-step picker, rebuilt for the 2026-09-28 GUI refresh with generous whitespace and
     * left-right symmetry: step 1 is the 木時差式 (delayed wooden-button) MAIN KITS/SUB KITS
     * branch on the centre row; step 2 is one centred row of that category's kits (folders:
     * LEFT = default child, RIGHT = child list) with mirrored page arrows at the row ends
     * and the kit currently selected glowing.
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        if (session.kitCategory() == null) {
            renderChooser(player, inventory);
        } else {
            renderCategory(player, session, inventory);
        }
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private static int CHOOSER_ROW = 2;

    /** The branch screen: two big centred category buttons (delated-press 木時差式). */
    private void renderChooser(Player player, Inventory inventory) {
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 3),
                categoryTile(player, com.rumilance.practice.model.KitCategory.MAIN,
                        "gui.kit-main-button", UiTheme.SUCCESS));
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 5),
                categoryTile(player, com.rumilance.practice.model.KitCategory.SUB,
                        "gui.kit-sub-button", UiTheme.SECONDARY));
        // 前回選択したキットを MAIN と SUB の間に表示 (クリックで即選択)
        renderLastSelectedKit(player, inventory);
    }

    private void renderLastSelectedKit(Player player, Inventory inventory) {
        com.rumilance.practice.kit.LastSelectedKitTracker tracker = lastKitTracker;
        if (tracker == null) return;
        String lastKitId = tracker.get(player.getUniqueId());
        if (lastKitId == null) return;
        var kit = kitService.get(lastKitId).orElse(null);
        if (kit == null || !kit.enabled()) return;
        Material mat = Material.matchMaterial(kit.icon());
        if (mat == null) mat = Material.DIAMOND_SWORD;
        inventory.setItem(GuiSlots.slot(CHOOSER_ROW, 4),
                ItemBuilder.of(mat)
                        .name(Component.text(KitNames.pretty(kit.name()), UiTheme.VALUE)
                                .decoration(TextDecoration.ITALIC, false))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-last-selected")),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-click-select"))
                        )
                        .glint(true)
                        .action("pick:" + kit.name())
                        .build());
    }

    private ItemStack categoryTile(Player player, com.rumilance.practice.model.KitCategory category,
                                   String nameKey, net.kyori.adventure.text.format.TextColor color) {
        int count = kitService.enabled(category).size();
        return com.rumilance.practice.gui.KitSections.categoryButton(category,
                t(player, nameKey).color(color),
                java.util.List.of(
                        UiTheme.labelValue(line(player, "gui.kit-count-label"), String.valueOf(count)),
                        UiTheme.hint(line(player, "gui.kit-click-select"))));
    }

    private static final int CATEGORY_LABEL_SLOT = 4 + 1 * 9;   // (1,4) header
    private static final int CATEGORY_FIRST_SLOT = 1 + 2 * 9;   // (2,1) → 7 slots row
    private static final int CATEGORY_CAPACITY = 7;

    /** One category: header centred at (1,4); kits centered on row 2; arrows at the row ends. */
    private void renderCategory(Player player, GuiSession session, Inventory inventory) {
        boolean main = !("SUB".equalsIgnoreCase(session.kitCategory()));
        com.rumilance.practice.model.KitCategory category = main
                ? com.rumilance.practice.model.KitCategory.MAIN
                : com.rumilance.practice.model.KitCategory.SUB;
        List<KitDefinition> kits = kitService.enabled(category);
        inventory.setItem(CATEGORY_LABEL_SLOT, ItemBuilder.of(
                        com.rumilance.practice.gui.KitSections.icon(category))
                .name(t(player, main ? "gui.kit-main-button" : "gui.kit-sub-button")
                        .color(main ? UiTheme.SUCCESS : UiTheme.SECONDARY))
                .lore(UiTheme.labelValue(line(player, "gui.kit-count-label"),
                        String.valueOf(kits.size())))
                .action("decorate")
                .build());
        int page = Math.min(Math.max(0, session.page()), Math.max(0, (kits.size() - 1) / CATEGORY_CAPACITY));
        if (page != session.page()) {
            session.setPage(page);
        }
        String current = session.selectedKit();
        int from = page * CATEGORY_CAPACITY;
        for (int i = 0; i < CATEGORY_CAPACITY && from + i < kits.size(); i++) {
            inventory.setItem(CATEGORY_FIRST_SLOT + i,
                    kitIcon(player, session, kits.get(from + i), current));
        }
        if (page > 0) {
            inventory.setItem(2 * 9,
                    ItemBuilder.action(UiTheme.PREV_PAGE, t(player, "menu.page-prev"), "page:prev"));
        }
        if (from + CATEGORY_CAPACITY < kits.size()) {
            inventory.setItem((2 + 1) * 9 - 1,
                    ItemBuilder.action(UiTheme.NEXT_PAGE, t(player, "menu.page-next"), "page:next"));
        }
    }

    private ItemStack kitIcon(Player player, GuiSession session, KitDefinition kit, String current) {
        // フォルダになっても親の名前とアイコンはそのまま。左=既定の子、右=子一覧。
        List<KitDefinition> children = kitService.children(kit.name());
        Material mat = Material.matchMaterial(kit.icon());
        boolean selected = kit.name().equalsIgnoreCase(current)
                || kitService.get(current).map(k -> kit.name().equalsIgnoreCase(k.parent())).orElse(false);
        List<Component> lore = new ArrayList<>(List.of(
                UiTheme.line(kit.prettyDisplayName())));
        if (!children.isEmpty()) {
            lore.add(UiTheme.hint(line(player, "gui.innerkit-right-hint")));
        }
        lore.add(selected
                ? UiTheme.status(line(player, "gui.kit-selected"), UiTheme.SUCCESS)
                : UiTheme.hint(line(player, "gui.kit-click-select")));
        return ItemBuilder.of(mat == null ? Material.DIAMOND_SWORD : mat)
                .name(Component.text(KitNames.pretty(kit.name()),
                        selected ? UiTheme.SUCCESS : UiTheme.VALUE))
                .lore(lore.toArray(new Component[0]))
                .glint(selected)
                .action("pick:" + kit.name())
                .build();
    }

    /** Folder RIGHT-click opens its child-kit list; LEFT picks its default child. */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("pick:")
                && innerKitSelectGui != null) {
            String kitId = action.substring("pick:".length());
            if (kitService.isFolder(kitId)) {
                sounds.play(player, "gui-click");
                session.setNavigatingAway(true);
                innerKitSelectGui.openForDuel(player, session, kitId);
                return;
            }
        }
        handleClick(player, session, inventory, slot, action);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        if (action != null && action.startsWith("cat:")) {
            session.setKitCategory(action.substring(4));
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action != null && action.startsWith("page:")) {
            // Per-category paging on the kit row: page:prev / page:next.
            boolean next = "next".equals(action.substring(5));
            boolean main = !("SUB".equalsIgnoreCase(session.kitCategory()));
            com.rumilance.practice.model.KitCategory category = main
                    ? com.rumilance.practice.model.KitCategory.MAIN
                    : com.rumilance.practice.model.KitCategory.SUB;
            int pages = Math.max(1, (kitService.enabled(category).size() + CATEGORY_CAPACITY - 1)
                    / CATEGORY_CAPACITY);
            session.setPage(Math.min(Math.max(0, session.page() + (next ? 1 : -1)), pages - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("back".equals(action) || "close".equals(action)) {
            if (session.kitCategory() != null && "back".equals(action)) {
                // Back from a category returns to the MAIN/SUB branch, not all the way out.
                session.setKitCategory(null);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("pick:")) {
            // フォルダを選んだらデフォルトの子で進む(中メニューから選んだ子はそのまま)。
            String chosen = kitService.playableId(action.substring(5));
            session.setSelectedKit(chosen);
            if (lastKitTracker != null) {
                lastKitTracker.record(player.getUniqueId(), chosen);
            }
            sounds.play(player, "select");
            returnToDuel(player, session);
        }
    }

    private void returnToDuel(Player player, GuiSession session) {
        Player target = session.targetPlayer() == null ? null : org.bukkit.Bukkit.getPlayer(session.targetPlayer());
        if (target == null || duelRequestGui == null) {
            player.closeInventory();
            return;
        }
        player.closeInventory();
        // Single carry-everything return: openFor builds a FRESH session, so returning through
        // it used to wipe the KB choice, combat mode and FT picked before the kit pick.
        duelRequestGui.reopenCarrying(player, session);
    }
}
