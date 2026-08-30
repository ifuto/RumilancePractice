package com.rumilance.practice.gui.menus;

import com.rumilance.practice.database.repository.KitLayoutRepository;
import com.rumilance.practice.gui.AbstractGui;
import com.rumilance.practice.gui.BottomInventoryClickHandler;
import com.rumilance.practice.gui.GuiDecorator;
import com.rumilance.practice.gui.GuiSession;
import com.rumilance.practice.gui.GuiSessionRegistry;
import com.rumilance.practice.gui.GuiType;
import com.rumilance.practice.gui.ItemBuilder;
import com.rumilance.practice.gui.MenuScaffold;
import com.rumilance.practice.gui.PracticeGuiHolder;
import com.rumilance.practice.gui.UiTheme;
import com.rumilance.practice.kit.KitLayoutEditor;
import com.rumilance.practice.kit.KitLayoutCache;
import com.rumilance.practice.kit.KitLayoutContents;
import com.rumilance.practice.kit.KitService;
import com.rumilance.practice.kit.PresetItems;
import com.rumilance.practice.model.KitDefinition;
import com.rumilance.practice.model.KitItemEntry;
import com.rumilance.practice.model.KitLayoutSnapshot;
import com.rumilance.practice.session.PlayerStateManager;
import com.rumilance.practice.sound.SoundService;
import com.rumilance.practice.state.PlayerState;
import com.rumilance.practice.util.AsyncExecutor;
import com.rumilance.practice.util.GuiSlots;
import com.rumilance.practice.util.ItemKeys;
import com.rumilance.practice.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Edit official kit slot layout only (GUI 5). Rearrange with vanilla-style click/drag;
 * content validated on save (no adding/removing items).
 */
public final class EditKitGui extends AbstractGui implements BottomInventoryClickHandler {

    private static final int PRESET_ITEMS_PER_PAGE = 27;

    private final KitService kitService;
    private final KitLayoutRepository layoutRepository;
    private final KitLayoutCache layoutCache;
    private final AsyncExecutor asyncExecutor;
    private final PlayerStateManager stateManager;
    private final PresetItems presetItems;
    private com.rumilance.practice.lobby.LobbyService lobbyService;
    private com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService;
    private com.rumilance.practice.gui.KitEditStash kitEditStash;
    private SmithingTrimGui smithingTrimGui;
    private EkitSelectGui ekitSelectGui;

    public void setEkitSelectGui(EkitSelectGui ekitSelectGui) {
        this.ekitSelectGui = ekitSelectGui;
    }

    public void setKitAnvilRenameService(com.rumilance.practice.gui.KitAnvilRenameService kitAnvilRenameService) {
        this.kitAnvilRenameService = kitAnvilRenameService;
    }

    public void setLobbyService(com.rumilance.practice.lobby.LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    public void setSmithingTrimGui(SmithingTrimGui smithingTrimGui) {
        this.smithingTrimGui = smithingTrimGui;
    }

    public void setKitEditStash(com.rumilance.practice.gui.KitEditStash kitEditStash) {
        this.kitEditStash = kitEditStash;
    }

    public void stashCurrentLayout(Player player, GuiSession session) {
        if (kitEditStash == null || session == null || session.selectedKit() == null) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        kitEditStash.putLayout(player.getUniqueId(), session.selectedKit(),
                session.get("preset", String.class), layout);
    }

    public void restoreLobbyHands(Player player) {
        player.setItemOnCursor(null);
        player.getInventory().clear();
        if (lobbyService != null) {
            lobbyService.applyLobbyInventory(player);
        }
    }

    public void reopenFromStash(Player player) {
        if (kitEditStash == null) {
            return;
        }
        com.rumilance.practice.gui.KitEditStash.Snapshot snapshot = kitEditStash.get(player.getUniqueId());
        if (snapshot == null || snapshot.kitId() == null || snapshot.kitId().isBlank()) {
            return;
        }
        reopenWithLayout(player, snapshot.kitId(), snapshot.layout());
    }

    public EditKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            KitLayoutRepository layoutRepository,
            KitLayoutCache layoutCache,
            AsyncExecutor asyncExecutor,
            PlayerStateManager stateManager
    ) {
        this(registry, sounds, kitService, layoutRepository, layoutCache, asyncExecutor, stateManager, null);
    }

