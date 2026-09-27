package com.rumilance.practice.gui.menus;

import com.rumilance.practice.chat.PendingInput;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.InnerKitService;
import com.rumilance.practice.kit.InnerKitService.InnerKit;
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

/**
 * 中キット (inner kit) management — everything about a kit's presets without a single command.
 *
 * <p>Opened from the kit admin screen (「中キット」button), by right-clicking a kit in the kit
 * editor's picker ({@code /ekit}), or by {@code /kit preset <kit>}. One screen does all of it:</p>
 *
 * <ul>
 *   <li><b>create</b> — the three buttons at the bottom of the grid ask for a name in chat and
 *       then take the contents from the admin's inventory (the same snapshot {@code /kit create}
 *       takes), from a copy of the kit, or from nothing. So "preset 1 has 6 arrows, preset 2 has
 *       3" is built by holding the items and clicking.</li>
 *   <li><b>edit contents</b> — left-click an entry: the normal kit editor opens on that preset and
 *       saves into kits.yml, so everybody who picks it gets the same items.</li>
 *   <li><b>rename</b> — right-click: the label shown in the duel/party/edit pickers. On the
 *       default entry this renames only the label (its contents are the kit itself), which is how
 *       a kit {@code Axe} gets listed as {@code HQ Style Axe [Default]}.</li>
 *   <li><b>icon</b> — shift-click with an item in hand.</li>
 *   <li><b>delete</b> — press Q, then confirm.</li>
 * </ul>
 *
 * <p>The default entry is always first and always locked: it is the kit's own loadout, the one
 * Queue and every left-click in the player pickers hand out. It can be renamed and its contents
 * can be edited (that is editing the kit), but it can never be removed or replaced by a
 * preset.</p>
 */
public final class InnerKitAdminGui extends AbstractGui {

    /** Session key holding which screen "back" returns to. */
    public static final String ORIGIN_KEY = "innerkit-admin-origin";

    public static final String ORIGIN_EKIT = "ekit";
    public static final String ORIGIN_KIT_ADMIN = "kitadmin";
    public static final String ORIGIN_COMMAND = "command";

    /** Grid slots for the entry list; the three create buttons sit in the last three. */
    private static final int ENTRY_SLOTS = 25;
    private static final int CREATE_SLOT = ENTRY_SLOTS;

    private final KitService kitService;
    private final InnerKitService innerKits;
    private EditKitGui editKitGui;
    private ConfirmGui confirmGui;
    private EkitSelectGui ekitSelectGui;
    private KitAdminGui kitAdminGui;

    public InnerKitAdminGui(GuiSessionRegistry registry, SoundService sounds,
                            KitService kitService, InnerKitService innerKits) {
        super(registry, sounds, GuiType.INNER_KIT_ADMIN, 6, false);
        this.kitService = kitService;
        this.innerKits = innerKits;
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

    /** Opens one kit's 中キット management. */
    public void open(Player player, String kitId) {
        open(player, kitId, ORIGIN_COMMAND);
    }

    public void open(Player player, String kitId, String origin) {
        if (kitId == null || kitId.isBlank()) {
            return;
        }
        // 中キットの作成・削除・中身の変更は Admin の仕事。/ekit は rumilance.user でも叩けるので、
        // ここでも一度確認しておく（右クリックの出し分けを何かの拍子に通り抜けても開かない）。
        if (!player.hasPermission("rumilance.admin")) {
            player.sendMessage(t(player, "general.no-permission"));
            return;
        }
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitId);
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
        // The locked default first, then every stored preset.
        int index = 0;
        inventory.setItem(MenuScaffold.gridSlot(index++), defaultTile(player, kitId, kit));
        List<InnerKit> presets = innerKits.list(kitId);
        for (InnerKit preset : presets) {
            if (index >= ENTRY_SLOTS) {
                inventory.setItem(MenuScaffold.gridSlot(index), ItemBuilder.of(Material.BARRIER)
                        .name(t(player, "gui.innerkit-admin-more").color(UiTheme.WARNING))
                        .lore(
                                UiTheme.divider(),
                                UiTheme.line(line(player, "gui.innerkit-admin-more-lore"))
                        )
                        .action("decorate")
                        .build());
                break;
            }
            inventory.setItem(MenuScaffold.gridSlot(index++), presetTile(player, preset));
        }
        createButtons(player, inventory);
        MenuScaffold.returnButton(inventory, t(player, "menu.back"));
    }

