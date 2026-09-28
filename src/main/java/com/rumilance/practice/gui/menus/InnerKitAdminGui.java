package com.rumilance.practice.gui.menus;

import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.kit.InnerKitService;
import com.rumilance.practice.kit.KitLoadout;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.locale.MessageService;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.util.KitNames;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * 中メニュー (sub-menu) management — the child kits inside one folder, without a single command.
 *
 * <p>Opened from the kit admin screen (「中メニュー」button), by right-clicking a folder in the
 * kit editor's picker ({@code /ekit}), or by {@code /kit inner <kit>}. One screen does all of it:</p>
 *
 * <ul>
 *   <li><b>create</b> — the three buttons under the list ask for a name in chat and then take the
 *       contents from the admin's inventory (the same snapshot {@code /kit create} takes), from a
 *       copy of the folder's default child, or from nothing. So "child 1 has 6 arrows, child 2 has
 *       3" is built by holding the items and clicking.</li>
 *   <li><b>configure</b> — left-click a child: its own kit admin screen opens, because a child is a
 *       normal kit — contents, icon, name, HP, knockback, block rules, arenas, queue toggle.</li>
 *   <li><b>default</b> — right-click a child: the folder's own tile (and Queue, which never opens a
 *       sub-menu) uses that child from then on.</li>
 *   <li><b>delete</b> — press Q, then confirm. Deleting the default child moves the folder's tile
 *       to the next one; deleting the last child turns the folder back into a plain kit.</li>
 * </ul>
 *
 * <p>A new child inherits the folder's rules — HP, knockback, block rules, arenas, start effects —
 * so a sub-menu offers several loadouts of the same fight. Only the contents, icon and name are
 * new, and the first child becomes the default automatically.</p>
 */
public final class InnerKitAdminGui extends AbstractGui {

    /** Session key holding which screen "back" returns to. */
    public static final String ORIGIN_KEY = "innerkit-admin-origin";

    public static final String ORIGIN_EKIT = "ekit";
    public static final String ORIGIN_KIT_ADMIN = "kitadmin";
    public static final String ORIGIN_COMMAND = "command";

    /** Child-kit rows of the grid; the three create buttons are mirrored at row4 cols 2/4/6. */
    private static final int ENTRY_SLOTS = 22;
    private static final String VIEW_IMPORT = "import";

    private final KitService kitService;
    private EditKitGui editKitGui;
    private ConfirmGui confirmGui;
    private EkitSelectGui ekitSelectGui;
    private KitAdminGui kitAdminGui;

    public InnerKitAdminGui(GuiSessionRegistry registry, SoundService sounds,
                            KitService kitService) {
        super(registry, sounds, GuiType.INNER_KIT_ADMIN, 6, false);
        this.kitService = kitService;
    }

    public void setEditKitGui(EditKitGui editKitGui) {
        this.editKitGui = editKitGui;
    }

    public void setConfirmGui(ConfirmGui confirmGui) {
        this.confirmGui = confirmGui;
    }

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    public void setKitAdminGui(KitAdminGui kitAdminGui) {
        this.kitAdminGui = kitAdminGui;
    }

    /** Opens one folder's 中メニュー management. */
    public void open(Player player, String kitId) {
        open(player, kitId, ORIGIN_COMMAND);
    }