    public EditKitGui(
            GuiSessionRegistry registry,
            SoundService sounds,
            KitService kitService,
            KitLayoutRepository layoutRepository,
            KitLayoutCache layoutCache,
            AsyncExecutor asyncExecutor,
            PlayerStateManager stateManager,
            PresetItems presetItems
    ) {
        super(registry, sounds, GuiType.EDIT_KIT, 5, true);
        this.kitService = kitService;
        this.layoutRepository = layoutRepository;
        this.layoutCache = layoutCache;
        this.asyncExecutor = asyncExecutor;
        this.stateManager = stateManager;
        this.presetItems = presetItems;
    }

    /** True when editing a kit with preset candidates enabled (hotbar palette + Q-drop delete). */
    public boolean isPresetEdit(GuiSession session) {
        if (session == null || presetItems == null || !"edit".equals(session.get("mode", String.class))) {
            return false;
        }
        String kitId = session.selectedKit();
        if (kitId == null) {
            return false;
        }
        return kitService.get(kitId).map(KitDefinition::presetEnabled).orElse(false);
    }

    public void onEditorClosed(Player player, GuiSession session) {
        if (kitAnvilRenameService != null && kitAnvilRenameService.isRenaming(player.getUniqueId())) {
            return;
        }
        if (registry.get(player.getUniqueId())
                .map(s -> s.type() != GuiType.EDIT_KIT)
                .orElse(false)) {
            return;
        }
        restoreLobbyHands(player);
        if (kitEditStash != null) {
            kitEditStash.clear(player.getUniqueId());
        }
    }

    /**
     * Applies an anvil-renamed tool back into the kit layout and reopens the editor.
     */
    public void applyRenamedItem(Player player, String kitId, String preset, int layoutSlot,
                                ItemStack renamed, ItemStack[] layoutSnapshot) {
        ItemStack[] layout = layoutSnapshot;
        if (layout == null && kitEditStash != null) {
            layout = kitEditStash.layoutCopy(player.getUniqueId());
        }
        if (layout == null) {
            layout = new ItemStack[41];
        } else {
            layout = layout.clone();
        }
        if (layoutSlot >= 0 && layoutSlot < layout.length && renamed != null) {
            layout[layoutSlot] = KitLayoutEditor.stripEditorTags(renamed.clone());
        }
        reopenWithLayout(player, kitId, layout);
    }

    /** Q / Ctrl+Q on a kit slot removes the item from the layout (preset kits only). */
    public void handleKitSlotDrop(Player player, GuiSession session, Inventory top, int guiSlot) {
        int layoutIndex = KitLayoutEditor.layoutIndexForGuiSlot(guiSlot);
        if (layoutIndex < 0) {
            return;
        }
        ItemStack[] layout = session.get("layout", ItemStack[].class);
        if (layout == null || layoutIndex >= layout.length) {
            return;
        }
        ItemStack current = layout[layoutIndex];
        if (current == null || current.getType().isAir()) {
            return;
        }
        layout[layoutIndex] = null;
        session.put("layout", layout);
        sounds.play(player, "delete");
        render(player, session, top);
    }

    /** Clears the cursor and plays delete feedback (preset palette delete zones). */
    public void deleteCursorItem(Player player) {
        player.getOpenInventory().setCursor(null);
        sounds.play(player, "delete");
    }

