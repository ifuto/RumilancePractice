package com.rumilance.practice.gui.menus;

import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiCloseHandler;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.model.KitCategory;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.GuiSlots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * /ekit entry: click a kit to edit immediately. Original kits sit on the bottom-left.
 */
public final class EkitSelectGui extends AbstractGui
        implements BottomInventoryClickHandler, GuiCloseHandler {

    /** The mockup KIT SELECT GUI grid: rows 1-4 x cols 1-7 = 28 kits per page. */
    private static final int KIT_GRID_SIZE = 28;

    /** Bottom-inventory cells of the mockup's K-chip panel (K1..K4 chips). */
    private static final int[] CHIP_SLOTS = {19, 20, 24, 25};
    /** The mockup's null center cell between the lime separators: the assigned kit's item. */
    private static final int ASSIGN_SLOT = 22;

    /**
     * The player's own inventory rows are borrowed for the K-chip panel (mockup main36);
     * the real contents are stashed here and restored on every close / chooser return.
     */
    private final java.util.Map<java.util.UUID, ItemStack[]> bottomStash =
            new java.util.concurrent.ConcurrentHashMap<>();

    private InnerKitSelectGui innerKitSelectGui;
    private InnerKitAdminGui innerKitAdminGui;

    public void setInnerKitSelectGui(InnerKitSelectGui innerKitSelectGui) {
        this.innerKitSelectGui = innerKitSelectGui;
    }

    /** 中キット management screen opened by a right-click here (create/rename/delete/edit). */
    public void setInnerKitAdminGui(InnerKitAdminGui innerKitAdminGui) {
        this.innerKitAdminGui = innerKitAdminGui;
    }

    /**
     * A folder's RIGHT-click opens its ordinary child-kit list (for both players and admins).
     * Players edit their own layout for that child, the same as any other kit. Admins can reach
     * the management screen from the child list's Manage button; an empty parent opens the
     * management screen directly so they can create the first child. LEFT uses the default child.
     * The K1..K4 chips are handled here too — they need the {@code ClickType}.
     */
    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("viewkit:")
                && isViewer(session) && innerKitSelectGui != null) {
            String kitId = action.substring("viewkit:".length());
            if (kitService.isFolder(kitId)) {
                session.setNavigatingAway(true);
                innerKitSelectGui.openForViewer(player, session, kitId);
                return;
            }
        }
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT
                && action != null && action.startsWith("kit:")
                && !isViewer(session)) {
            String kitId = action.substring("kit:".length());
            // 中メニューを持つキット: 誰でも子キットの一覧へ(子は普通のキットなので、そこで
            // 自分の配置を直す)。子を持たないキットを Admin が右クリックしたときだけ、子を作る
            // ための管理画面へ進む。
            if (kitService.isFolder(kitId)) {
                if (innerKitSelectGui != null) {
                    sounds.play(player, "gui-click");
                    session.setNavigatingAway(true);
                    innerKitSelectGui.openForEdit(player, session, kitId);
                    return;
                }
            } else if (innerKitAdminGui != null && player.hasPermission("rumilance.admin")) {
                sounds.play(player, "gui-click");
                session.setNavigatingAway(true);
                innerKitAdminGui.open(player, kitId, InnerKitAdminGui.ORIGIN_EKIT);
                return;
            }
        }
        handleClick(player, session, inventory, slot, action);
    }

    private final KitService kitService;
    private EditKitGui editKitGui;
    private OriginalKitGui originalKitGui;
    private CrystalKitSlotsGui crystalKitSlotsGui;
    private com.rumilance.practice.kit.KitVariantsStore kitVariantsStore;
    private volatile com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker;

    public EkitSelectGui(GuiSessionRegistry registry, SoundService sounds, KitService kitService) {
        super(registry, sounds, GuiType.EKIT_SELECT, 6, false);
        this.kitService = kitService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    /** Per-kit K1..K4 variant slots (本=Active/紙=Not Active chips). Null hides the chips. */
    public void setKitVariantsStore(com.rumilance.practice.kit.KitVariantsStore kitVariantsStore) {
        this.kitVariantsStore = kitVariantsStore;
    }

    /** Shared last-selected-kit tracker (also fed by queue/duel pickers) — 前回の KIT tile. */
    public void setLastKitTracker(com.rumilance.practice.kit.LastSelectedKitTracker lastKitTracker) {
        this.lastKitTracker = lastKitTracker;
    }

    public void setOriginalKitGui(OriginalKitGui originalKitGui) {
        this.originalKitGui = originalKitGui;
    }

    public void setCrystalKitSlotsGui(CrystalKitSlotsGui crystalKitSlotsGui) {
        this.crystalKitSlotsGui = crystalKitSlotsGui;
    }

    /** Reopen the same parent category/page after selecting or backing out of a child menu. */
    public void openAt(Player player, String category, int page) {
        openWithSession(player, session -> {
            session.setKitCategory(category);
            session.setPage(Math.max(0, page));
        });
    }

    /** Opens the official-kit picker in read-only mode for a tester inspecting another player. */
    public void openViewer(Player viewer, UUID targetId, String targetName) {
        openViewerAt(viewer, targetId, targetName, null, 0);
    }

    public void openViewerAt(Player viewer, UUID targetId, String targetName,
                             String category, int page) {
        openWithSession(viewer, session -> {
            session.put("mode", "viewer-picker");
            session.setTargetPlayer(targetId);
            session.put("viewer-target-name", targetName == null ? "?" : targetName);
            session.setKitCategory(category);
            session.setPage(Math.max(0, page));
        });
    }

    private static boolean isViewer(GuiSession session) {
        return session != null && "viewer-picker".equals(session.get("mode", String.class));
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.YELLOW;
    }

    @Override
    protected Material titleIcon() {
        return Material.CRAFTING_TABLE;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        if (isViewer(session)) {
            return Component.text("Kit View: "
                            + session.get("viewer-target-name", String.class), UiTheme.PRIMARY)
                    .decoration(TextDecoration.ITALIC, false);
        }
        return t(player, "gui.kit-edit-title").color(UiTheme.PRIMARY);
    }

    /** 開き直すたびに「Main / Sub / Sword」の2択から始める（前のカテゴリを持ち越さない）。 */
    @Override
    protected void configureSession(GuiSession session, Player player) {
        session.setKitCategory(null);
        session.setPage(0);
        session.put("selected-kit", null);
    }

    /**
     * Two screens, both painted CELL-FOR-CELL from docs/design/gui.json (saves v2,
     * 2026-10-04): category==null → MAIN KIT SELECTER, otherwise the KIT SELECT GUI.
     * No generic frame and no filler — every cell the mockup leaves empty stays EMPTY
     * air (Unused cell は空気のまま), and the K-chip panel lives in the player's own
     * inventory rows exactly like the mockup's main36.
     */
    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        if (session.kitCategory() == null) {
            restoreBottom(player);
            renderChooser(player, inventory);
            return;
        }
        if (!isViewer(session)) {
            borrowBottom(player);
        }
        renderKitSelect(player, session, inventory);
    }

    /**
     * MAIN KIT SELECTER — mockup cell-for-cell: full green glass bars on rows 0 and 5,
     * weathered copper chain side borders on rows 1-4, MAIN KITS (wild trim) at (2,2),
     * SUB KITS (bolt trim) at (2,6), and the 前回の KIT sword slot centered at (3,4).
     * Every other interior cell stays EMPTY (air).
     */
    private void renderChooser(Player player, Inventory inventory) {
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(0, col), mockupPane(Material.GREEN_STAINED_GLASS_PANE));
            inventory.setItem(GuiSlots.slot(5, col), mockupPane(Material.GREEN_STAINED_GLASS_PANE));
        }
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), mockupPane(Material.WEATHERED_COPPER_CHAIN));
            inventory.setItem(GuiSlots.slot(row, 8), mockupPane(Material.WEATHERED_COPPER_CHAIN));
        }
        int mainCount = kitService.enabled(KitCategory.MAIN).size();
        int subCount = kitService.enabled(KitCategory.SUB).size();
        inventory.setItem(GuiSlots.slot(2, 2),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        KitCategory.MAIN,
                        t(player, "gui.kit-main-button").color(UiTheme.SUCCESS),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-main-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(mainCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
        inventory.setItem(GuiSlots.slot(2, 6),
                com.rumilance.practice.gui.KitSections.categoryButton(
                        KitCategory.SUB,
                        t(player, "gui.kit-sub-button").color(UiTheme.SECONDARY),
                        java.util.List.of(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.kit-sub-button-lore")),
                                UiTheme.blank(),
                                UiTheme.labelValue(line(player, "gui.kit-count-label"),
                                        String.valueOf(subCount)),
                                UiTheme.blank(),
                                UiTheme.hint(line(player, "gui.kit-button-hint")))));
        inventory.setItem(GuiSlots.slot(3, 4), previousKitTile(player));
    }

    /**
     * KIT SELECT GUI — mockup cell-for-cell. Container: green bar row0 with the category
     * header (wild/bolt trim) centered at (0,4); weathered copper chain borders on
     * rows 1-4 with the 28-cell kit grid between them (empty cells = gray 空き枠);
     * green bar row5 with the Back barrier at (5,4) (paging arrows at (5,3)/(5,5) only
     * when the category needs more than one page). The K-chip panel is painted into the
     * player's own inventory rows (see {@link #paintChipPanel}).
     */
    private void renderKitSelect(Player player, GuiSession session, Inventory inventory) {
        String categoryKey = session.kitCategory();
        KitCategory category = "SUB".equalsIgnoreCase(categoryKey) ? KitCategory.SUB : KitCategory.MAIN;
        List<KitDefinition> kits = kitService.enabled(category);
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(0, col), col == 4
                    ? headerTile(player, category) : mockupPane(Material.GREEN_STAINED_GLASS_PANE));
        }
        int pages = Math.max(1, (kits.size() + KIT_GRID_SIZE - 1) / KIT_GRID_SIZE);
        int page = Math.min(Math.max(0, session.page()), pages - 1);
        String selected = session.get("selected-kit", String.class);
        for (int row = 1; row <= 4; row++) {
            inventory.setItem(GuiSlots.slot(row, 0), mockupPane(Material.WEATHERED_COPPER_CHAIN));
            inventory.setItem(GuiSlots.slot(row, 8), mockupPane(Material.WEATHERED_COPPER_CHAIN));
            for (int col = 1; col <= 7; col++) {
                int idx = page * KIT_GRID_SIZE + (row - 1) * 7 + (col - 1);
                if (idx < kits.size()) {
                    KitDefinition kit = kits.get(idx);
                    ItemStack icon = kitIcon(player, kit, isViewer(session));
                    if (selected != null && selected.equals(kit.name())) {
                        // 代入済みキット: the chips panel below belongs to this kit.
                        icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                    }
                    inventory.setItem(GuiSlots.slot(row, col), icon);
                } else {
                    inventory.setItem(GuiSlots.slot(row, col), mockupPane(Material.GRAY_STAINED_GLASS_PANE));
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            inventory.setItem(GuiSlots.slot(5, col), col == 4
                    ? ItemBuilder.action(Material.BARRIER, t(player, "menu.back"), "back")
                    : mockupPane(Material.GREEN_STAINED_GLASS_PANE));
        }
        if (pages > 1) {
            if (page > 0) {
                inventory.setItem(GuiSlots.slot(5, 3), pageArrow(player, "page:prev", "menu.page-prev"));
            }
            if (page < pages - 1) {
                inventory.setItem(GuiSlots.slot(5, 5), pageArrow(player, "page:next", "menu.page-next"));
            }
        }
        paintChipPanel(player, session);
    }

    /** The mockup's category header tile (wild trim = MAIN, bolt trim = SUB), decorative. */
    private ItemStack headerTile(Player player, KitCategory category) {
        boolean main = category == KitCategory.MAIN;
        return ItemBuilder.of(com.rumilance.practice.gui.KitSections.icon(category))
                .name(Component.text(main ? "MAIN KITS" : "SUB KITS",
                                main ? UiTheme.SUCCESS : UiTheme.SECONDARY)
                        .decoration(TextDecoration.ITALIC, false))
                .action("decorate")
                .build();
    }

    private ItemStack pageArrow(Player player, String action, String loreKey) {
        return ItemBuilder.of(Material.ARROW)
                .name(t(player, loreKey).color(UiTheme.VALUE)
                        .decoration(TextDecoration.ITALIC, false))
                .action(action)
                .build();
    }

    /** A plain, unclickable mockup decoration cell. */
    private static ItemStack mockupPane(Material material) {
        return ItemBuilder.action(material, Component.text(" "), "decorate");
    }

    /**
     * 前回の KIT — the centered shortcut between MAIN and SUB: the kit the player selected
     * last (tracked across this picker AND the queue/duel kit screens). One click opens the
     * KIT EDIT GUI on that kit's ACTIVE K1..K4 slot.
     */
    private ItemStack previousKitTile(Player player) {
        com.rumilance.practice.kit.LastSelectedKitTracker tracker = lastKitTracker;
        String lastId = tracker == null ? null : tracker.get(player.getUniqueId());
        KitDefinition kit = lastId == null ? null : kitService.get(lastId).orElse(null);
        if (kit == null) {
            return ItemBuilder.of(Material.GRAY_DYE)
                    .name(Component.text("Previous Kit", UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(UiTheme.divider(),
                            UiTheme.line("No kit selected yet."),
                            UiTheme.hint(line(player, "gui.pick-category-first")))
                    .action("decorate")
                    .build();
        }
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(kit.prettyDisplayName())
                .lore(UiTheme.divider(),
                        UiTheme.status(line(player, "gui.prev-kit-lore"), UiTheme.SUCCESS),
                        UiTheme.hint(line(player, "gui.kit-button-hint")))
                // The wooden-button delay for parity with the category buttons.
                .action(com.rumilance.practice.gui.DelayedButton.wrap("prevkit"))
                .build();
    }

    /**
     * The mockup's K-chip panel, painted into the PLAYER's own inventory rows (main36):
     * a gray outer frame with green inner corners and a lime accent column, chips
     * K1/K2 (inv 19/20) and K3/K4 (inv 24/25) flanking the assigned kit's item at inv 22
     * between lime separators. 未代入: the chip cells AND the assign cell are gray glass.
     */
    private void paintChipPanel(Player player, GuiSession session) {
        if (isViewer(session)) {
            return; // viewer: their inventory is never touched
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack gray = mockupPane(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack green = mockupPane(Material.GREEN_STAINED_GLASS_PANE);
        ItemStack lime = mockupPane(Material.LIME_STAINED_GLASS_PANE);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, gray);                       // bottom panel row 0
        }
        // docs/design/gui.json「KIT SELECT GUI」main36 と 1:1。中央のアサインセル(inv 22)は
        // 上下左右をライムで囲む: 左右 21/23、上 13、下 31。その外側の四隅が緑 12/14/30/32。
        // (以前は 13/31 が三項演算の else に落ちて灰色になり、ライムの十字が縦に欠けていた)
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, i == 13 ? lime : (i == 12 || i == 14) ? green : gray);
        }
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, i == 31 ? lime : (i == 30 || i == 32) ? green : gray);
        }
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, i == 21 || i == 23 ? lime : gray);
        }
        String selected = session.get("selected-kit", String.class);
        KitDefinition kit = selected == null ? null : kitService.get(selected).orElse(null);
        if (kit == null) {
            // 未代入 (user spec 2026-10-04): the 本/紙 chip cells show TRANSPARENT glass
            // (the assign cell keeps the frame's gray glass).
            for (int slot : CHIP_SLOTS) {
                inv.setItem(slot, mockupPane(Material.GLASS_PANE));
            }
            inv.setItem(ASSIGN_SLOT, mockupPane(Material.GRAY_STAINED_GLASS_PANE));
            return;
        }
        // 代入済み: the kit's representative item sits in the center assign cell.
        inv.setItem(ASSIGN_SLOT,
                ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                        .nameMini(kit.prettyDisplayName())
                        .lore(UiTheme.divider(),
                                UiTheme.status(line(player, "gui.assigned-kit-lore"),
                                        UiTheme.SUCCESS))
                        .build());
        int active = kitVariantsStore == null ? 1
                : kitVariantsStore.selected(player.getUniqueId(), selected);
        for (int k = 1; k <= com.rumilance.practice.kit.KitVariantsStore.SLOTS; k++) {
            boolean isActive = k == active;
            ItemStack chip = ItemBuilder.of(isActive ? Material.BOOK : Material.PAPER)
                    .name(Component.text("KIT " + k,
                                    isActive ? UiTheme.SUCCESS : UiTheme.MUTED)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(UiTheme.divider(),
                            UiTheme.status(isActive ? "Active" : "Not Active",
                                    isActive ? UiTheme.SUCCESS : UiTheme.MUTED),
                            UiTheme.line("Kit: "
                                    + com.rumilance.practice.util.KitNames.pretty(selected)),
                            UiTheme.blank(),
                            UiTheme.hint("Left-click: make Active"),
                            UiTheme.hint("Right-click: edit this slot"),
                            UiTheme.hint("Shift+click: reset contents"))
                    .build();
            if (isActive) {
                chip.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            inv.setItem(CHIP_SLOTS[k - 1], chip);
        }
    }

    /** Stashes the player's real inventory rows once, before the chip panel paints. */
    private void borrowBottom(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        bottomStash.computeIfAbsent(player.getUniqueId(), uuid -> {
            ItemStack[] copy = player.getInventory().getStorageContents();
            ItemStack[] saved = new ItemStack[36];
            for (int i = 0; i < 36 && i < copy.length; i++) {
                saved[i] = copy[i] == null ? null : copy[i].clone();
            }
            return saved;
        });
    }

    /** Hands the player's real inventory rows back (idempotent). */
    private void restoreBottom(Player player) {
        if (player == null) {
            return;
        }
        ItemStack[] saved = bottomStash.remove(player.getUniqueId());
        if (saved == null || !player.isOnline()) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, saved[i] == null ? null : saved[i].clone());
        }
    }

    /**
     * Bottom-inventory clicks (player's own rows): the K-chip panel is the only live
     * area. LEFT = make Active, RIGHT = open the KIT EDIT GUI on exactly that K slot,
     * SHIFT+click = reset that slot's contents — each with the direct ui.button.click.
     * Everything else stays cancelled (the listener cancels before dispatch).
     */
    @Override
    public void handleBottomClick(Player player, GuiSession session,
                                  org.bukkit.event.inventory.InventoryClickEvent event) {
        if (session == null || isViewer(session)
                || session.kitCategory() == null || kitVariantsStore == null) {
            return;
        }
        try {
            handleChipClick(player, session, event);
        } catch (Throwable t) {
            // A failing chip action must never look like "the click did nothing": log it
            // and give the player the error cue so the breakage is audible.
            org.bukkit.Bukkit.getLogger().warning("[KIT SELECT] chip click failed: " + t);
            sounds.play(player, "error");
        }
    }

    private void handleChipClick(Player player, GuiSession session,
                                 org.bukkit.event.inventory.InventoryClickEvent event) {
        int slot = event.getSlot();
        int variant = -1;
        for (int k = 0; k < CHIP_SLOTS.length; k++) {
            if (CHIP_SLOTS[k] == slot) {
                variant = k + 1;
                break;
            }
        }
        if (variant < 0) {
            return; // panel frame / assign cell / anything else: no action
        }
        String kitId = session.get("selected-kit", String.class);
        if (kitId == null) {
            return; // 未代入: glass chips are inert
        }
        org.bukkit.event.inventory.ClickType click = event.getClick();
        // Console ground truth for every chip interaction: with this line the server log
        // proves which button arrived and which action ran (build-verification aid,
        // 1.92.35 — the owner reports a left/right mapping that cannot come from this code).
        if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT
                || click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            com.rumilance.practice.sound.ClickSound.play(player);
            if (editKitGui != null) {
                editKitGui.resetVariantSlot(player, kitId, variant);
            }
            paintChipPanel(player, session);
            org.bukkit.Bukkit.getLogger().info("[KIT SELECT] " + player.getName()
                    + " K" + variant + " " + click + " -> reset");
            return;
        }
        if (click == org.bukkit.event.inventory.ClickType.RIGHT) {
            if (editKitGui != null) {
                com.rumilance.practice.sound.ClickSound.play(player);
                session.setNavigatingAway(true);
                editKitGui.openKitVariantEditorWithReturn(player, kitId, variant,
                        session.kitCategory(), session.page());
            }
            org.bukkit.Bukkit.getLogger().info("[KIT SELECT] " + player.getName()
                    + " K" + variant + " " + click + " -> edit"
                    + (editKitGui == null ? " (SKIPPED: editor missing)" : ""));
            return;
        }
        if (click == org.bukkit.event.inventory.ClickType.LEFT) {
            kitVariantsStore.select(player.getUniqueId(), kitId, variant);
            com.rumilance.practice.sound.ClickSound.play(player);
            paintChipPanel(player, session);
            org.bukkit.Bukkit.getLogger().info("[KIT SELECT] " + player.getName()
                    + " K" + variant + " " + click + " -> active (now K"
                    + kitVariantsStore.selected(player.getUniqueId(), kitId) + ")");
        }
    }

    /** Always hand the borrowed inventory rows back when the GUI goes away. */
    @Override
    public void onGuiClose(Player player, GuiSession session, Inventory top,
                           org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        restoreBottom(player);
    }

    private ItemStack kitIcon(Player player, KitDefinition kit, boolean viewer) {
        // フォルダは親の名前とアイコンのまま。左=既定の子を編集、右=子一覧。
        KitDefinition shown = kitService.tile(kit);
        java.util.List<KitDefinition> children = kitService.children(kit.name());
        Material material = Material.matchMaterial(kit.icon());
        java.util.List<Component> lore = new java.util.ArrayList<>(java.util.List.of(
                UiTheme.divider(),
                (kit.crystalFfa() || shown.crystalFfa())
                        ? UiTheme.status("Crystal FFA Kit", UiTheme.SUCCESS)
                        : UiTheme.hint(line(player, viewer ? "gui.kit-view-only" : "gui.kit-edit-hint"))));
        boolean canManage = innerKitAdminGui != null && player.hasPermission("rumilance.admin");
        if (!children.isEmpty() || (!viewer && canManage)) {
            // Admin には右クリックの案内を常に出す（子が0件でも、そこで中メニューを作るため）。
            lore.add(UiTheme.labelValue(line(player, "gui.innerkit-count-label"),
                    String.valueOf(children.size())));
            if (!children.isEmpty()) {
                lore.add(UiTheme.labelValue(line(player, "gui.innerkit-default-label"),
                        com.rumilance.practice.gui.KitDisplayNames.plain(shown)));
            }
            lore.add(UiTheme.hint(line(player, children.isEmpty() && canManage
                    ? "gui.innerkit-admin-right-hint" : "gui.innerkit-right-hint")));
        } else {
            lore.add(UiTheme.hint(line(player, "gui.kit-button-hint")));
        }
        return ItemBuilder.of(material == null ? Material.DIAMOND_SWORD : material)
                .name(MiniMessage.miniMessage().deserialize(kit.prettyDisplayName())
                        .decoration(TextDecoration.ITALIC, false))
                .lore(lore.toArray(new Component[0]))
                // Keep the wooden-button delay for the normal picker; viewer actions are still
                // delayed, but use a separate prefix so the editor can remain read-only.
                .action(com.rumilance.practice.gui.DelayedButton.wrap(
                        (viewer ? "viewkit:" : "kit:") + kit.name()))
                .build();
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
            List<KitDefinition> kits = kitService.enabled(
                    "SUB".equalsIgnoreCase(session.kitCategory())
                            ? KitCategory.SUB : KitCategory.MAIN);
            int pages = Math.max(1, (kits.size() + KIT_GRID_SIZE - 1) / KIT_GRID_SIZE);
            int page = "page:next".equals(action) ? session.page() + 1 : session.page() - 1;
            session.setPage(Math.min(Math.max(0, page), pages - 1));
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if ("close".equals(action) || "back".equals(action)) {
            if (session.kitCategory() != null) {
                // Back from a category returns to the two wooden buttons, not out of /ekit.
                session.setKitCategory(null);
                session.setPage(0);
                sounds.play(player, "gui-back");
                refresh(player, session, inventory);
                return;
            }
            sounds.play(player, "gui-back");
            player.closeInventory();
            return;
        }
        if (action != null && action.startsWith("viewkit:")) {
            String kitId = action.substring("viewkit:".length());
            UUID targetId = session.targetPlayer();
            if (targetId == null || editKitGui == null) {
                return;
            }
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            editKitGui.openKitViewer(player, targetId,
                    session.get("viewer-target-name", String.class), kitId);
            return;
        }
        if (action != null && action.startsWith("kit:")) {
            String pickedId = action.substring(4);
            // Crystal FFA's KIT1..9 variants are keyed by its DECLARED original id; the special
            // editor must stay on that id even if it is also used as a folder. Other folders
            // open the current default child's ordinary personal kit layout.
            boolean crystal = kitService.get(pickedId).map(KitDefinition::crystalFfa).orElse(false);
            String kitId = crystal ? pickedId : kitService.playableId(pickedId);
            if (crystal && crystalKitSlotsGui != null) {
                sounds.play(player, "select");
                session.setNavigatingAway(true);
                crystalKitSlotsGui.openPicker(player, kitId);
                return;
            }
            if (kitVariantsStore != null && !isViewer(session)) {
                // K1..K4 flow: tapping a kit ASSIGNS it (ui.button.click) — its item lands
                // in the assignment cell and the 本/紙 chips appear. Editing happens on the
                // chips (right-click), activation on left-click, reset on shift-click.
                session.put("selected-kit", kitId);
                if (lastKitTracker != null) {
                    lastKitTracker.record(player.getUniqueId(), kitId);
                }
                com.rumilance.practice.sound.ClickSound.play(player);
                refresh(player, session, inventory);
                return;
            }
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (editKitGui != null) {
                // Remember the MAIN/SUB page this kit lives on so BACK lands back there.
                editKitGui.openKitEditorWithReturn(player, kitId,
                        session.kitCategory(), session.page());
            }
            return;
        }
        if ("prevkit".equals(action)) {
            // 前回の KIT: open the last-selected kit's editor on its ACTIVE K slot. From the
            // chooser the Save return target is the chooser itself (null category).
            String lastId = lastKitTracker == null ? null : lastKitTracker.get(player.getUniqueId());
            if (lastId == null) {
                return;
            }
            boolean crystal = kitService.get(lastId).map(KitDefinition::crystalFfa).orElse(false);
            String kitId = crystal ? lastId : kitService.playableId(lastId);
            sounds.play(player, "select");
            session.setNavigatingAway(true);
            if (crystal && crystalKitSlotsGui != null) {
                crystalKitSlotsGui.openPicker(player, kitId);
                return;
            }
            if (kitVariantsStore != null && editKitGui != null) {
                lastKitTracker.record(player.getUniqueId(), kitId);
                editKitGui.openKitVariantEditorWithReturn(player, kitId,
                        kitVariantsStore.selected(player.getUniqueId(), kitId), null, 0);
            } else if (editKitGui != null) {
                editKitGui.openKitEditorWithReturn(player, kitId, null, 0);
            }
            return;
        }
    }

}
