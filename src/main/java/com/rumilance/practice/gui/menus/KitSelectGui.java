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

    public KitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.KIT_SELECT, 6, true);
        this.kitService = kitService;
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
     * One-screen picker (2026-09-28 redesign, replacing the two-step "wooden button"
     * category chooser that felt too complex): both families on one screen — the top band
     * lists MAIN kits, the bottom band SUB kits, each with its own header and its own page
     * arrows when it overflows one row. Folder kits still work as before: LEFT-click picks
     * the folder's default child, RIGHT-click opens the child list.
     */
    private static final int MAIN_LABEL_SLOT = 4 + 1 * 9;             // (1,4)
    private static final int MAIN_FIRST_SLOT = 1 + 2 * 9;             // (2,1) → 7 slots
    private static final int SUB_LABEL_SLOT = 4 + 3 * 9;              // (3,4)
    private static final int SUB_FIRST_SLOT = 1 + 4 * 9;              // (4,1) → 7 slots
    private static final int BAND_CAPACITY = 7;

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        paintFrame(player, session, inventory);
        renderBand(player, session, inventory, com.rumilance.practice.model.KitCategory.MAIN,
                MAIN_LABEL_SLOT, MAIN_FIRST_SLOT, "gui.kit-main-button", UiTheme.SUCCESS);
        renderBand(player, session, inventory, com.rumilance.practice.model.KitCategory.SUB,
                SUB_LABEL_SLOT, SUB_FIRST_SLOT, "gui.kit-sub-button", UiTheme.SECONDARY);
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** Header + one row of kits for one category, with per-band arrows when it overflows. */
    private void renderBand(Player player, GuiSession session, Inventory inventory,
                            com.rumilance.practice.model.KitCategory category, int labelSlot,
                            int firstSlot, String labelKey, net.kyori.adventure.text.format.TextColor color) {
        List<KitDefinition> kits = kitService.enabled(category);
        inventory.setItem(labelSlot, ItemBuilder.of(com.rumilance.practice.gui.KitSections
                        .icon(category))
                .name(t(player, labelKey).color(color))
                .lore(UiTheme.labelValue(line(player, "gui.kit-count-label"),
                        String.valueOf(kits.size())))
                .action("decorate")
                .build());
        boolean main = category == com.rumilance.practice.model.KitCategory.MAIN;
        int bandPage = Math.min(bandPage(session, main),
                Math.max(0, (kits.size() - 1) / BAND_CAPACITY));
        if (main) {
            session.put("page-main", bandPage);
        } else {
            session.put("page-sub", bandPage);
        }
        String current = session.selectedKit();
        int from = bandPage * BAND_CAPACITY;
        for (int i = 0; i < BAND_CAPACITY && from + i < kits.size(); i++) {
            inventory.setItem(firstSlot + i, kitIcon(player, session, kits.get(from + i), current));
        }
        // Prev/next live at the row ends of the header line (free since the glass frame is gone).
        boolean prev = bandPage > 0;
        boolean next = from + BAND_CAPACITY < kits.size();
        if (prev) {
            inventory.setItem(labelSlot - 2,
                    ItemBuilder.action(UiTheme.PREV_PAGE, t(player, "menu.page-prev"),
                            "page:" + (main ? "main" : "sub") + ":prev"));
        }
        if (next) {
            inventory.setItem(labelSlot + 2,
                    ItemBuilder.action(UiTheme.NEXT_PAGE, t(player, "menu.page-next"),
                            "page:" + (main ? "main" : "sub") + ":next"));
        }
    }

    private static int bandPage(GuiSession session, boolean main) {
        Integer page = session.get(main ? "page-main" : "page-sub", Integer.class);
        return page == null ? 0 : Math.max(0, page);
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
        if (action != null && action.startsWith("page:")) {
            // Per-band paging: page:main:prev / page:sub:next etc.
            String[] parts = action.split(":");
            if (parts.length == 3) {
                boolean main = "main".equals(parts[1]);
                boolean next = "next".equals(parts[2]);
                com.rumilance.practice.model.KitCategory category = main
                        ? com.rumilance.practice.model.KitCategory.MAIN
                        : com.rumilance.practice.model.KitCategory.SUB;
                int pages = Math.max(1, (kitService.enabled(category).size() + BAND_CAPACITY - 1)
                        / BAND_CAPACITY);
                int page = Math.min(Math.max(0, bandPage(session, main) + (next ? 1 : -1)), pages - 1);
                session.put(main ? "page-main" : "page-sub", page);
                sounds.play(player, "gui-click");
                refresh(player, session, inventory);
            }
            return;
        }
        if ("back".equals(action) || "close".equals(action)) {
            sounds.play(player, "gui-back");
            returnToDuel(player, session);
            return;
        }
        if (action != null && action.startsWith("pick:")) {
            // フォルダを選んだらデフォルトの子で進む(中メニューから選んだ子はそのまま)。
            session.setSelectedKit(kitService.playableId(action.substring(5)));
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
        String kit = session.selectedKit();
        String map = session.selectedMap();
        int bestOf = session.bestOf();
        boolean ranked = session.ranked();
        boolean fromBattle = session.fromBattleMenu();
        player.closeInventory();
        // Pass the choices into openFor so they are applied to the new session BEFORE render.
        duelRequestGui.openFor(player, target, ranked, kit, map, bestOf);
        registry.get(player.getUniqueId()).ifPresent(s -> s.setFromBattleMenu(fromBattle));
    }
}