    public void reopenWithLayout(Player player, String kitName, ItemStack[] layout) {
        openKitEditor(player, kitName, null);
        GuiSession session = registry.get(player.getUniqueId()).orElse(null);
        if (session != null && layout != null) {
            session.put("layout", layout);
            render(player, session, player.getOpenInventory().getTopInventory());
        }
    }

    /** Opens the editor directly in edit mode for the given kit (/ekit select flow). */
    public void openKitEditor(Player player, String kitName) {
        openKitEditor(player, kitName, null);
    }

    /** Opens the kit editor; {@code preset} is stored on the session when non-blank. */
    public void openKitEditor(Player player, String kitName, String preset) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.setSelectedKit(kitName);
        session.put("mode", "edit");
        if (preset != null && !preset.isBlank()) {
            session.put("preset", preset);
        }
        initPresetSession(session);
        PlayerState state = stateManager.getState(player.getUniqueId());
        try {
            if (state != PlayerState.EDITING_KIT) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // keep going — party / nested GUI may already be OPENING_GUI
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    public void applyTrimmedItem(Player player, String kitId, String preset, int layoutSlot, ItemStack trimmed) {
        ItemStack[] layout = kitEditStash == null ? null : kitEditStash.layoutCopy(player.getUniqueId());
        if (layout == null) {
            GuiSession session = registry.get(player.getUniqueId()).orElse(null);
            layout = session == null ? null : session.get("layout", ItemStack[].class);
        }
        if (layout == null) {
            layout = new ItemStack[41];
        } else {
            layout = layout.clone();
        }
        if (trimmed != null && layoutSlot >= 0 && layoutSlot < layout.length) {
            layout[layoutSlot] = KitLayoutEditor.stripEditorTags(trimmed.clone());
        }
        reopenWithLayout(player, kitId, layout);
        persistLayout(player, kitId, layout, false);
    }

    public void openKitPicker(Player player) {
        GuiSession session = registry.open(player.getUniqueId(), type(), rows);
        session.put("mode", "picker");
        try {
            if (stateManager.getState(player.getUniqueId()) == PlayerState.LOBBY
                    || stateManager.getState(player.getUniqueId()) == PlayerState.OPENING_GUI) {
                stateManager.transition(player.getUniqueId(), PlayerState.EDITING_KIT);
            }
        } catch (Exception ignored) {
            // keep going
        }
        PracticeGuiOpen.open(this, player, session);
        sounds.play(player, "gui-open");
    }

    @Override
    protected Component title(Player player, GuiSession session) {
        String kit = session.selectedKit();
        return Component.text(kit == null ? "Edit Kit"
                : "Edit: " + com.rumilance.practice.util.KitNames.pretty(kit), UiTheme.PRIMARY)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void render(Player player, GuiSession session, Inventory inventory) {
        MenuScaffold.editorChrome(inventory);
        if ("picker".equals(session.get("mode", String.class)) || session.selectedKit() == null) {
            int i = 0;
            for (KitDefinition kit : kitService.enabled()) {
                if (i >= 21) {
                    break;
                }
                Material mat = Material.matchMaterial(kit.icon());
                ItemStack icon = new ItemStack(mat == null ? Material.DIAMOND_SWORD : mat);
                ItemMeta meta = icon.getItemMeta();
                meta.displayName(Component.text(kit.prettyDisplayName(), UiTheme.PRIMARY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.getPersistentDataContainer().set(ItemKeys.guiAction(), PersistentDataType.STRING,
                        "editkit:" + kit.name());
                icon.setItemMeta(meta);
                inventory.setItem(GuiSlots.slot(1 + i / 7, 1 + i % 7), icon);
                i++;
            }
            inventory.setItem(GuiSlots.slot(4, 4),
                    ItemBuilder.action(UiTheme.CLOSE, t(player, "menu.close"), "close"));
            return;
        }
        KitDefinition kit = kitService.get(session.selectedKit()).orElse(null);
        if (kit == null) {
            return;
        }
        // Keep in-session rearranges across re-render; reloading from disk wiped swaps before Save.
        ItemStack[] layout = KitLayoutContents.retainOrLoad(
                session.get("layout", ItemStack[].class),
                loadLayout(player.getUniqueId(), kit));
        // armor row visually: helmet/chest/legs/boots + offhand
        inventory.setItem(GuiSlots.slot(0, 1), tagged(player, layout.length > 36 ? layout[36] : null, "slot:36"));
        inventory.setItem(GuiSlots.slot(0, 2), tagged(player, layout.length > 37 ? layout[37] : null, "slot:37"));
        inventory.setItem(GuiSlots.slot(0, 3), tagged(player, layout.length > 38 ? layout[38] : null, "slot:38"));
        inventory.setItem(GuiSlots.slot(0, 4), tagged(player, layout.length > 39 ? layout[39] : null, "slot:39"));
        inventory.setItem(GuiSlots.slot(0, 6), tagged(player, layout.length > 40 ? layout[40] : null, "slot:40"));
        // Main inventory slots 9-35 -> menu rows 1-3, ALL 9 columns (27 slots exactly).
        // (Previously columns 0 and 8 were skipped, hiding the edge slots of each row.)
        for (int inv = 9; inv < 36; inv++) {
            int local = inv - 9;
            int row = 1 + local / 9;
            int col = local % 9;
            inventory.setItem(GuiSlots.slot(row, col), tagged(player, layout[inv], "slot:" + inv));
        }
        // hotbar row 4
        for (int hot = 0; hot < 9; hot++) {
            inventory.setItem(GuiSlots.slot(4, hot), tagged(player, layout[hot], "slot:" + hot));
        }
        inventory.setItem(GuiSlots.slot(0, 0),
                ItemBuilder.action(UiTheme.BACK, t(player, "menu.back"), "back"));
        inventory.setItem(GuiSlots.slot(0, 8),
                ItemBuilder.action(UiTheme.CONFIRM, t(player, "gui.save"), "save"));
        session.put("layout", layout);
        stashCurrentLayout(player, session);
        if (kit.presetEnabled() && presetItems != null) {
            schedulePresetPaletteRender(player);
        }
    }

    private void initPresetSession(GuiSession session) {
        String kitId = session.selectedKit();
        if (kitId == null || presetItems == null) {
            return;
        }
        kitService.get(kitId).ifPresent(kit -> {
            if (kit.presetEnabled()) {
                session.put("preset_category", PresetItems.CATEGORIES.getFirst());
                session.put("preset_page", 0);
            }
        });
    }

    private static int presetPage(GuiSession session) {
        Integer page = session.get("preset_page", Integer.class);
        return page == null ? 0 : Math.max(0, page);
    }

    private void setPresetPage(GuiSession session, int page) {
        session.put("preset_page", Math.max(0, page));
    }

    private void schedulePresetPaletteRender(Player player) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(EditKitGui.class);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            GuiSession live = registry.get(player.getUniqueId()).orElse(null);
            if (live == null || !isPresetEdit(live)) {
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof PracticeGuiHolder holder)
                    || holder.type() != GuiType.EDIT_KIT) {
                return;
            }
            renderPlayerPresetPalette(player, live);
        });
    }