    public void open(Player player, String kitId, String origin) {
        if (kitId == null || kitId.isBlank()) {
            return;
        }
        // 子キットの追加・削除・既定の変更は Admin の仕事。/ekit は rumilance.user でも叩けるので、
        // ここでも一度確認しておく（右クリックの出し分けを何かの拍子に通り抜けても開かない）。
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(t(player, "general.no-permission"));
            return;
        }
        KitDefinition parent = kitService.get(kitId).orElse(null);
        if (parent == null || parent.isChild()) {
            player.sendMessage(t(player, "gui.innerkit-create-no-kit",
                    MessageService.tags("name", kitId)).color(UiTheme.DANGER));
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(parent.name());
        session.setPage(0);
        session.put(ORIGIN_KEY, origin);
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected com.rumilance.practice.gui.GuiFrame.Theme theme() {
        return com.rumilance.practice.gui.GuiFrame.Theme.PURPLE;
    }

    @Override
    protected Material titleIcon() {
        return Material.SHULKER_BOX;
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        return t(player, "gui.innerkit-admin-title").color(UiTheme.PRIMARY)
                .append(Component.text(" " + kitName(session.selectedKit()), UiTheme.VALUE));
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
        if (VIEW_IMPORT.equals(session.get("view", String.class))) {
            renderImport(player, session, inventory, kitId);
            return;
        }
        List<KitDefinition> children = kitService.children(kitId);
        String defaultId = kitService.defaultChild(kitId).map(KitDefinition::name).orElse(null);
        if (children.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.CHEST)
                    .name(t(player, "gui.innerkit-admin-empty").color(UiTheme.MUTED))
                    .lore(
                            UiTheme.divider(),
                            UiTheme.line(line(player, "gui.innerkit-admin-empty-lore"))
                    )
                    .action("decorate")
                    .build());
        }
        int pages = Math.max(1, (children.size() + ENTRY_SLOTS - 1) / ENTRY_SLOTS);
        int page = Math.min(session.page(), pages - 1);
        for (int index = 0; index < ENTRY_SLOTS && page * ENTRY_SLOTS + index < children.size(); index++) {
            KitDefinition child = children.get(page * ENTRY_SLOTS + index);
            inventory.setItem(MenuScaffold.gridSlot(index),
                    childTile(player, child, child.name().equalsIgnoreCase(defaultId)));
        }
        pageControls(player, inventory, page, pages);
        createButtons(player, inventory);
        inventory.setItem(GuiSlots.slot(5, 1), ItemBuilder.of(Material.NETHER_STAR)
                .name(t(player, "gui.innerkit-admin-convert").color(UiTheme.PRIMARY))
                .lore(UiTheme.divider(), UiTheme.line(line(player, "gui.innerkit-admin-convert-lore")))
                .action("convert").build());
        inventory.setItem(GuiSlots.slot(5, 7), ItemBuilder.of(Material.HOPPER)
                .name(t(player, "gui.innerkit-admin-import").color(UiTheme.SECONDARY))
                .lore(UiTheme.divider(), UiTheme.line(line(player, "gui.innerkit-admin-import-lore")))
                .action("import").build());
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** Select an already existing, independent kit to file under this folder — no data is lost. */
    private void renderImport(Player player, GuiSession session, Inventory inventory, String folderId) {
        List<KitDefinition> choices = kitService.topLevel().stream()
                .filter(k -> !k.name().equalsIgnoreCase(folderId) && !kitService.isFolder(k.name()))
                .toList();
        if (choices.isEmpty()) {
            inventory.setItem(MenuScaffold.gridSlot(13), ItemBuilder.of(Material.BARRIER)
                    .name(t(player, "gui.innerkit-admin-import-empty").color(UiTheme.MUTED))
                    .action("decorate").build());
        }
        int perPage = MenuScaffold.gridPageSize();
        int pages = Math.max(1, (choices.size() + perPage - 1) / perPage);
        int page = Math.min(session.page(), pages - 1);
        for (int index = 0; index < perPage && page * perPage + index < choices.size(); index++) {
            KitDefinition kit = choices.get(page * perPage + index);
            inventory.setItem(MenuScaffold.gridSlot(index),
                    ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                            .nameMini(kit.prettyDisplayName())
                            .lore(UiTheme.divider(),
                                    UiTheme.labelValue(line(player, "gui.innerkit-id-label"), kit.name()),
                                    UiTheme.hint(line(player, "gui.innerkit-admin-import-hint")))
                            .action("move:" + kit.name()).build());
        }
        pageControls(player, inventory, page, pages);
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    private void pageControls(Player player, Inventory inventory, int page, int pages) {
        if (page > 0) {
            inventory.setItem(GuiSlots.slot(5, 0), ItemBuilder.of(Material.ARROW)
                    .name(t(player, "menu.page-prev")).action("page:prev").build());
        }
        if (page + 1 < pages) {
            inventory.setItem(GuiSlots.slot(5, 8), ItemBuilder.of(Material.ARROW)
                    .name(t(player, "menu.page-next")).action("page:next").build());
        }
    }

    /** One child kit. The folder's default carries the badge — it is what the tile and Queue use. */
    private ItemStack childTile(Player player, KitDefinition child, boolean isDefault) {
        String label = child.prettyDisplayName()
                + (isDefault ? " " + InnerKitService.DEFAULT_BADGE : "");
        return ItemBuilder.of(ItemBuilder.materialOr(child.icon(), Material.DIAMOND_SWORD))
                .nameMini(label)
                .lore(
                        UiTheme.divider(),
                        isDefault
                                ? UiTheme.status(line(player, "gui.innerkit-admin-default-lore"),
                                        UiTheme.SUCCESS)
                                : UiTheme.line(line(player, "gui.innerkit-admin-child-lore")),
                        UiTheme.labelValue(line(player, "gui.innerkit-admin-slots"),
                                Integer.toString(KitLoadout.itemCount(KitLoadout.fromOfficial(child)))),
                        UiTheme.labelValue(line(player, "gui.innerkit-id-label"), child.name()),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-admin-edit-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-default-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-official-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-delete-hint"))
                )
                .glint(isDefault)
                .action("child:" + child.name())
                .build();
    }

    /** The three ways to create a child kit — contents source first, name asked in chat after. */
    private void createButtons(Player player, Inventory inventory) {
        inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(4, 2), ItemBuilder.of(Material.PLAYER_HEAD)
                .name(t(player, "gui.innerkit-create-inventory").color(UiTheme.SUCCESS))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-create-inventory-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-name-prompt"))
                )
                .glint(true)
                .action("create:inventory")
                .build());
        inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(4, 4), ItemBuilder.of(Material.CHEST)
                .name(t(player, "gui.innerkit-create-copy").color(UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-create-copy-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-name-prompt"))
                )
                .action("create:copy")
                .build());
        inventory.setItem(com.rumilance.practice.util.GuiSlots.slot(4, 6), ItemBuilder.of(Material.PAPER)
                .name(t(player, "gui.innerkit-create-empty").color(UiTheme.MUTED))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-create-empty-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-name-prompt"))
                )
                .action("create:empty")
                .build());
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, ClickType click) {
        if (action == null || !player.hasPermission("rumilance.admin")) {
            return;
        }
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            if (VIEW_IMPORT.equals(session.get("view", String.class))) {
                session.put("view", null);
                session.setPage(0);
                refresh(player, session, inventory);
            } else {
                back(player, session);
            }
            return;
        }
        String kitId = session.selectedKit();
        if (kitId == null || kitService.get(kitId).isEmpty()) {
            sounds.play(player, "error");
            return;
        }
        String origin = originOf(session);
        if ("page:prev".equals(action) || "page:next".equals(action)) {
            session.setPage(Math.max(0, session.page() + ("page:next".equals(action) ? 1 : -1)));
            refresh(player, session, inventory);
            return;
        }
        if ("import".equals(action)) {
            session.put("view", VIEW_IMPORT);
            session.setPage(0);
            sounds.play(player, "gui-click");
            refresh(player, session, inventory);
            return;
        }
        if (action.startsWith("move:") && VIEW_IMPORT.equals(session.get("view", String.class))) {
            String childId = action.substring("move:".length());
            try {
                boolean moved = kitService.setParent(childId, kitId);
                player.sendMessage(t(player, moved
                        ? "gui.innerkit-admin-moved" : "gui.innerkit-save-failed",
                        MessageService.tags("name", childId)));
                sounds.play(player, moved ? "select" : "error");
                if (moved) {
                    session.put("view", null);
                    session.setPage(0);
                    refresh(player, session, inventory);
                }
            } catch (RuntimeException e) {
                player.sendMessage(t(player, "gui.innerkit-save-failed"));
                sounds.play(player, "error");
            }
            return;
        }
        if ("convert".equals(action)) {
            try {
                boolean ok = kitService.ensureFolder(kitId).isPresent();
                player.sendMessage(t(player, ok
                        ? "gui.innerkit-admin-converted" : "gui.innerkit-save-failed"));
                sounds.play(player, ok ? "select" : "error");
                refresh(player, session, inventory);
            } catch (RuntimeException e) {
                player.sendMessage(t(player, "gui.innerkit-save-failed"));
                sounds.play(player, "error");
            }
            return;
        }
        if (action.startsWith("create:")) {
            sounds.play(player, "gui-click");
            promptCreate(player, kitId, origin, action.substring("create:".length()));
            return;
        }
        if (!action.startsWith("child:")) {
            return;
        }
        String childId = action.substring("child:".length());
        KitDefinition child = kitService.get(childId).orElse(null);
        if (child == null || !kitId.equalsIgnoreCase(child.parent())) {
            sounds.play(player, "error");
            return;
        }
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            sounds.play(player, "gui-click");
            askDelete(player, kitId, child, origin);
            return;
        }
        if (click == ClickType.SHIFT_LEFT && editKitGui != null) {
            // Admin edits the shared contents of THIS child, never their own personal layout.
            session.setNavigatingAway(true);
            editKitGui.openOfficialEditor(player, childId);
            return;
        }
        if (click == ClickType.RIGHT) {
            boolean ok = kitService.setDefaultChild(kitId, childId);
            player.sendMessage(ok
                    ? t(player, "gui.innerkit-admin-default-set",
                            MessageService.tags("name", child.prettyDisplayName()))
                    : t(player, "gui.innerkit-save-failed"));
            sounds.play(player, ok ? "select" : "error");
            if (ok) {
                refresh(player, session, inventory);
            }
            return;
        }
        if (click != ClickType.LEFT) {
            return;
        }
        sounds.play(player, "select");
        session.setNavigatingAway(true);
        if (kitAdminGui != null) {
            kitAdminGui.openConfig(player, childId);
        } else if (editKitGui != null) {
            editKitGui.openOfficialEditor(player, childId);
        } else {
            player.closeInventory();
        }
    }

    // ---------------------------------------------------------------- create

    private void promptCreate(Player player, String kitId, String origin, String kind) {
        // Fail before the chat prompt when there is nothing to snapshot: asking for a name and
        // then refusing wastes the admin's typing.
        if ("inventory".equals(kind) && !KitLoadout.hasAnyItem(KitLoadout.fromPlayer(player))) {
            player.sendMessage(t(player, "gui.innerkit-empty-inventory").color(UiTheme.DANGER));
            sounds.play(player, "error");
            return;
        }
        player.closeInventory();
        player.sendMessage(t(player, "gui.innerkit-name-prompt"));
        PendingInput.await(player, text -> {
            if (text == null || text.isBlank() || text.equalsIgnoreCase("cancel")) {
                player.sendMessage(t(player, "gui.innerkit-name-cancel").color(UiTheme.MUTED));
                open(player, kitId, origin);
                return;
            }
            create(player, kitId, origin, kind, text.trim());
        });
    }

    private void create(Player player, String kitId, String origin, String kind, String name) {
        // 名前から id を作る。日本語など ascii に落とせない名前は「親id-連番」を id にして、
        // 入力された名前を表示名としてそのまま使う。
        String id = idOf(name);
        if (id.isEmpty()) {
            id = uniqueChildId(kitId);
        } else if (kitService.get(id).isPresent()) {
            player.sendMessage(t(player, "gui.innerkit-exists",
                    MessageService.tags("name", name)).color(UiTheme.DANGER));
            sounds.play(player, "error");
            open(player, kitId, origin);
            return;
        }
        ItemStack[] seed;
        String icon;
        switch (kind) {
            case "copy" -> {
                // 既定の子(いなければフォルダ自身)の中身を複製してから差だけ直す、がやりやすい。
                KitDefinition source = kitService.defaultChild(kitId)
                        .orElseGet(() -> kitService.get(kitId).orElse(null));
                seed = source == null ? new ItemStack[KitLoadout.SIZE] : KitLoadout.fromOfficial(source);
                icon = source == null ? null : source.icon();
            }
            case "empty" -> {
                seed = new ItemStack[KitLoadout.SIZE];
                icon = null;
            }
            default -> {
                seed = KitLoadout.fromPlayer(player);
                ItemStack hand = player.getInventory().getItemInMainHand();
                icon = hand == null || hand.getType().isAir() ? null : hand.getType().name();
            }
        }
        KitDefinition child;
        try {
            child = kitService.createChild(id, kitId, seed, icon, name);
        } catch (RuntimeException e) {
            // DB copy failure: the kit still exists unchanged. Show a useful message rather
            // than dropping the admin's chat input in an uncaught inventory callback.
            player.getServer().getLogger().warning("Could not carry over kit data for " + kitId
                    + ": " + e.getMessage());
            child = null;
        }
        if (child == null) {
            player.sendMessage(t(player, "gui.innerkit-save-failed").color(UiTheme.DANGER));
            sounds.play(player, "error");
            open(player, kitId, origin);
            return;
        }
        player.sendMessage(t(player, "gui.innerkit-created", MessageService.tags(
                "name", name,
                "slots", Integer.toString(KitLoadout.itemCount(seed)))));
        sounds.play(player, "select");
        open(player, kitId, origin);
    }

    /** A typed name as a kit id: lowercase, spaces to dashes, a-z 0-9 and dashes only. */
    private static String idOf(String name) {
        String raw = name.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                out.append(c);
            }
        }
        String id = out.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return id.length() > 48 ? id.substring(0, 48) : id;
    }

    /** {@code <folder>-2}, {@code <folder>-3}, ... — the first free id for a non-ascii name. */
    private String uniqueChildId(String parentId) {
        String base = parentId.toLowerCase(Locale.ROOT) + "-";
        for (int i = 2; i < 1000; i++) {
            if (kitService.get(base + i).isEmpty()) {
                return base + i;
            }
        }
        return base + System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- delete

    private void askDelete(Player player, String kitId, KitDefinition child, String origin) {
        if (confirmGui == null) {
            delete(player, kitId, child, origin);
            return;
        }
        player.closeInventory();
        confirmGui.open(player,
                t(player, "gui.innerkit-delete-title",
                        MessageService.tags("name", child.prettyDisplayName())),
                List.of(t(player, "gui.innerkit-delete-lore")),
                yes -> delete(yes, kitId, child, origin),
                no -> open(no, kitId, origin));
    }

    private void delete(Player player, String kitId, KitDefinition child, String origin) {
        // delete() は子キットの親参照と既定の子を整理してから消すので、フォルダは壊れない。
        boolean removed = kitService.get(child.name())
                .filter(k -> kitId.equalsIgnoreCase(k.parent()))
                .map(k -> kitService.delete(k.name())).orElse(false);
        player.sendMessage(removed
                ? t(player, "gui.innerkit-deleted",
                        MessageService.tags("name", child.prettyDisplayName()))
                : t(player, "gui.innerkit-save-failed"));
        sounds.play(player, removed ? "delete" : "error");
        open(player, kitId, origin);
    }

    // ---------------------------------------------------------------- navigation

    private String originOf(GuiSession session) {
        String origin = session.get(ORIGIN_KEY, String.class);
        return origin == null ? ORIGIN_COMMAND : origin;
    }

    private void back(Player player, GuiSession session) {
        String origin = originOf(session);
        if (ORIGIN_EKIT.equals(origin) && ekitSelectGui != null) {
            session.setNavigatingAway(true);
            ekitSelectGui.open(player);
            return;
        }
        if (ORIGIN_KIT_ADMIN.equals(origin) && kitAdminGui != null) {
            session.setNavigatingAway(true);
            // Back to the kit's own config panel, not the whole kit list.
            kitAdminGui.openConfig(player, session.selectedKit());
            return;
        }
        player.closeInventory();
    }

    /** Kit display name for the title, falling back to a prettified id. */
    private String kitName(String kitId) {
        if (kitId == null) {
            return "";
        }
        return kitService.get(kitId).map(KitDefinition::prettyDisplayName)
                .orElseGet(() -> KitNames.pretty(kitId));
    }
}