    /** {@code HQ Style Axe [Default]} — the kit itself: contents locked, label renameable. */
    private ItemStack defaultTile(Player player, String kitId, KitDefinition kit) {
        return ItemBuilder.of(ItemBuilder.materialOr(kit.icon(), Material.DIAMOND_SWORD))
                .nameMini(innerKits.displayOf(kitId, null, kit.prettyDisplayName()))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-admin-default-lore")),
                        UiTheme.labelValue(line(player, "gui.innerkit-admin-slots"),
                                Integer.toString(KitLoadout.itemCount(KitLoadout.fromOfficial(kit)))),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-admin-default-edit-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-rename-hint"))
                )
                .glint(true)
                .action("entry:" + InnerKitService.DEFAULT_ID)
                .build();
    }

    private ItemStack presetTile(Player player, InnerKit preset) {
        Material icon = Material.matchMaterial(preset.icon() == null ? "" : preset.icon());
        return ItemBuilder.of(icon == null || icon.isAir() ? Material.DIAMOND_SWORD : icon)
                .name(Component.text(preset.displayName(), UiTheme.VALUE))
                .lore(
                        UiTheme.divider(),
                        UiTheme.labelValue(line(player, "gui.innerkit-id-label"), preset.id()),
                        UiTheme.labelValue(line(player, "gui.innerkit-admin-slots"),
                                Integer.toString(KitLoadout.itemCount(preset.layout()))),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-admin-edit-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-rename-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-icon-hint")),
                        UiTheme.hint(line(player, "gui.innerkit-admin-delete-hint"))
                )
                .action("entry:" + preset.id())
                .build();
    }

    /** The three ways to create a preset — contents source first, name asked in chat after. */
    private void createButtons(Player player, Inventory inventory) {
        inventory.setItem(MenuScaffold.gridSlot(CREATE_SLOT), ItemBuilder.of(Material.PLAYER_HEAD)
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
        inventory.setItem(MenuScaffold.gridSlot(CREATE_SLOT + 1), ItemBuilder.of(Material.CHEST)
                .name(t(player, "gui.innerkit-create-copy").color(UiTheme.SECONDARY))
                .lore(
                        UiTheme.divider(),
                        UiTheme.line(line(player, "gui.innerkit-create-copy-lore")),
                        UiTheme.blank(),
                        UiTheme.hint(line(player, "gui.innerkit-name-prompt"))
                )
                .action("create:copy")
                .build());
        inventory.setItem(MenuScaffold.gridSlot(CREATE_SLOT + 2), ItemBuilder.of(Material.PAPER)
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
        if (action == null) {
            return;
        }
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("back".equals(action)) {
            sounds.play(player, "gui-back");
            back(player, session);
            return;
        }
        String kitId = session.selectedKit();
        if (kitId == null || kitService.get(kitId).isEmpty()) {
            sounds.play(player, "error");
            return;
        }
        String origin = originOf(session);
        if (action.startsWith("create:")) {
            sounds.play(player, "gui-click");
            promptCreate(player, kitId, origin, action.substring("create:".length()));
            return;
        }
        if (!action.startsWith("entry:")) {
            return;
        }
        String id = action.substring("entry:".length());
        boolean isDefault = InnerKitService.isDefault(id);
        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            if (isDefault) {
                locked(player);
                return;
            }
            sounds.play(player, "gui-click");
            askDelete(player, kitId, id, origin);
            return;
        }
        if (click.isShiftClick()) {
            if (isDefault) {
                locked(player);
                return;
            }
            setIconFromHand(player, session, inventory, kitId, id);
            return;
        }
        if (click == ClickType.RIGHT) {
            sounds.play(player, "gui-click");
            promptRename(player, kitId, id, isDefault, origin);
            return;
        }
        if (click != ClickType.LEFT) {
            return;
        }
        // Left click: the contents — the normal kit editor, on the preset (or on the kit itself
        // for the default entry, whose contents ARE the kit).
        if (editKitGui == null) {
            player.closeInventory();
            return;
        }
        sounds.play(player, "select");
        session.setNavigatingAway(true);
        editKitGui.openKitEditor(player, kitId, null, null, isDefault ? null : id);
    }

    // ---------------------------------------------------------------- create

    private void promptCreate(Player player, String kitId, String origin, String kind) {
        // Fail before the chat prompt when there is nothing to snapshot: asking for a name and
        // then refusing wastes the admin's typing.
        if (!"copy".equals(kind) && !"empty".equals(kind)
                && !KitLoadout.hasAnyItem(KitLoadout.fromPlayer(player))) {
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
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null) {
            player.sendMessage(t(player, "gui.innerkit-create-no-kit",
                    MessageService.tags("name", kitId)).color(UiTheme.DANGER));
            sounds.play(player, "error");
            return;
        }
        ItemStack[] seed;
        String icon;
        switch (kind) {
            case "copy" -> {
                seed = KitLoadout.fromOfficial(kit);
                icon = kit.icon();
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
        InnerKitService.CreateResult result = innerKits.create(kitId, name, seed, icon);
        player.sendMessage(switch (result) {
            case OK -> t(player, "gui.innerkit-created", MessageService.tags(
                    "name", name,
                    "slots", Integer.toString(KitLoadout.itemCount(seed))));
            case NO_SUCH_KIT -> t(player, "gui.innerkit-create-no-kit",
                    MessageService.tags("name", kitId));
            case BLANK_NAME -> t(player, "gui.innerkit-name-invalid");
            case RESERVED_NAME -> t(player, "gui.innerkit-reserved");
            case ALREADY_EXISTS -> t(player, "gui.innerkit-exists",
                    MessageService.tags("name", name));
        });
        sounds.play(player, result == InnerKitService.CreateResult.OK ? "select" : "error");
        open(player, kitId, origin);
    }

    // ---------------------------------------------------------------- rename

    private void promptRename(Player player, String kitId, String id, boolean isDefault, String origin) {
        KitDefinition kit = kitService.get(kitId).orElse(null);
        String current = isDefault
                ? innerKits.displayOf(kitId, null, kit == null ? kitId : kit.prettyDisplayName())
                : innerKits.get(kitId, id).map(InnerKit::displayName).orElse(id);
        player.closeInventory();
        player.sendMessage(t(player,
                isDefault ? "gui.innerkit-default-rename-prompt" : "gui.innerkit-rename-prompt",
                MessageService.tags("name", current)));
        PendingInput.await(player, text -> {
            if (text == null || text.isBlank() || text.equalsIgnoreCase("cancel")) {
                player.sendMessage(t(player, "gui.innerkit-name-cancel").color(UiTheme.MUTED));
            } else {
                boolean ok = isDefault
                        ? innerKits.setDefaultName(kitId, text)
                        : innerKits.setDisplayName(kitId, id, text);
                player.sendMessage(ok
                        ? t(player, "gui.innerkit-renamed", MessageService.tags("name", text.trim()))
                        : t(player, "gui.innerkit-save-failed"));
                sounds.play(player, ok ? "select" : "error");
            }
            open(player, kitId, origin);
        });
    }

    // ---------------------------------------------------------------- icon

    private void setIconFromHand(Player player, GuiSession session, Inventory inventory,
                                 String kitId, String id) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(t(player, "gui.innerkit-icon-hold").color(UiTheme.DANGER));
            sounds.play(player, "error");
            return;
        }
        boolean ok = innerKits.setIcon(kitId, id, hand.getType().name());
        player.sendMessage(ok
                ? t(player, "gui.innerkit-icon-set")
                : t(player, "gui.innerkit-save-failed"));
        sounds.play(player, ok ? "select" : "error");
        if (ok) {
            refresh(player, session, inventory);
        }
    }

    // ---------------------------------------------------------------- delete

    private void askDelete(Player player, String kitId, String id, String origin) {
        InnerKit preset = innerKits.get(kitId, id).orElse(null);
        if (preset == null) {
            sounds.play(player, "error");
            return;
        }
        if (confirmGui == null) {
            delete(player, kitId, id, preset.displayName(), origin);
            return;
        }
        player.closeInventory();
        confirmGui.open(player,
                t(player, "gui.innerkit-delete-title", MessageService.tags("name", preset.displayName())),
                List.of(t(player, "gui.innerkit-delete-lore")),
                yes -> delete(yes, kitId, id, preset.displayName(), origin),
                no -> open(no, kitId, origin));
    }

    private void delete(Player player, String kitId, String id, String name, String origin) {
        boolean removed = innerKits.remove(kitId, id);
        player.sendMessage(removed
                ? t(player, "gui.innerkit-deleted", MessageService.tags("name", name))
                : t(player, "gui.innerkit-save-failed"));
        sounds.play(player, removed ? "delete" : "error");
        open(player, kitId, origin);
    }

    private void locked(Player player) {
        player.sendMessage(t(player, "gui.innerkit-default-locked").color(UiTheme.DANGER));
        sounds.play(player, "error");
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