    private void renderPlayerPresetPalette(Player player, GuiSession session) {
        String category = session.get("preset_category", String.class);
        if (category == null || !PresetItems.CATEGORIES.contains(category)) {
            category = PresetItems.CATEGORIES.getFirst();
            session.put("preset_category", category);
        }
        int page = presetPage(session);
        setPresetPage(session, page);

        PlayerInventory inv = player.getInventory();
        inv.clear();
        String kitId = session.selectedKit();
        int start = page * PRESET_ITEMS_PER_PAGE;
        java.util.Map<Integer, String> slotMap = presetItems.slots(kitId, category);
        for (int i = 0; i < PRESET_ITEMS_PER_PAGE; i++) {
            int absSlot = start + i;
            String entry = slotMap.get(absSlot);
            if (entry != null) {
                inv.setItem(9 + i, presetItems.displayItem(category, entry).clone());
            }
        }
        for (int i = 0; i < PresetItems.CATEGORIES.size(); i++) {
            String cat = PresetItems.CATEGORIES.get(i);
            boolean selected = cat.equals(category);
            inv.setItem(i, GuiDecorator.button(presetTabMaterial(cat),
                    Component.text(cat, selected ? UiTheme.SUCCESS : UiTheme.VALUE),
                    "preset-cat:" + cat, selected));
        }
        inv.setItem(4, GuiDecorator.decorative(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(5, deleteGlass(player));
        inv.setItem(6, deleteGlass(player));
        if (page > 0) {
            inv.setItem(7, pageButton(line(player, "gui.page-prev"), "preset-prev"));
        }
        int maxSlot = slotMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        if (start + PRESET_ITEMS_PER_PAGE <= maxSlot) {
            inv.setItem(8, pageButton(line(player, "gui.page-next"), "preset-next"));
        }
    }

    private static Material presetTabMaterial(String category) {
        return switch (com.rumilance.practice.kit.CategoryKeys.canonicalPreset(category)) {
            case "Armor" -> Material.NETHERITE_CHESTPLATE;
            case "Gear" -> Material.NETHERITE_SWORD;
            case "Potions" -> Material.SPLASH_POTION;
            case "Consumables" -> Material.GOLDEN_APPLE;
            default -> Material.CHEST;
        };
    }

    private ItemStack deleteGlass(Player player) {
        ItemStack stack = GuiDecorator.button(Material.BLUE_STAINED_GLASS_PANE,
                t(player, "gui.delete"), "preset-delete");
        stack.editMeta(meta -> meta.lore(List.of(
                Component.text(line(player, "gui.delete-hint"), UiTheme.MUTED))));
        return stack;
    }

    private ItemStack pageButton(String name, String action) {
        return GuiDecorator.button(Material.LIME_STAINED_GLASS_PANE,
                Component.text(name, UiTheme.SUCCESS), action);
    }

    private ItemStack[] loadLayout(UUID uuid, KitDefinition kit) {
        try {
            var snap = layoutRepository.find(uuid, kit.name());
            if (snap.isPresent()) {
                return ItemSerializer.fromBase64(snap.get().itemDataBase64());
            }
        } catch (Exception ignored) {
            // fall through
        }
        ItemStack[] layout = new ItemStack[41];
        for (KitItemEntry entry : kit.items()) {
            ItemStack stack = kitEntryStack(entry);
            if (stack == null) {
                continue;
            }
            if (entry.slot() >= 0 && entry.slot() < 36) {
                layout[entry.slot()] = stack;
            } else if (entry.slot() == 40) {
                layout[40] = stack;
            }
        }
        layout[36] = material(kit.armor().get("helmet"));
        layout[37] = material(kit.armor().get("chestplate"));
        layout[38] = material(kit.armor().get("leggings"));
        layout[39] = material(kit.armor().get("boots"));
        return layout;
    }

    /** Full-NBT kit entry when available; plain material+amount otherwise. */
    private static ItemStack kitEntryStack(KitItemEntry entry) {
        if (entry.hasSerializedItem()) {
            ItemStack decoded = ItemSerializer.singleFromBase64(entry.itemDataBase64());
            if (decoded != null) {
                return decoded;
            }
        }
        Material mat = Material.matchMaterial(entry.material());
        return mat == null || mat.isAir() ? null : new ItemStack(mat, Math.max(1, entry.amount()));
    }

    private static ItemStack material(String name) {
        if (name == null) {
            return null;
        }
        // "data:<base64>" armor values carry full NBT.
        if (name.startsWith("data:")) {
            ItemStack decoded = ItemSerializer.singleFromBase64(name.substring("data:".length()));
            if (decoded != null) {
                return decoded;
            }
            return null;
        }
        Material mat = Material.matchMaterial(name);
        return mat == null ? null : new ItemStack(mat);
    }

    public boolean isEditorMode(GuiSession session) {
        return session != null
                && session.selectedKit() != null
                && !"picker".equals(session.get("mode", String.class));
    }

    private ItemStack tagged(Player player, ItemStack stack, String action) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        java.util.ArrayList<Component> extra = new java.util.ArrayList<>();
        if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta) {
            extra.add(t(player, "gui.kit-trim-hint").color(UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (com.rumilance.practice.gui.KitAnvilRenameService.isRenameableTool(stack.getType())) {
            extra.add(t(player, "gui.kit-rename-hint").color(UiTheme.MUTED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return KitLayoutEditor.tagLayoutItem(stack, action, extra);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot, String action) {
        handleClick(player, session, inventory, slot, action, org.bukkit.event.inventory.ClickType.LEFT);
    }

    @Override
    public void handleClick(Player player, GuiSession session, Inventory inventory, int slot,
                            String action, org.bukkit.event.inventory.ClickType clickType) {
        if (clickType == org.bukkit.event.inventory.ClickType.RIGHT && action.startsWith("slot:")) {
            int layoutIndex = Integer.parseInt(action.substring(5));
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                ItemStack item = layout[layoutIndex];
                String kitId = session.selectedKit();
                String preset = session.get("preset", String.class);
                stashCurrentLayout(player, session);
                if (item != null && kitAnvilRenameService != null
                        && com.rumilance.practice.gui.KitAnvilRenameService.isRenameableTool(item.getType())
                        && kitAnvilRenameService.tryOpenRename(player, item, layoutIndex, kitId, preset, layout)) {
                    return;
                }
                if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta
                        && smithingTrimGui != null && kitId != null) {
                    smithingTrimGui.openForLayoutSlot(player, item, layoutIndex, kitId, preset);
                    return;
                }
            }
        }
        if ("close".equals(action) || "back".equals(action)) {
            restoreLobbyHands(player);
            if (kitEditStash != null) {
                kitEditStash.clear(player.getUniqueId());
            }
            if ("back".equals(action) && ekitSelectGui != null) {
                session.setNavigatingAway(true);
                stateManager.resetToLobby(player.getUniqueId());
                ekitSelectGui.open(player);
                return;
            }
            stateManager.resetToLobby(player.getUniqueId());
            player.closeInventory();
            return;
        }
        if (action.startsWith("editkit:")) {
            session.setSelectedKit(action.substring(8));
            session.put("mode", "edit");
            initPresetSession(session);
            render(player, session, inventory);
            sounds.play(player, "kit-select");
            return;
        }
        if (action.startsWith("slot:")) {
            int layoutIndex = Integer.parseInt(action.substring(5));
            ItemStack[] layout = session.get("layout", ItemStack[].class);
            if (layout == null) {
                return;
            }
            KitLayoutEditor.handleSlotPickup(player, layout, layoutIndex);
            session.put("layout", layout);
            stashCurrentLayout(player, session);
            render(player, session, inventory);
            return;
        }
        if ("save".equals(action)) {
            save(player, session);
            return;
        }
    }

    @Override
    public void handleBottomClick(Player player, GuiSession session, InventoryClickEvent event) {
        if (!isPresetEdit(session)) {
            return;
        }
        int slot = event.getSlot();
        ItemStack cursor = event.getCursor();
        if (slot >= 9 && slot <= 35) {
            if (cursor != null && !cursor.getType().isAir()) {
                return;
            }
            String category = session.get("preset_category", String.class);
            int page = presetPage(session);
            java.util.Map<Integer, String> slotMap = presetItems.slots(session.selectedKit(), category);
            int absSlot = page * PRESET_ITEMS_PER_PAGE + (slot - 9);
            String entry = slotMap.get(absSlot);
            if (entry == null) {
                return;
            }
            ItemStack item = presetItems.displayItem(category, entry);
            if (item == null) {
                return;
            }
            event.getView().setCursor(item.clone());
            sounds.play(player, "gui-click");
            return;
        }
        if (slot <= 8) {
            if (slot >= 0 && slot <= 3) {
                List<String> categories = PresetItems.CATEGORIES;
                if (slot < categories.size()) {
                    session.put("preset_category", categories.get(slot));
                    session.put("preset_page", 0);
                    renderPlayerPresetPalette(player, session);
                    sounds.play(player, "gui-click");
                }
                return;
            }
            if (slot == 7 && presetPage(session) > 0) {
                setPresetPage(session, presetPage(session) - 1);
                renderPlayerPresetPalette(player, session);
                sounds.play(player, "gui-click");
                return;
            }
            if (slot == 8) {
                String category = session.get("preset_category", String.class);
                int page = presetPage(session);
                java.util.Map<Integer, String> slotMap = presetItems.slots(session.selectedKit(), category);
                int maxSlot = slotMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                if ((page + 1) * PRESET_ITEMS_PER_PAGE <= maxSlot) {
                    setPresetPage(session, page + 1);
                    renderPlayerPresetPalette(player, session);
                    sounds.play(player, "gui-click");
                }
                return;
            }
            if (slot == 5 || slot == 6) {
                if (cursor != null && !cursor.getType().isAir()) {
                    event.getView().setCursor(null);
                    sounds.play(player, "delete");
                }
            }
        }
    }

    private void save(Player player, GuiSession session) {
        persistLayout(player, session, true);
    }

    /**
     * Writes the current kit layout to DB. Always succeeds when kit/layout exist — no
     * rearrange-only gate. Used by Save and trim apply.
     */
    public void persistLayout(Player player, GuiSession session, boolean notify) {
        String kitId = session == null ? null : session.selectedKit();
        if (kitId == null) {
            return;
        }
        persistLayout(player, kitId, resolveLayoutForSave(player, session), notify);
    }

    public void persistLayout(Player player, String kitId, ItemStack[] layout, boolean notify) {
        KitDefinition kit = kitService.get(kitId).orElse(null);
        if (kit == null || layout == null) {
            return;
        }
        String base64 = ItemSerializer.toBase64(layout);
        KitLayoutSnapshot snap = KitLayoutSnapshot.create(player.getUniqueId(), kitId, base64);
        layoutCache.put(player.getUniqueId(), kitId, layout);
        asyncExecutor.execute(() -> {
            try {
                layoutRepository.upsert(snap);
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> {
                            sounds.play(player, "select");
                            player.sendMessage(t(player, "gui.kit-saved"));
                        });
            } catch (Exception e) {
                if (!notify) {
                    return;
                }
                player.getServer().getScheduler().runTask(
                        JavaPlugin.getProvidingPlugin(EditKitGui.class),
                        () -> player.sendMessage(t(player, "gui.save-failed")));
            }
        });
    }

    private ItemStack[] resolveLayoutForSave(Player player, GuiSession session) {
        ItemStack[] layout = null;
        if (kitEditStash != null) {
            layout = kitEditStash.layoutCopy(player.getUniqueId());
        }
        if (layout == null && session != null) {
            layout = session.get("layout", ItemStack[].class);
        }
        if (layout == null) {
            return null;
        }
        layout = layout.clone();
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof com.rumilance.practice.gui.PracticeGuiHolder holder
                && holder.type() == GuiType.EDIT_KIT) {
            KitLayoutEditor.syncLayoutFromTopInventory(player.getOpenInventory().getTopInventory(), layout);
        }
        if (session != null) {
            session.put("layout", layout);
        }
        return layout;
    }

}
